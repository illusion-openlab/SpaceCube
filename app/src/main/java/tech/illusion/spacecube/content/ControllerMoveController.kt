package tech.illusion.spacecube.content

import android.util.Log
import com.pico.spatial.tracking.controller.ControllerActionData
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.controller.ThumbstickValue
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "SpaceCubeController"

/** Digital direction derived from whichever controller's thumbstick is deflected furthest. */
private enum class StickDirection { LEFT, RIGHT, DOWN, ROTATE }

/** Deflection past which a neutral stick counts as newly pressed in that direction. */
private const val STICK_PRESS_THRESHOLD = 0.6f

/**
 * Deflection below which an already-locked direction counts as released. Lower than
 * [STICK_PRESS_THRESHOLD] (same hysteresis shape as this project's pinch distance thresholds) so
 * a stick resting exactly on the boundary doesn't flicker the lock on/off every callback.
 */
private const val STICK_RELEASE_THRESHOLD = 0.35f

/** Hold-repeat cadence for LEFT/RIGHT/DOWN, matching V1 hand tracking's own soft-drop repeat
 * shape (`SOFT_DROP_INTERVAL_FACTOR`/`SOFT_DROP_MIN_INTERVAL_MS` in `GamePage.kt`) so controller
 * movement doesn't feel faster or slower than pinch-and-hold. */
private const val STICK_MOVE_REPEAT_INTERVAL_FACTOR = 0.7f
private const val STICK_MOVE_REPEAT_MIN_INTERVAL_MS = 60L

/**
 * Sign of the thumbstick's y axis for this project's up/down mapping. [ThumbstickValue]'s own
 * KDoc says "exact orientation depends on device mapping" and no other project in this workspace
 * consumes a thumbstick yet, so this is a first-pass guess (positive y = pushed up = rotate),
 * kept as a single isolated constant - same pattern as `SCENE_YAW_SIGN` elsewhere in this
 * package - so a real-device test that finds up/down swapped is a one-line fix here, not a
 * re-derivation of the whole controller. **Unverified on real hardware.**
 */
private const val STICK_VERTICAL_SIGN = 1f

/**
 * Thumbstick-driven piece control: a controller-based alternative to the pinch (V1) and
 * glass-plane drag (V2) schemes described in AGENTS.md's "Two control schemes: V1 vs V2" and
 * "Gesture input model" sections.
 *
 * Deliberately **not** gated by [ControlScheme] the way V1/V2 gate each other: a player holding
 * physical controllers isn't simultaneously bare-hand pinching or aiming a ray at the V2 glass
 * plane, so there is no double-input risk to guard against. This runs as a genuinely independent
 * third input path whenever [isPlaying] is true - the same "second, additive way in" shape as
 * this workspace's `SpaceStack` project's `TriggerDropController` (also built on
 * `ControllerTrackingProvider`), which this class's start/stop/listener plumbing mirrors.
 *
 * Left/right/down thumbstick deflection moves/soft-drops with hold-repeat, like V1's
 * pinch-and-hold. Up is edge-triggered exactly once per press (no repeat) and rotates clockwise -
 * this project's `AGENTS.md` records that an earlier design considered the controller *trigger*
 * for rotation, but V2's own gesture path already had to work around the trigger/ray-cast button
 * fighting the AttachmentPanel UI for taps, so rotation was moved to the stick instead and the
 * trigger stays untouched, free for whatever ray-cast/click behavior the platform gives it.
 *
 * Reads **both** controllers' sticks and lets whichever is deflected further drive the current
 * lock - mirrors this project's existing "either hand" rule for double-pinch rotate, so a player
 * doesn't have to remember which hand the direction stick lives on.
 */
class ControllerMoveController {

    private val provider = ControllerTrackingProvider()

    /** Owns every main-thread callback hand-off, so [stop] cancels any still in flight instead of
     * letting one land after teardown - same reasoning as `TriggerDropController.scope`. */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var onMoveLeft: (() -> Unit)? = null
    private var onMoveRight: (() -> Unit)? = null
    private var onMoveDown: (() -> Unit)? = null
    private var onRotate: (() -> Unit)? = null
    private var isPlaying: (() -> Boolean)? = null
    private var currentFallIntervalMs: (() -> Long)? = null
    private var listener: ControllerTrackingProvider.ControllerActionListener? = null

    /** Only ever read/written on the tracking callback's own data-source thread (see
     * `ControllerTrackingProvider`'s class KDoc), a single thread, so plain fields are enough. */
    private var lockedDirection: StickDirection? = null
    private var repeatJob: Job? = null

    fun start(
        onMoveLeft: () -> Unit,
        onMoveRight: () -> Unit,
        onMoveDown: () -> Unit,
        onRotate: () -> Unit,
        isPlaying: () -> Boolean,
        currentFallIntervalMs: () -> Long,
    ) {
        this.onMoveLeft = onMoveLeft
        this.onMoveRight = onMoveRight
        this.onMoveDown = onMoveDown
        this.onRotate = onRotate
        this.isPlaying = isPlaying
        this.currentFallIntervalMs = currentFallIntervalMs
        val startResult = provider.start()
        // supportState is logged alongside the start result for the same reason
        // TriggerDropController logs it: DEVICE_NOT_SUPPORTED (no controller paired),
        // WITHOUT_PERMISSION, or NOT_IN_FULL_SPACE explain a start result that "succeeded" but
        // never actually delivers a callback.
        Log.i(TAG, "ControllerTrackingProvider.start() -> $startResult, supportState=${provider.supportState}")
        val actionListener = ControllerTrackingProvider.ControllerActionListener { action ->
            onControllerAction(action)
        }
        provider.addControllerActionListener(actionListener)
        listener = actionListener
    }

    fun stop() {
        listener?.let { provider.removeControllerActionListener(it) }
        listener = null
        provider.stop()
        cancelRepeat()
        scope.cancel()
        onMoveLeft = null
        onMoveRight = null
        onMoveDown = null
        onRotate = null
        isPlaying = null
        currentFallIntervalMs = null
    }

    private fun onControllerAction(action: ControllerActionData) {
        if (isPlaying?.invoke() != true) {
            // Not actually playing (start screen / paused / game-over) - drop any held lock so a
            // stick still deflected when a round (re)starts doesn't fire a stale move. Same reset
            // HandGestureController performs on its own move-lock state across the same
            // !isPlaying -> isPlaying edge.
            cancelRepeat()
            return
        }
        val direction = dominantDirection(action.left.thumbstickValue, action.right.thumbstickValue)
        if (direction == lockedDirection) return
        cancelRepeat()
        lockedDirection = direction ?: return

        if (direction == StickDirection.ROTATE) {
            // Edge-triggered only, no repeat job - one rotation per press, like the double-pinch
            // rotate gesture, not a hold-repeat movement.
            val rotate = onRotate ?: return
            scope.launch { rotate() }
            return
        }

        val fire = when (direction) {
            StickDirection.LEFT -> onMoveLeft
            StickDirection.RIGHT -> onMoveRight
            StickDirection.DOWN -> onMoveDown
            StickDirection.ROTATE -> null
        } ?: return
        val fallIntervalMs = currentFallIntervalMs ?: return
        repeatJob = scope.launch {
            while (isActive) {
                fire()
                val interval = (fallIntervalMs() * STICK_MOVE_REPEAT_INTERVAL_FACTOR).toLong()
                    .coerceAtLeast(STICK_MOVE_REPEAT_MIN_INTERVAL_MS)
                delay(interval)
            }
        }
    }

    private fun cancelRepeat() {
        repeatJob?.cancel()
        repeatJob = null
        lockedDirection = null
    }

    /**
     * Picks whichever controller's stick is deflected further this frame, then resolves it to a
     * digital direction using axis priority (the larger-magnitude axis wins, so a diagonal push
     * reads as one direction, not two at once). Applies [STICK_RELEASE_THRESHOLD] instead of
     * [STICK_PRESS_THRESHOLD] while a direction is already locked, so a stick sitting near the
     * boundary doesn't chatter the lock on/off every callback.
     */
    private fun dominantDirection(left: ThumbstickValue, right: ThumbstickValue): StickDirection? {
        val stick = if (magnitudeSquared(left) >= magnitudeSquared(right)) left else right
        val absX = abs(stick.x)
        val absY = abs(stick.y)
        val threshold = if (lockedDirection != null) STICK_RELEASE_THRESHOLD else STICK_PRESS_THRESHOLD
        if (absX < threshold && absY < threshold) return null
        return if (absX >= absY) {
            if (stick.x < 0f) StickDirection.LEFT else StickDirection.RIGHT
        } else {
            if (stick.y * STICK_VERTICAL_SIGN < 0f) StickDirection.DOWN else StickDirection.ROTATE
        }
    }

    private fun magnitudeSquared(stick: ThumbstickValue): Float = stick.x * stick.x + stick.y * stick.y
}
