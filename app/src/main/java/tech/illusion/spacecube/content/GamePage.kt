package tech.illusion.spacecube.content

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hand.HandPose
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import tech.illusion.spacecube.game.Board
import tech.illusion.spacecube.game.ControlScheme
import tech.illusion.spacecube.game.Difficulty
import tech.illusion.spacecube.game.DIFFICULTY_BUNDLE_KEY
import tech.illusion.spacecube.game.GameEngine
import tech.illusion.spacecube.game.GameEvent
import tech.illusion.spacecube.game.GameSettings
import tech.illusion.spacecube.game.GameState
import tech.illusion.spacecube.game.NO_PIECE_ID
import tech.illusion.spacecube.game.PieceMaterial
import tech.illusion.spacecube.game.SharedPreferencesBasePlateMaterialStore
import tech.illusion.spacecube.game.SharedPreferencesHighScoreStore
import tech.illusion.spacecube.game.SharedPreferencesPieceMaterialStore
import tech.illusion.spacecube.game.parseDifficulty

// 2026-08-06: replaces an earlier attempt built on detectSpatialDragGesture /
// detectSpatialRotateGesture (Compose gesture detectors that hit-test against ECS
// entities). That attempt compiled and rendered its debug hit-test volume in the
// right place, but produced zero response to any pinch on-device across multiple
// fix attempts (missing components, wrong targeting) - the root cause was never
// isolated. This reads hand-tracking joint data directly instead and computes
// pinch/direction/rotation ourselves, bypassing that layer entirely. See AGENTS.md.
private const val HAND_GESTURE_LOG_TAG = "SpaceCubeHandGesture"

// A pinch is "thumb-tip close to index-tip". Hysteresis (start < release) avoids
// flicker right at the boundary. PINCH_CONFIRM_TICKS additionally requires the
// new reading to repeat for a couple of polls before it's accepted - filters a
// single noisy/jittery tracking frame from registering as a real pinch/release.
//
// 2026-08-06: tightened twice now, both times from direct user feedback -
// 0.025/0.04 -> 0.015/0.025 (screenshot showing a visible gap while already
// registering as a pinch) -> 0.010/0.018 (still felt too loose). PinchTracker
// logs the measured tip-to-tip distance every time the state actually flips
// (tag HAND_GESTURE_LOG_TAG) - check logcat for real numbers if tightening
// further, rather than guessing again.
private const val PINCH_START_DISTANCE_M = 0.010f
private const val PINCH_RELEASE_DISTANCE_M = 0.018f
private const val PINCH_CONFIRM_TICKS = 2

// How far the hand must drop below where the pinch started before soft drop
// engages. Only gates the DOWN axis now: horizontal movement is continuous 1:1
// hand tracking (user request 2026-08-07 "先去掉下落方块进行横向移动时只能移动一格限制，
// 尽量保持移动跟手"), so there is no longer a latched left/right/down direction.
private const val SOFT_DROP_HAND_DROP_M = 0.05f

// Visual feedback ball at each hand's pinch point (2026-08-06, user-requested):
// gray while tracked-but-not-pinching, mint while pinching - lets you see exactly
// when/where the system registers a pinch, which is the fastest way to judge
// whether PINCH_START/RELEASE_DISTANCE_M above need further tuning.
private const val PINCH_INDICATOR_RADIUS_M = 0.008f
private val PINCH_INDICATOR_IDLE_COLOR = Color4(0.75f, 0.75f, 0.75f, 1f)
private val PINCH_INDICATOR_ACTIVE_COLOR = Color4(0.34f, 0.88f, 0.63f, 1f)

// Soft drop repeat cadence while the hand is held low: a fraction of the current
// fall interval, so holding always beats gravity at every difficulty/level.
private const val SOFT_DROP_INTERVAL_FACTOR = 0.7f
private const val SOFT_DROP_MIN_INTERVAL_MS = 60L

// Rotate is a quick double-pinch (like a double-click), not the earlier two-hand
// twist (removed 2026-08-06 per user request). A second pinch-start on the SAME
// hand within this window counts as a "double pinch".
//
// 2026-08-07: BOTH hands now rotate clockwise, per user request ("双击旋转的方向
// 都按照顺时针方向进行"). An earlier version mapped left hand -> counter-clockwise
// and right hand -> clockwise, but that split was only ever an assumed default
// the user never asked for. GameEngine.rotateCounterClockwise() is consequently
// no longer called from any gameplay path - it's kept (and unit-tested) as the
// engine-level inverse of rotateClockwise(), not dead code to delete.
//
// 2026-08-06: raised from an initial 400ms - user reported the gesture felt
// unresponsive. The window is measured start-to-start across pinch1-start ->
// release -> pinch2-start, and each of those three transitions must survive
// PINCH_CONFIRM_TICKS (2 x 20ms = 40ms) of debounce before it's even
// recognized - 120ms of that 400ms budget was pure software latency before
// accounting for any actual finger movement, which is tight for a real
// double-pinch. If it's still unresponsive, the next lever is
// PINCH_CONFIRM_TICKS (lower = less debounce latency per transition, but
// more jitter risk - the pinch distance thresholds are tight enough now that
// this is a safer trade than it used to be).
private const val DOUBLE_PINCH_WINDOW_MS = 600L

private const val HAND_POLL_INTERVAL_MS = 20L

// Enlarged 2026-08-06 from 8x14 per user request ("宽高大些，以便能容纳更多方块") -
// closer to standard Tetris proportions (10 wide) while staying short of the full
// 10x20 to keep the board from towering too far above/below ANCHOR_HEIGHT_M.
private const val BOARD_WIDTH = 10
private const val BOARD_HEIGHT = 18

// Height of the board's anchor above the floor (Stage's origin is at the user's
// feet). Lowered 2026-08-06 from 1.3f per user request ("离地高度0.8m").
private const val ANCHOR_HEIGHT_M = 0.8f

// The base plate is placed this far below the user's measured head height at
// startup (user request 2026-08-07: "读取当前头戴设备的高度，游戏井底座的高度为头显
// 设备向下0.5米处的高度"), replacing a fixed ANCHOR_HEIGHT_M for the initial
// placement. ANCHOR_HEIGHT_M is still the fallback if the HMD never reports a
// usable pose, so the board is never left unplaced/invisible.
private const val BASE_PLATE_BELOW_HEAD_M = 0.5f

// The score / NEXT / pause panels stand along the base plate's edge nearest the
// user (user request 2026-08-07: "分数、暂停、下个这几个UI可以贴在底座面上靠近用户这一
// 侧"). Stage's +Z points toward the user, so "near" is +Z. The plate spans
// boardWidth * CELL_STEP_M (0.56m at 10 wide) in both X and Z, so these offsets
// have to stay inside ±0.28m.
//
// They were initially laid perfectly flat, which the user found hard to read
// ("过于贴合底座对用户视角不太友善"), so they now tilt back like a lectern: bottom
// edge on the plate, face angled 60° up from it ("底边贴合底座然后向上倾斜60度左右").
// A panel's default facing is +Z with pitch 0 = fully upright, pitch -90 = flat,
// so 60° of elevation off the plate is pitch -30.
private const val PLATE_PANEL_TILT_DEG = -30f
// Back to 0.17 now that the board has returned to z=0 - the 0.24 push-out only
// existed to stop these panels sitting inside the bottom row of blocks while the
// board was (mistakenly) moved forward to meet them.
private const val PLATE_PANEL_NEAR_Z_M = 0.17f

// The big centred card overlays (start screen, paused, game over) sit at the SAME
// depth as the status panels above, per user request "游戏主面板可以往前靠近一点与游戏
// 状态面板保持同一深度" - they were at 0.08, noticeably further back. Deliberately
// derived from PLATE_PANEL_NEAR_Z_M rather than repeating the number, so the two
// can't drift apart again. No collision risk despite the shared depth: these sit at
// the anchor's own height (y=0), well clear of the bottom-row blocks and of the
// status panels down on the base plate.
private const val MAIN_PANEL_Z_M = PLATE_PANEL_NEAR_Z_M
private const val PLATE_PANEL_SIDE_X_M = 0.18f

// How far to raise a tilted panel's centre so its BOTTOM edge rests on the plate
// rather than sinking through it: half the panel's height x sin(60°), plus a
// little clearance. Estimated - the dp->metre scale for AttachmentPanel content
// isn't documented anywhere we've verified, so this is the value to nudge if the
// panels end up floating above or clipping into the plate.
private const val PLATE_PANEL_LIFT_M = 0.05f

// Scene transform limits/conventions for the non-gameplay 6DoF + rotate + scale
// gestures (user request 2026-08-07: "非游戏状态中可以进行6Dof移动操作并且支持旋转和
// 缩放"). Scale is clamped so the board can't be shrunk to nothing or blown up
// past where it can be found again.
private const val MIN_SCENE_SCALE = 0.4f
private const val MAX_SCENE_SCALE = 2.5f

// Two hands closer than this are treated as one blob - avoids a near-zero span
// making the scale ratio explode.
private const val MIN_TWO_HAND_SPAN_M = 0.08f

// Maps two-hand twist to yaw. The right-hand rule about +Y carries +Z toward +X,
// while atan2(dz, dx) grows going from +X toward +Z - opposite senses, hence the
// negation. UNVERIFIED on-device: if the board turns the wrong way, flip this.
private const val SCENE_YAW_SIGN = -1f

// HMD pose isn't available the instant start() returns (it can report PENDING,
// and latestData starts zeroed), so poll briefly for a usable reading.
private const val HEAD_CALIBRATION_POLL_INTERVAL_MS = 50L
private const val HEAD_CALIBRATION_MAX_POLLS = 60 // ~3s

/**
 * The anchor's live position, shared by head-height calibration and the scene
 * free-drag.
 *
 * Both features move the same entity, and the drag works from a remembered
 * "position when the pinch started" - so if each kept its own copy, calibrating
 * the height and then dragging would snap the board back to whichever copy was
 * stale. One holder, one source of truth.
 */
private class AnchorPlacement(initial: Vector3) {
    var position: Vector3 = initial
        private set

    /** Rotation about the world Y axis - turning the board to face the user. */
    var yawDegrees: Float = 0f
        private set

    /** Uniform scale; children (board + panels) scale with the anchor. */
    var scale: Float = 1f
        private set

    private fun apply(anchor: Entity) {
        anchor.components[TransformComponent::class.java]?.let { transform ->
            transform.setPosition(position)
            transform.setEulerAngles(EulerAngles(0f, yawDegrees, 0f))
            transform.setScaleVector(Vector3(scale, scale, scale))
        }
    }

    fun moveTo(anchor: Entity, next: Vector3) {
        position = next
        apply(anchor)
    }

    fun transformTo(anchor: Entity, nextPosition: Vector3, nextYawDegrees: Float, nextScale: Float) {
        position = nextPosition
        yawDegrees = nextYawDegrees
        scale = nextScale.coerceIn(MIN_SCENE_SCALE, MAX_SCENE_SCALE)
        apply(anchor)
    }
}

/**
 * Rotates a world-space delta into the board's own space by undoing the scene's
 * yaw.
 *
 * Needed because the scene can now be turned: without this, after rotating the
 * board 90° a hand movement to the user's right would push the piece along a
 * board axis that no longer points right on screen.
 */
private fun toBoardSpaceDelta(worldDelta: Vector3, yawDegrees: Float): Vector3 {
    val radians = (-yawDegrees * PI / 180.0).toFloat()
    val cosine = cos(radians)
    val sine = sin(radians)
    return Vector3(
        worldDelta.x * cosine + worldDelta.z * sine,
        worldDelta.y,
        -worldDelta.x * sine + worldDelta.z * cosine,
    )
}

/**
 * Reads the headset's height once at startup and drops the whole board so its
 * base plate sits [BASE_PLATE_BELOW_HEAD_M] below the user's head.
 *
 * Runs once and stops - this is a one-shot calibration, not continuous
 * following, so the board stays put afterwards (and doesn't fight the scene
 * free-drag).
 */
@Composable
private fun HeadHeightCalibration(
    anchor: Entity,
    placement: AnchorPlacement,
    handTrackingRoot: Entity,
    basePlateOffsetY: Float,
) {
    val hmdTrackingProvider = remember { HMDTrackingProvider() }

    DisposableEffect(hmdTrackingProvider) {
        val startResult = hmdTrackingProvider.start()
        Log.i(
            HAND_GESTURE_LOG_TAG,
            "HMD start() -> $startResult, supportState=${hmdTrackingProvider.supportState}",
        )
        onDispose { hmdTrackingProvider.stop() }
    }

    LaunchedEffect(hmdTrackingProvider) {
        repeat(HEAD_CALIBRATION_MAX_POLLS) {
            val rawHead = hmdTrackingProvider.latestData.hmdPose.position
            // Vector3.ZERO is how this SDK signals "not tracked yet" - the same
            // check the SDK's own ShowControllerModel() sample uses on controller
            // poses. A real head is never at the Stage origin (the user's feet).
            if (rawHead != Vector3.ZERO) {
                // Raw tracking positions are NOT directly usable as entity
                // positions - convert through the same identity-transform root the
                // pinch indicators use (proven correct there). See debugging note #9.
                val headY = handTrackingRoot.convertPositionFrom(rawHead, null).y
                val anchorY = (headY - BASE_PLATE_BELOW_HEAD_M) - basePlateOffsetY
                placement.moveTo(anchor, Vector3(placement.position.x, anchorY, placement.position.z))
                Log.i(
                    HAND_GESTURE_LOG_TAG,
                    "head height calibration: headY=${"%.3f".format(headY)}m -> " +
                        "base plate at ${"%.3f".format(headY - BASE_PLATE_BELOW_HEAD_M)}m, " +
                        "anchorY=${"%.3f".format(anchorY)}m",
                )
                return@LaunchedEffect
            }
            delay(HEAD_CALIBRATION_POLL_INTERVAL_MS)
        }
        Log.w(
            HAND_GESTURE_LOG_TAG,
            "head height calibration gave up after $HEAD_CALIBRATION_MAX_POLLS polls " +
                "(supportState=${hmdTrackingProvider.supportState}) - keeping the " +
                "fixed ANCHOR_HEIGHT_M fallback",
        )
    }
}

/** The raw thumb-tip and index-tip positions this pinch check is based on. */
private data class FingerTips(val thumbTip: Vector3, val indexTip: Vector3) {
    val midpoint: Vector3 get() = Vector3.lerp(thumbTip, indexTip, 0.5f)
}

/**
 * Distance-based pinch detection with hysteresis (start/release thresholds) plus a
 * short debounce (PINCH_CONFIRM_TICKS) requiring a state flip to repeat across a
 * couple of polls before it's accepted, to filter single-frame tracking jitter.
 */
private class PinchTracker(private val label: String) {
    var isPinching: Boolean = false
        private set

    /** True only on the exact update() call where isPinching debounce-confirms false -> true. */
    var justStartedPinching: Boolean = false
        private set

    private var pendingState: Boolean? = null
    private var pendingCount = 0

    /** Returns the raw thumb/index tip positions whenever the hand is tracked, regardless of pinch state. */
    fun update(pose: HandPose?): FingerTips? {
        justStartedPinching = false
        if (pose == null) {
            isPinching = false
            pendingState = null
            pendingCount = 0
            return null
        }
        val thumbTip = pose.joint(HandJoint.Index.THUMB_TIP).position
        val indexTip = pose.joint(HandJoint.Index.INDEX_TIP).position
        val distance = Vector3.distance(thumbTip, indexTip)
        val instantPinching = if (isPinching) distance <= PINCH_RELEASE_DISTANCE_M else distance <= PINCH_START_DISTANCE_M
        when {
            instantPinching == isPinching -> {
                pendingState = null
                pendingCount = 0
            }
            pendingState == instantPinching -> {
                pendingCount++
                if (pendingCount >= PINCH_CONFIRM_TICKS) {
                    val wasPinching = isPinching
                    isPinching = instantPinching
                    pendingState = null
                    pendingCount = 0
                    justStartedPinching = !wasPinching && isPinching
                    Log.i(
                        HAND_GESTURE_LOG_TAG,
                        "$label pinch=${isPinching} at distance=${"%.4f".format(distance)}m",
                    )
                }
            }
            else -> {
                pendingState = instantPinching
                pendingCount = 1
            }
        }
        return FingerTips(thumbTip, indexTip)
    }
}

/**
 * A small sphere that follows one hand's pinch point; color signals pinch state.
 *
 * Raw hand-joint positions are not already expressed in this entity's parent
 * frame - per Entity.convertPositionFrom's KDoc and the SDK's own
 * ShowControllerModel() sample (Plane.kt), a raw tracking position must be
 * converted via `referenceEntity.convertPositionFrom(rawPosition, null)` before
 * it's valid to hand to setPosition(). referenceEntity here is this entity's own
 * parent (the shared handTrackingRoot, itself a plain identity-transform child of
 * `content` - matching the sample's `controllerEntity`).
 */
private class PinchIndicator(val entity: ModelEntity, private val material: UnlitMaterial) {
    fun update(referenceEntity: Entity, point: Vector3?, isPinching: Boolean) {
        if (point == null) {
            entity.enabled = false
            return
        }
        entity.enabled = true
        val localPosition = referenceEntity.convertPositionFrom(point, null)
        entity.components[TransformComponent::class.java]?.setPosition(localPosition)
        material.setBaseColor(if (isPinching) PINCH_INDICATOR_ACTIVE_COLOR else PINCH_INDICATOR_IDLE_COLOR)
    }
}

private fun createPinchIndicator(): PinchIndicator {
    val mesh = MeshResource.createSphere(PINCH_INDICATOR_RADIUS_M)
    val material = UnlitMaterial.create().apply { setBaseColor(PINCH_INDICATOR_IDLE_COLOR) }
    val entity = ModelEntity(mesh, material).apply { enabled = false }
    return PinchIndicator(entity, material)
}

/**
 * No visual output - a background poller driving every gesture in the game.
 *
 * While PLAYING:
 * - one-hand pinch + move sideways tracks the piece to the hand continuously
 *   (one rendered cell of travel = one column, no per-gesture step limit),
 * - holding the hand below the pinch point soft-drops on a timer,
 * - a quick double-pinch on either hand rotates clockwise.
 *
 * While NOT playing (start screen, paused, game-over) the same pinches instead
 * manipulate the whole scene: one hand translates in 6DoF, two hands translate +
 * yaw + scale together. See the `!isPlaying` branch below.
 */
@Composable
private fun HandGestureController(
    engine: GameEngine,
    isPlaying: Boolean,
    /** False when V2 owns piece input; scene manipulation stays on hand tracking regardless. */
    pieceControlEnabled: Boolean,
    anchor: Entity,
    placement: AnchorPlacement,
    handTrackingRoot: Entity,
    cellStepMeters: Float,
    leftThumbIndicator: PinchIndicator,
    leftIndexIndicator: PinchIndicator,
    rightThumbIndicator: PinchIndicator,
    rightIndexIndicator: PinchIndicator,
) {
    val handTrackingProvider = remember { HandTrackingProvider() }
    val latestIsPlaying = rememberUpdatedState(isPlaying)
    val latestPieceControlEnabled = rememberUpdatedState(pieceControlEnabled)

    DisposableEffect(handTrackingProvider) {
        val startResult = handTrackingProvider.start()
        Log.i(
            HAND_GESTURE_LOG_TAG,
            "start() -> $startResult, supportState=${handTrackingProvider.supportState}, state=${handTrackingProvider.state}",
        )
        onDispose { handTrackingProvider.stop() }
    }

    LaunchedEffect(handTrackingProvider) {
        // Support/state can take a moment to negotiate after start(); log it again
        // shortly after so logcat shows whether it ever leaves PENDING/WITHOUT_PERMISSION.
        delay(2000)
        Log.i(
            HAND_GESTURE_LOG_TAG,
            "2s after start: supportState=${handTrackingProvider.supportState}, state=${handTrackingProvider.state}",
        )

        val leftTracker = PinchTracker("left")
        val rightTracker = PinchTracker("right")
        var moveOriginPoint: Vector3? = null
        // Net columns already applied during the current pinch, and a correction that
        // re-anchors the hand->column mapping when a move is blocked. Together these
        // make horizontal movement track the hand instead of stepping one cell per
        // timer tick.
        var appliedColumnSteps = 0
        var columnBaselineCorrectionM = 0f
        var lastSoftDropAtMs = 0L
        // The piece this pinch-hold began on. A held pinch only ever steers that one
        // piece: if it locks mid-pinch, the session goes stale and the user has to
        // release and re-pinch, rather than have the remaining hand travel drag the
        // freshly spawned piece across the board.
        var movePieceId = NO_PIECE_ID
        var moveSessionStaleLogged = false
        var lastPinchStartMsLeft = 0L
        var lastPinchStartMsRight = 0L
        // Which piece each hand's previous pinch-start landed on, so the two halves of
        // a double-pinch must belong to the SAME piece to count as a rotate. Otherwise
        // re-pinching to start a fresh session right after a lock reads as the second
        // half of a double-pinch and spins the new piece unasked.
        var lastPinchPieceLeft = NO_PIECE_ID
        var lastPinchPieceRight = NO_PIECE_ID
        var loggedFirstData = false

        // Scene manipulation (only while !isPlaying - see below). The anchor's live
        // transform comes from the shared AnchorPlacement, NOT a local copy - head
        // height calibration also moves the anchor, and a local copy would go stale
        // and snap the board back on the next drag.
        //
        // One hand pinching = 6DoF translate. Two hands pinching = translate (by the
        // midpoint) + yaw (by the twist) + scale (by the span), all at once, like a
        // standard two-handed transform. Two-hand wins when both are pinching.
        var twoHandStartSpan: Float? = null
        var twoHandStartAngleDeg = 0f
        var twoHandStartMidpoint: Vector3? = null
        var twoHandStartPosition: Vector3? = null
        var twoHandStartYaw = 0f
        var twoHandStartScale = 1f
        var sceneDragStartHandPoint: Vector3? = null
        var sceneDragStartAnchorPosition: Vector3? = null

        while (isActive) {
            val data = handTrackingProvider.latestData
            if (!loggedFirstData) {
                loggedFirstData = true
                Log.i(HAND_GESTURE_LOG_TAG, "first latestData: left=${data.left != null}, right=${data.right != null}")
            }

            // Indicators update regardless of `started` - the balls are just a visual
            // of hand tracking, useful on the start screen too when tuning thresholds.
            // One ball per tracked fingertip (not the midpoint) so they visibly
            // converge on an actual pinch instead of floating between the fingers.
            val leftTips = leftTracker.update(data.left)
            val rightTips = rightTracker.update(data.right)
            leftThumbIndicator.update(handTrackingRoot, leftTips?.thumbTip, leftTracker.isPinching)
            leftIndexIndicator.update(handTrackingRoot, leftTips?.indexTip, leftTracker.isPinching)
            rightThumbIndicator.update(handTrackingRoot, rightTips?.thumbTip, rightTracker.isPinching)
            rightIndexIndicator.update(handTrackingRoot, rightTips?.indexTip, rightTracker.isPinching)
            // Converted into the Stage's coordinate convention (same call PinchIndicator
            // uses for its own visual placement) - the raw, unconverted joint position
            // does NOT reliably use the same up/down axis sense, which is why "pinch
            // down" specifically didn't work when this math used the raw midpoint.
            val leftPoint = leftTips?.midpoint?.let { handTrackingRoot.convertPositionFrom(it, null) }
            val rightPoint = rightTips?.midpoint?.let { handTrackingRoot.convertPositionFrom(it, null) }
            val leftPinching = leftTracker.isPinching
            val rightPinching = rightTracker.isPinching

            if (!latestIsPlaying.value) {
                // Gameplay movement state doesn't apply right now (not started yet,
                // paused, or game-over) - reset it so nothing carries over once
                // play actually (re)starts.
                moveOriginPoint = null

                // Scene manipulation is fully continuous and un-quantised, unlike
                // gameplay's column stepping - it should feel like grabbing the board.
                if (leftPinching && rightPinching && leftPoint != null && rightPoint != null) {
                    // TWO HANDS: translate + rotate + scale together.
                    sceneDragStartHandPoint = null
                    sceneDragStartAnchorPosition = null

                    val span = Vector3.distance(leftPoint, rightPoint)
                    // Twist is measured in the horizontal plane, so turning the hands
                    // like a steering wheel laid flat yaws the board.
                    val angleDeg = EulerAngles.radianToDegree(
                        atan2(rightPoint.z - leftPoint.z, rightPoint.x - leftPoint.x)
                    )
                    val midpoint = Vector3.lerp(leftPoint, rightPoint, 0.5f)
                    val startSpan = twoHandStartSpan
                    val startMidpoint = twoHandStartMidpoint
                    val startPosition = twoHandStartPosition
                    if (startSpan == null || startMidpoint == null || startPosition == null) {
                        twoHandStartSpan = span
                        twoHandStartAngleDeg = angleDeg
                        twoHandStartMidpoint = midpoint
                        twoHandStartPosition = placement.position
                        twoHandStartYaw = placement.yawDegrees
                        twoHandStartScale = placement.scale
                    } else if (startSpan >= MIN_TWO_HAND_SPAN_M && span >= MIN_TWO_HAND_SPAN_M) {
                        var angleDelta = angleDeg - twoHandStartAngleDeg
                        if (angleDelta > 180f) angleDelta -= 360f
                        if (angleDelta < -180f) angleDelta += 360f
                        val midpointDelta = midpoint - startMidpoint
                        placement.transformTo(
                            anchor,
                            nextPosition = startPosition + midpointDelta,
                            nextYawDegrees = twoHandStartYaw + SCENE_YAW_SIGN * angleDelta,
                            nextScale = twoHandStartScale * (span / startSpan),
                        )
                    }
                } else {
                    twoHandStartSpan = null
                    twoHandStartMidpoint = null
                    twoHandStartPosition = null

                    // ONE HAND: 6DoF translate. Z (depth) is included now, per the
                    // request - the earlier version deliberately froze it when only
                    // "上下左右" had been asked for.
                    val pinchPoint = if (leftPinching) leftPoint else if (rightPinching) rightPoint else null
                    if (pinchPoint != null) {
                        val dragStartHand = sceneDragStartHandPoint
                        val dragStartAnchor = sceneDragStartAnchorPosition
                        if (dragStartHand == null || dragStartAnchor == null) {
                            sceneDragStartHandPoint = pinchPoint
                            sceneDragStartAnchorPosition = placement.position
                        } else {
                            placement.moveTo(anchor, dragStartAnchor + (pinchPoint - dragStartHand))
                        }
                    } else {
                        // Pinch released - next pinch starts a fresh drag from wherever
                        // the scene was just left, not from the first drag's start.
                        sceneDragStartHandPoint = null
                        sceneDragStartAnchorPosition = null
                    }
                }

                delay(HAND_POLL_INTERVAL_MS)
                continue
            }
            twoHandStartSpan = null
            twoHandStartMidpoint = null
            twoHandStartPosition = null

            // Clearing the scene-drag anchors belongs to the isPlaying transition, NOT to
            // whichever scheme owns the piece - so it must happen BEFORE the V2 bail-out
            // below. Left after it (as it was), a pinch held across the !isPlaying ->
            // isPlaying edge - which is exactly what pressing 开始游戏 with a pinch does -
            // leaves a stale start point behind, and the next pause re-enters the scene-drag
            // branch and teleports the board by the whole accumulated delta. Harmless while
            // V1 was the default (the guard never tripped); live on every game with V2.
            sceneDragStartHandPoint = null
            sceneDragStartAnchorPosition = null

            if (!latestPieceControlEnabled.value) {
                // V2 owns the falling piece right now. Bail out before any engine call
                // so the two schemes can never both drive it and double every move.
                moveOriginPoint = null
                delay(HAND_POLL_INTERVAL_MS)
                continue
            }

            // Quick double-pinch (like a double-click) rotates clockwise - either
            // hand, same direction. Checked independently of the move state machine
            // below, on every tick a pinch freshly starts. The two hands keep
            // SEPARATE timestamps on purpose: a left pinch followed by a right pinch
            // is two different gestures, not a double-pinch.
            val now = System.currentTimeMillis()
            val currentPieceId = engine.pieceId
            if (leftTracker.justStartedPinching) {
                val sameGesture = now - lastPinchStartMsLeft <= DOUBLE_PINCH_WINDOW_MS &&
                    lastPinchPieceLeft == currentPieceId
                if (sameGesture) {
                    engine.rotateClockwise()
                    lastPinchStartMsLeft = 0L
                    lastPinchPieceLeft = NO_PIECE_ID
                } else {
                    lastPinchStartMsLeft = now
                    lastPinchPieceLeft = currentPieceId
                }
            }
            if (rightTracker.justStartedPinching) {
                val sameGesture = now - lastPinchStartMsRight <= DOUBLE_PINCH_WINDOW_MS &&
                    lastPinchPieceRight == currentPieceId
                if (sameGesture) {
                    engine.rotateClockwise()
                    lastPinchStartMsRight = 0L
                    lastPinchPieceRight = NO_PIECE_ID
                } else {
                    lastPinchStartMsRight = now
                    lastPinchPieceRight = currentPieceId
                }
            }

            when {
                leftPinching || rightPinching -> {
                    // leftPoint/rightPoint are non-null whenever that hand is merely
                    // *tracked*, regardless of pinch state - `leftPoint ?: rightPoint`
                    // would silently use a resting-but-visible left hand's position
                    // over an actively-pinching right hand's, making right-hand-only
                    // pinch look like it does nothing whenever the left hand is also
                    // in view. Must pick by which hand is actually pinching.
                    val pinchPoint = if (leftPinching) leftPoint else rightPoint
                    if (pinchPoint == null) {
                        delay(HAND_POLL_INTERVAL_MS)
                        continue
                    }
                    val origin = moveOriginPoint
                    if (origin == null) {
                        moveOriginPoint = pinchPoint
                        movePieceId = currentPieceId
                        moveSessionStaleLogged = false
                        appliedColumnSteps = 0
                        columnBaselineCorrectionM = 0f
                        lastSoftDropAtMs = now
                        delay(HAND_POLL_INTERVAL_MS)
                        continue
                    }

                    if (movePieceId != currentPieceId) {
                        // The piece this pinch started on has locked. Do nothing at all
                        // until the pinch is released - note `moveOriginPoint` stays
                        // set, which is what makes this latch: re-anchoring here would
                        // silently hand the still-held pinch to the new piece, exactly
                        // the mis-trigger this guards against.
                        if (!moveSessionStaleLogged) {
                            moveSessionStaleLogged = true
                            Log.i(
                                HAND_GESTURE_LOG_TAG,
                                "pinch session stale - started on piece $movePieceId, now $currentPieceId; " +
                                    "release and pinch again",
                            )
                        }
                        delay(HAND_POLL_INTERVAL_MS)
                        continue
                    }

                    // Undo the scene's yaw first: once the board can be turned, a hand
                    // movement to the user's right must still mean "right along the
                    // board", not along a world axis that no longer points that way.
                    val delta = toBoardSpaceDelta(pinchPoint - origin, placement.yawDegrees)

                    // HORIZONTAL: continuous 1:1 tracking, no per-gesture cell limit
                    // and no timer. One rendered cell of hand travel = one column, so
                    // the piece stays under the hand however far or fast it moves.
                    // `* placement.scale` keeps that true after the board is scaled.
                    val cellTravelM = (cellStepMeters * placement.scale).coerceAtLeast(0.001f)
                    val desiredColumnSteps =
                        ((delta.x - columnBaselineCorrectionM) / cellTravelM).roundToInt()
                    while (appliedColumnSteps < desiredColumnSteps) {
                        if (!engine.moveRight()) {
                            // Blocked by a wall or stack: re-anchor the mapping here so
                            // pulling back moves the piece immediately, instead of
                            // first having to undo the travel that went nowhere.
                            columnBaselineCorrectionM = delta.x - appliedColumnSteps * cellTravelM
                            break
                        }
                        appliedColumnSteps++
                    }
                    while (appliedColumnSteps > desiredColumnSteps) {
                        if (!engine.moveLeft()) {
                            columnBaselineCorrectionM = delta.x - appliedColumnSteps * cellTravelM
                            break
                        }
                        appliedColumnSteps--
                    }

                    // DOWN stays a held soft drop rather than 1:1 tracking: gravity is
                    // also moving the piece down, so row bookkeeping can't be trusted
                    // the way column bookkeeping can. Self-cancelling - raise the hand
                    // and it stops, no latch.
                    if (delta.y <= -SOFT_DROP_HAND_DROP_M) {
                        val intervalMs = (engine.currentFallIntervalMs() * SOFT_DROP_INTERVAL_FACTOR)
                            .toLong()
                            .coerceAtLeast(SOFT_DROP_MIN_INTERVAL_MS)
                        if (now - lastSoftDropAtMs >= intervalMs) {
                            engine.moveDown()
                            lastSoftDropAtMs = now
                        }
                    } else {
                        // Keep the timer fresh so re-lowering the hand doesn't
                        // immediately fire a backlog of drops.
                        lastSoftDropAtMs = now
                    }
                }
                else -> moveOriginPoint = null
            }

            delay(HAND_POLL_INTERVAL_MS)
        }
    }
}

/**
 * Unwraps a Compose [Context] down to the hosting [ComponentActivity].
 *
 * `LocalContext.current` inside `SpatialView`'s content isn't guaranteed to be the
 * bare Activity (it can arrive wrapped, same as anywhere else in Android), so this
 * walks the [ContextWrapper] chain rather than assuming a direct cast - needed to
 * reach `onBackPressedDispatcher` for the exit-confirm handling below.
 */
private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

@Composable
fun GamePage(bundle: Bundle?) {
    val context = LocalContext.current
    val highScoreStore = remember { SharedPreferencesHighScoreStore(context) }
    val basePlateMaterialStore = remember { SharedPreferencesBasePlateMaterialStore(context) }
    val baseMaterialsBundle = remember { BaseMaterialsBundle() }
    val basePlateMaterialLoader = remember { BasePlateMaterialLoader(baseMaterialsBundle) }
    var selectedBasePlateMaterial by remember { mutableStateOf(basePlateMaterialStore.get()) }
    val pieceMaterialLoader = remember { PieceMaterialLoader(baseMaterialsBundle) }
    val pieceMaterialStore = remember { SharedPreferencesPieceMaterialStore(context) }
    var selectedPieceMaterial by remember { mutableStateOf(pieceMaterialStore.get()) }
    var showAppearanceSettings by remember { mutableStateOf(false) }
    // Gameplay-info overlay on the start screen ("玩法" button). Declared alongside the other
    // start-screen modal flags (showAppearanceSettings/showExitConfirm below) even though it's
    // a sub-state of start_screen's own visibility gate, not a fourth peer condition on it -
    // see the AttachmentPanel("start_screen") content for how it nests.
    var showGameplayInfo by remember { mutableStateOf(false) }
    // Board enlarged 8x14 -> 10x18 per user request ("宽高大些，以便能容纳更多方块") -
    // engine and renderer must agree on the same size, so both are constructed
    // explicitly with matching dimensions instead of relying on GameEngine()'s
    // (now-stale) 8x14 default.
    val engine = remember { GameEngine(board = Board(width = BOARD_WIDTH, height = BOARD_HEIGHT)) }
    val renderer = remember { BoardCubeRenderer(boardWidth = BOARD_WIDTH, boardHeight = BOARD_HEIGHT) }
    // Lifted out of `initial` (remember-scoped here instead) so HandGestureController
    // can move it directly - lets the user pinch-drag the whole scene into a
    // comfortable spot before starting the game.
    val anchor = remember { Entity() }
    // Which scheme drives the falling piece. V2 is now the fixed default and there is no
    // longer a UI picker ("把V1操作隐藏，默认使用V2操作"), so in practice this never changes
    // after init; V1_HAND_TRACKING stays reachable only by editing this line. Kept as
    // state rather than a const so restoring the picker is a UI-only change - but note
    // that means NOTHING re-runs the keyed effect below, which is why the `initial` block
    // has to derive the plane's enabled flag itself instead of hardcoding it.
    @Suppress("CanBeVal")
    var controlScheme by remember { mutableStateOf(ControlScheme.V2_SYSTEM_GESTURE) }
    // V2's interactable glass plane behind the well. Created unconditionally (so the
    // entity identity is stable) but only enabled while V2 is the active scheme.
    val v2ControlPlane = remember { createV2ControlPlane(renderer.boardSpanX, renderer.boardSpanY) }
    // Seeded with the fixed fallback height; HeadHeightCalibration overwrites it
    // once the HMD reports a usable pose, and the scene free-drag reads/writes the
    // same holder so the two can't disagree.
    val anchorPlacement = remember { AnchorPlacement(Vector3(0f, ANCHOR_HEIGHT_M, -1.0f)) }
    // Plain identity-transform entity, parent of all four pinch indicators, added
    // directly to `content` in `initial` - exists purely so PinchIndicator can
    // call `handTrackingRoot.convertPositionFrom(rawJointPosition, null)` to turn
    // a raw hand-tracking position into a valid local position, mirroring the
    // SDK's own ShowControllerModel() sample (Plane.kt) exactly.
    val handTrackingRoot = remember { Entity() }
    // One ball per tracked fingertip (thumb + index, per hand) rather than one
    // ball at their midpoint - they visibly converge on an actual pinch.
    val leftThumbIndicator = remember { createPinchIndicator() }
    val leftIndexIndicator = remember { createPinchIndicator() }
    val rightThumbIndicator = remember { createPinchIndicator() }
    val rightIndexIndicator = remember { createPinchIndicator() }
    val soundEffects = remember { GameSoundEffects() }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var started by remember { mutableStateOf(false) }
    // 难度由配置窗口经 openStage 的 Bundle 送进来。解析失败退回 NORMAL —— 见
    // parseDifficulty 的 KDoc。不需要在这里写回 GameSettings：startGame() 本来就会写，
    // 而 startGame() 是唯一一个把难度交给 GameEngine 的地方。
    val stageDifficulty = remember(bundle) { parseDifficulty(bundle?.getString(DIFFICULTY_BUNDLE_KEY)) }
    var selectedDifficulty by remember { mutableStateOf(stageDifficulty) }
    // False until renderer.attachTo() (below, in `initial`) has finished building the
    // ~185-entity board pool. Gates the start screen's interactive content so the panel
    // itself can appear (title + a loading line) well before that finishes, instead of
    // being invisible for the whole synchronous build (user report: "进入应用加载过程有点
    // 长，能否先出现操作面板，显示加载中状态"). See the `initial` block for why binding the
    // panel earlier ALSO needs a `yield()` - reordering alone doesn't paint anything.
    var sceneReady by remember { mutableStateOf(false) }
    val isPlaying = started && snapshot.state == GameState.PLAYING

    // 2026-08-21 fix: the Stage has no window chrome and nothing previously
    // intercepted KEYCODE_BACK, so ComponentActivity's default onBackPressed()
    // (called for ANY controller input the system maps to Back - confirmed on
    // real hardware to include at least a single press and a double-press of
    // different physical buttons) went straight to Activity.finish() and killed
    // the whole game mid-play with zero warning - reported as an unexpected
    // crash/exit during app review. This registers our own callback so every one
    // of those inputs pauses (if a round is in progress) and asks for
    // confirmation instead of exiting immediately.
    val activity = remember(context) { context.findComponentActivity() }
    var showExitConfirm by remember { mutableStateOf(false) }
    var pausedForExitConfirm by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showExitConfirm) return
                // Modal overlays on the start screen are mutually exclusive and close
                // top-most-first: gameplay info (opened most recently, if open at all) before
                // appearance settings, matching the "谁后开就把之前那层关掉" rule for the rest
                // of this file's overlay handling.
                if (showGameplayInfo) {
                    showGameplayInfo = false
                    return
                }
                if (showAppearanceSettings) {
                    showAppearanceSettings = false
                    return
                }
                pausedForExitConfirm = started && snapshot.state == GameState.PLAYING
                if (pausedForExitConfirm) {
                    engine.pause()
                    snapshot = engine.snapshot()
                }
                showExitConfirm = true
            }
        }
        activity.onBackPressedDispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }

    DisposableEffect(Unit) {
        onDispose {
            soundEffects.release()
            // AssetBundle is Closeable (SDK class-level docs: "Close the
            // AssetBundle when no longer needed") - baseMaterialsBundle caches
            // one internally once any non-glass base-plate or piece material is
            // picked, shared by both loaders, and nothing else in this
            // composable's lifecycle ever closed it.
            baseMaterialsBundle.close()
        }
    }

    // The plane is only visible/interactable while V2 is the active scheme AND gameplay
    // is actually underway - NOT merely while `started` (that still covers paused/game-over).
    // 2026-08-20 fix: previously this only tracked `controlScheme` (which never changes,
    // since V2 is the fixed default), so the plane stayed enabled - and therefore
    // hit-testable - for the ENTIRE app lifetime once attached, including on the start
    // screen. User report: gaze-click worked on the start screen's 慢/中/快/开始游戏 buttons
    // before the board finished loading, but stopped working (direct touch still worked)
    // right after - i.e. exactly when `anchor.addChild(v2ControlPlane)` runs in `initial`.
    // Root cause per PICO's own docs (video-faqs "playback buttons obscured by video
    // panel"): 3D ECS entities and 2D AttachmentPanel content are NOT in the same depth-sort
    // domain, so the plane sitting physically behind the panel (z=-0.28 vs panel z=+0.17)
    // doesn't guarantee it's excluded from gaze-pinch ray targeting - gaze-pinch is a
    // separate far-field ray-cast against interactable colliders (spatial-design
    // motion-based-interaction.md), unlike near-field direct touch, which is why only gaze
    // broke. Keyed on `isPlaying` (not `controlScheme`) so it actually reacts to state
    // changes; `entity.enabled = false` is the same mechanism BoardCubeRenderer already
    // uses to exclude locked/falling cubes from hit-testing.
    // 2026-08-28: briefly also gated on controller presence, then reverted same-day per user
    // request ("玻璃面板改为一直存在不用与手柄存在互斥") - the plane's visibility no longer
    // depends on whether a controller is paired.
    LaunchedEffect(controlScheme, isPlaying) {
        v2ControlPlane.enabled = controlScheme == ControlScheme.V2_SYSTEM_GESTURE && isPlaying
        Log.i(
            V2_LOG_TAG,
            "control scheme = ${controlScheme.name}, isPlaying=$isPlaying, plane enabled = ${v2ControlPlane.enabled}",
        )
    }

    HeadHeightCalibration(
        anchor = anchor,
        placement = anchorPlacement,
        handTrackingRoot = handTrackingRoot,
        basePlateOffsetY = renderer.basePlateOffsetY,
    )

    HandGestureController(
        engine = engine,
        isPlaying = isPlaying,
        pieceControlEnabled = controlScheme == ControlScheme.V1_HAND_TRACKING,
        anchor = anchor,
        placement = anchorPlacement,
        handTrackingRoot = handTrackingRoot,
        cellStepMeters = renderer.cellStepMeters,
        leftThumbIndicator = leftThumbIndicator,
        leftIndexIndicator = leftIndexIndicator,
        rightThumbIndicator = rightThumbIndicator,
        rightIndexIndicator = rightIndexIndicator,
    )

    // Thumbstick-driven controller input - a third, independent piece-control path alongside
    // V1/V2 (see ControllerMoveController's own KDoc for why it doesn't need their "exactly one
    // drives the engine" gate). `rememberUpdatedState` lets the single DisposableEffect-registered
    // listener always see the latest `isPlaying`/`engine` without re-registering the listener on
    // every recomposition.
    val controllerMoveController = remember { ControllerMoveController() }
    val latestIsPlayingForController = rememberUpdatedState(isPlaying)
    DisposableEffect(controllerMoveController) {
        controllerMoveController.start(
            onMoveLeft = { engine.moveLeft() },
            onMoveRight = { engine.moveRight() },
            onMoveDown = { engine.moveDown() },
            onRotate = { engine.rotateClockwise() },
            isPlaying = { latestIsPlayingForController.value },
            currentFallIntervalMs = { engine.currentFallIntervalMs() },
        )
        onDispose { controllerMoveController.stop() }
    }

    // Swaps the base plate's material when the user picks a new one from
    // BasePlateMaterialPicker. The !renderer.isAttached guard matters: this
    // effect fires immediately on first composition too, racing `initial`'s
    // own attachTo() call (which can take tens of seconds - see the isAttached
    // note on the frame-loop LaunchedEffect above for the same race). Without
    // the guard, the very first material would be built and torn down again a
    // moment after attachTo() finishes, for no visible reason.
    LaunchedEffect(selectedBasePlateMaterial) {
        if (!renderer.isAttached) return@LaunchedEffect
        renderer.setBasePlateMaterial(basePlateMaterialLoader.load(selectedBasePlateMaterial))
    }

    // Swaps the piece materials when the user picks a new one from
    // PieceMaterialPicker. Same isAttached guard as the base-plate effect
    // above, and for the same reason - this fires on first composition too,
    // racing initial's own attachTo() call.
    //
    // The explicit render() is NOT redundant, unlike the base plate's swap:
    // setBasePlateMaterial() destroys and recreates the ground entity, so its
    // new material is on screen the moment it returns, whereas
    // setPieceMaterials() only stores the map - nothing is visible until
    // render() rebinds each enabled cube via bindCubeMaterial(). The only other
    // caller of render() is the frame loop, and it fires solely on
    // engine.revision changes; sitting on the start screen after 返回开始画面
    // the tick loop is stopped, so revision never moves and a swatch tapped in
    // 外观设置 would store the new map and change nothing visible (leaving any
    // cube still showing a stale color/material stuck that way indefinitely).
    // render() is idempotent for a given snapshot and self-gates on isAttached,
    // so calling it here unconditionally is safe.
    LaunchedEffect(selectedPieceMaterial) {
        if (!renderer.isAttached) return@LaunchedEffect
        renderer.setPieceMaterials(
            if (selectedPieceMaterial == PieceMaterial.JELLY) null else pieceMaterialLoader.load(selectedPieceMaterial)
        )
        renderer.render(engine.snapshot())
    }

    LaunchedEffect(engine, started) {
        if (!started) return@LaunchedEffect
        while (true) {
            delay(engine.currentFallIntervalMs())
            engine.tick()
            snapshot = engine.snapshot()
            val events = engine.drainEvents()
            soundEffects.play(events)
            if (events.contains(GameEvent.GAME_OVER)) {
                highScoreStore.recordScore(snapshot.score)
            }
        }
    }

    // Single per-frame loop that owns ALL visual updating.
    //
    // Two jobs, and both fix reported bugs:
    // 1. Re-render whenever engine.revision changes. Previously render() ran only on
    //    the gravity tick, so gesture-driven moves stayed invisible for up to a full
    //    fall interval and then surfaced together - which is most of why fast movement
    //    looked like a teleport.
    // 2. Drive renderer.animate() every frame, so the falling piece eases toward its
    //    new cell instead of snapping.
    //
    // withFrameNanos rather than a fixed delay: it's synchronised to the display and
    // hands us a real timestamp, which the frame-rate-independent easing needs.
    LaunchedEffect(engine, renderer) {
        var lastFrameNanos = 0L
        var lastRevision = -1
        while (isActive) {
            withFrameNanos { frameNanos ->
                // isAttached matters: this loop can win the race against
                // SpatialView's `initial` block. Committing lastRevision for a render
                // that was skipped would leave the board blank forever, since the
                // revision would never differ again until the next engine change.
                if (renderer.isAttached && engine.revision != lastRevision) {
                    lastRevision = engine.revision
                    renderer.render(engine.snapshot())
                }
                if (lastFrameNanos != 0L) {
                    renderer.animate((frameNanos - lastFrameNanos) / 1_000_000_000f)
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    fun startGame() {
        GameSettings.difficulty = selectedDifficulty
        // The gameplay-info overlay's scrim blocks pointer input to 开始游戏 while open (see
        // GameplayInfoOverlay), so this path shouldn't be reachable with showGameplayInfo
        // still true - reset here anyway, defensively, per the modal-exclusivity rule: any
        // action that enters Playing/GameOver must force the gameplay overlay closed first.
        showGameplayInfo = false
        engine.start(selectedDifficulty)
        snapshot = engine.snapshot()
        // No explicit render(): engine.start() bumps revision, so the frame loop
        // picks it up on the very next frame.
        started = true
    }

    SpatialView(
        // V2's gesture detectors belong HERE, on the SpatialView that hosts the 3D
        // content - not on an AttachmentPanel, whose separate view host never sees
        // the MotionEvent from a ray hit on a 3D entity. Only pointerInput is added;
        // no sizing modifier (an arbitrary size here scales/clips the 3D content -
        // see debugging note #1).
        modifier = v2GestureModifier(
            engine = engine,
            controlPlane = v2ControlPlane,
            cellStepMeters = renderer.cellStepMeters,
            sceneScale = anchorPlacement.scale,
            sceneYawDegrees = anchorPlacement.yawDegrees,
            // Only while actually PLAYING - not merely `started`. `started` stays
            // true while paused and at game over, which armed the detectors exactly
            // when the pause / game-over overlay buttons needed the input.
            active = isPlaying && controlScheme == ControlScheme.V2_SYSTEM_GESTURE,
        ),
        initial = { content, attachments ->
            // Stage's origin is at the user's feet, not a window's center, so the whole
            // board/UI cluster is parented to one `anchor` entity positioned ~1m ahead.
            // Every other entity below is added as a *child* of anchor (never
            // content.addEntity directly), so their relative offsets don't change.
            // `anchor` and its position live in GamePage() (not here) because two
            // features move it: HeadHeightCalibration (once, at startup) and the
            // scene free-drag. This just applies whatever height the shared
            // AnchorPlacement currently holds - the fallback until the HMD reports in.
            // Goes through the placement rather than setPosition directly, so the
            // anchor's rotation and scale get initialised from it too.
            anchorPlacement.moveTo(anchor, anchorPlacement.position)
            content.addEntity(anchor)

            fun attach(id: String, position: Vector3, pitchDegrees: Float = 0f) {
                attachments.entity(id = id)?.apply {
                    components[TransformComponent::class.java]?.let { transform ->
                        transform.setPosition(position)
                        if (pitchDegrees != 0f) {
                            // EulerAngles are in DEGREES, pitch about X. A panel faces
                            // +Z (the user) at pitch 0 and lies flat facing +Y at -90,
                            // so anything in between tilts it back off the plate.
                            transform.eulerAngles = EulerAngles(pitchDegrees, 0f, 0f)
                        }
                    }
                    anchor.addChild(this)
                }
            }

            // Bind start_screen and yield BEFORE the ~185-entity board build below, so
            // its loading state gets a chance to actually paint. Order alone is not
            // enough: `initial` is one uninterrupted suspend block, so without a real
            // suspension point here the main thread would run straight through into
            // attachTo() with no frame drawn in between, same as before. Confirmed via
            // PICO Spatial SDK 6.0 docs (add-3d-content-to-spatialmodelview-and-
            // spatialview.md): `attachments.entity(id)` is available this early, and the
            // SDK's own sample suspends inside `initial` (withContext(Dispatchers.IO))
            // for the same reason.
            //
            // WHICH panels have to be attached here rather than after the build: any
            // panel whose content can become visible while `started` is still false,
            // because start_screen HIDES itself whenever one of them takes over
            // (`!started && !showExitConfirm && !showAppearanceSettings`). If such a
            // panel isn't in the render tree yet, that hand-off leaves the user staring
            // at an empty room for the rest of the board build. That's exactly the
            // three panels attached here:
            //   - start_screen        (the loading card itself)
            //   - appearance_settings (gated on showAppearanceSettings; the 外观设置
            //                          button on start_screen has no sceneReady guard,
            //                          unlike 开始游戏, so it IS tappable mid-load)
            //   - exit_confirm_overlay (gated on showExitConfirm; Back is a hardware
            //                          button and fires the OnBackPressedCallback at
            //                          any moment, board build included)
            // The five panels still attached after the build - score_hud, next_piece,
            // pause_button, pause_overlay, game_over_overlay - are all gated on
            // `started`, which cannot become true until sceneReady flips, i.e. not
            // before the build finishes. Attaching the three early costs nothing: their
            // content is gated on flags that are all false on a fresh launch, so they
            // bind an empty panel and draw nothing until something sets their flag.
            //
            // attachTo() itself ALSO yields periodically inside its loop (see its KDoc) -
            // this yield only guarantees the panels are bound and get first crack at a
            // frame before that loop's own per-cell cost (measured severe under load,
            // even ANR-triggering) has a chance to start starving the main thread.
            Log.i(HAND_GESTURE_LOG_TAG, "initial: attaching start_screen before board build")
            attach("start_screen", Vector3(0f, 0f, MAIN_PANEL_Z_M))
            attach("appearance_settings", Vector3(0f, 0f, MAIN_PANEL_Z_M))
            attach("exit_confirm_overlay", Vector3(0f, 0f, MAIN_PANEL_Z_M))
            yield()

            val boardBuildStartMs = System.currentTimeMillis()
            // Captured explicitly so it can be compared against selectedBasePlateMaterial's
            // value below - two separate reads of a Compose state var are not guaranteed to
            // agree once yield()s/suspension are involved, and the comparison below needs
            // to know what was actually handed to attachTo(), not just "the current value".
            val basePlateMaterialAtAttach = selectedBasePlateMaterial
            renderer.attachTo(anchor, basePlateMaterialLoader.load(basePlateMaterialAtAttach))
            sceneReady = true
            // Re-resolve ONLY if selectedBasePlateMaterial actually differs from what was
            // just applied above: the board build just took ~60s, during which the
            // LaunchedEffect(selectedBasePlateMaterial) below either bailed out
            // (renderer.isAttached was false) or, in a narrow window, ran but no-opped
            // (groundAnchor wasn't set yet) - so a swatch tapped mid-load would otherwise be
            // silently dropped: the picker UI and SharedPreferences would show the new pick,
            // but the actual base plate would keep whatever material was current at t=0.
            // Gating (rather than unconditionally re-applying basePlateMaterialAtAttach
            // again) skips a redundant destroy+recreate+load on the common nothing-changed
            // path, and avoids taking BaseMaterialsBundle's internal mutex for no reason
            // when nothing actually needs to change - see BaseMaterialsBundle's KDoc for the
            // race this and the mutex together close.
            if (selectedBasePlateMaterial != basePlateMaterialAtAttach) {
                renderer.setBasePlateMaterial(basePlateMaterialLoader.load(selectedBasePlateMaterial))
            }
            // Piece materials don't feed into attachTo()'s arguments (unlike the
            // ground material) - attachTo() builds the same locked/falling cube pool
            // regardless of which PieceMaterial is active, since every cell starts
            // enabled = false. So there's no "at attach" value to pass in; just resolve
            // and apply the current selection once, right after sceneReady = true, with
            // the same re-resolve-if-changed gating as the base-plate block above in
            // case a swap landed mid-load via the LaunchedEffect(selectedPieceMaterial)
            // above (which no-ops until isAttached is true).
            val initialPieceMaterial = selectedPieceMaterial
            renderer.setPieceMaterials(
                if (initialPieceMaterial == PieceMaterial.JELLY) null else pieceMaterialLoader.load(initialPieceMaterial)
            )
            if (selectedPieceMaterial != initialPieceMaterial) {
                renderer.setPieceMaterials(
                    if (selectedPieceMaterial == PieceMaterial.JELLY) null else pieceMaterialLoader.load(selectedPieceMaterial)
                )
            }
            // Same reason as the LaunchedEffect(selectedPieceMaterial) above:
            // setPieceMaterials() only stores the map, so the board isn't showing it
            // until something calls render(). Once for the whole resolve rather than
            // after each branch - the second setPieceMaterials call, when it runs,
            // supersedes the first, so only the final map needs painting.
            renderer.render(engine.snapshot())
            Log.i(HAND_GESTURE_LOG_TAG, "initial: board build took ${System.currentTimeMillis() - boardBuildStartMs}ms")

            // V2's glass control plane: same parent as the board so it inherits the
            // scene's position/yaw/scale, positioned behind the cubes.
            v2ControlPlane.components[TransformComponent::class.java]
                ?.setPosition(v2ControlPlanePosition(renderer.boardCenterY, renderer.basePlateFarEdgeZ))
            // Must compute the SAME predicate as the LaunchedEffect above, not a
            // hardcoded false. Either write can land last - the file already documents
            // that a LaunchedEffect declared in GamePage() can win the race against this
            // `initial` block (see the frame loop's isAttached note) - so a literal here
            // silently fights the effect. It was harmless only while V1 was the default
            // and both writes happened to agree on false; once V2 became the default and
            // the picker (the only writer of controlScheme, hence the only thing that could
            // re-run the keyed effect) was deleted, this line disabled the plane forever:
            // invisible AND, per createV2ControlPlane's KDoc, likely dropped from hit-testing.
            // Also gated on `isPlaying` now (always false here, since `started` never flips
            // true before `initial` runs) - see the LaunchedEffect above for why: the plane
            // must stay disabled outside actual gameplay so its oversized (2x board span)
            // collider can't compete with the start/pause/game-over panels' gaze targeting.
            v2ControlPlane.enabled = controlScheme == ControlScheme.V2_SYSTEM_GESTURE && isPlaying
            anchor.addChild(v2ControlPlane)

            // handTrackingRoot is a bare identity-transform entity added directly to
            // content (NOT anchor - unrelated to the board's (0,1.3,-1.0) offset).
            // All four pinch indicators are its children so PinchIndicator.update
            // can call handTrackingRoot.convertPositionFrom(rawJointPosition, null)
            // to turn a raw hand-tracking position into a valid local position -
            // setting a raw joint position directly (no conversion) placed the ball
            // nowhere near the actual hand; see the PinchIndicator KDoc.
            content.addEntity(handTrackingRoot)
            handTrackingRoot.addChild(leftThumbIndicator.entity)
            handTrackingRoot.addChild(leftIndexIndicator.entity)
            handTrackingRoot.addChild(rightThumbIndicator.entity)
            handTrackingRoot.addChild(rightIndexIndicator.entity)

            // Gameplay input no longer goes through an AttachmentPanel at all - see
            // HandGestureController, which reads hand-tracking joints directly and
            // doesn't need a hit-testable panel/entity in the scene.
            //
            // score/NEXT/pause stand along the base plate's near edge, tilted back
            // like a lectern, instead of floating around the board's sides. The
            // overlays stay upright and centred - they're read head-on, not glanced
            // down at. start_screen, appearance_settings and exit_confirm_overlay are
            // already attached above, before the board build - see there for why those
            // three specifically can't wait until now.
            val platePanelY = renderer.basePlateOffsetY + PLATE_PANEL_LIFT_M
            attach(
                "score_hud",
                Vector3(-PLATE_PANEL_SIDE_X_M, platePanelY, PLATE_PANEL_NEAR_Z_M),
                pitchDegrees = PLATE_PANEL_TILT_DEG,
            )
            attach(
                "next_piece",
                Vector3(0f, platePanelY, PLATE_PANEL_NEAR_Z_M),
                pitchDegrees = PLATE_PANEL_TILT_DEG,
            )
            attach(
                "pause_button",
                Vector3(PLATE_PANEL_SIDE_X_M, platePanelY, PLATE_PANEL_NEAR_Z_M),
                pitchDegrees = PLATE_PANEL_TILT_DEG,
            )
            attach("pause_overlay", Vector3(0f, 0f, MAIN_PANEL_Z_M))
            attach("game_over_overlay", Vector3(0f, 0f, MAIN_PANEL_Z_M))
        },
        attachments = {
            AttachmentPanel(id = "start_screen") {
                if (!started && !showExitConfirm && !showAppearanceSettings) {
                    // Wrapped in a Box so the gameplay-info overlay can sit above CandyCard as
                    // a Box sibling, drawn/hit-tested last = on top (CandyCard does not use
                    // backgroundMaterial - it's a plain opaque background+clip - so this
                    // sibling-overlay approach is safe here; see gameplay_button below for the
                    // one that briefly wasn't). CandyCard gets an explicit align(Center): once
                    // the overlay opens, this Box grows to fit the wider 480dp overlay card,
                    // and without an explicit alignment CandyCard (Box's default TopStart)
                    // would visibly jump sideways at that moment instead of staying anchored
                    // where the user already sees it.
                    Box {
                        CandyCard(modifier = Modifier.align(Alignment.Center)) {
                            Column(
                                modifier = Modifier.width(IntrinsicSize.Max),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // "玩法" entry (gameplay_button) - lives INSIDE CandyCard's own
                                // Column now (first child, right-aligned via a fillMaxWidth Box)
                                // rather than floating outside the card's clip as a corner badge.
                                // It used to be a Box sibling anchored past CandyCard's edge with
                                // an outward offset - that put the button over bare passthrough
                                // once the card render0ed, reading as "outside the window" (caught
                                // on Overwinter's identical pattern; see memory
                                // backgroundmaterial-is-compositor-layer for the related but
                                // distinct compositor-occlusion issue that pattern also risks
                                // when a card DOES use backgroundMaterial). The Column's own
                                // Modifier.width(IntrinsicSize.Max) gives this fillMaxWidth Box a
                                // real width to resolve against (CandyCard itself doesn't force
                                // one - it just wraps its content) so TopEnd alignment lands at
                                // the card's actual right edge instead of stretching unbounded.
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { showGameplayInfo = true },
                                        modifier = Modifier.align(Alignment.TopEnd),
                                        size = ButtonDefaults.Small,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CandyAccentMint.copy(alpha = GAMEPLAY_BUTTON_CONTAINER_ALPHA),
                                            contentColor = CandyAccentMintInk,
                                        ),
                                        leadingIcon = { GameplayButtonBadge() },
                                    ) { Text("玩法") }
                                }
                                Text(
                                    text = "空间方块",
                                    color = CandyCardInk,
                                    style = PicoTheme.typography.titleLarge,
                                )
                                Text(
                                    text = "历史最高 ${highScoreStore.highScore()}",
                                    color = CandyCardInkDim,
                                    style = PicoTheme.typography.bodyMedium,
                                )
                                // Difficulty row and gesture hints are NOT gated on sceneReady -
                                // per user request 2026-08-12 ("加载中应该同样以这个窗口显示"),
                                // loading and ready share the identical card; only the button
                                // below changes. Difficulty picking itself is left enabled while
                                // loading (not asked for otherwise) - it only writes
                                // selectedDifficulty, read later by startGame().
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf(Difficulty.SLOW to "慢", Difficulty.NORMAL to "中", Difficulty.FAST to "快")
                                        .forEach { (difficulty, label) ->
                                            Button(
                                                onClick = { selectedDifficulty = difficulty },
                                                colors = candyButtonColors(primary = difficulty == selectedDifficulty),
                                            ) { Text(label) }
                                        }
                                }
                                // V1 (hand-tracking polling) picker UI removed - V2 (SDK spatial
                                // gestures on the glass plane behind the well) is now the fixed
                                // default. ControlScheme.V1_HAND_TRACKING and its supporting code
                                // are kept as a hidden fallback rather than deleted.
                                Button(
                                    onClick = { showAppearanceSettings = true },
                                    colors = candyButtonColors(primary = false),
                                ) { Text("外观设置") }
                                Text(
                                    text = "旋转：注视方块，双击旋转方块",
                                    color = CandyCardInkDim,
                                    style = PicoTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "移动：手指捏合移动控制方块方向",
                                    color = CandyCardInkDim,
                                    style = PicoTheme.typography.bodySmall,
                                )
                                // The one element that DOES depend on sceneReady: label/color/
                                // enabled all flip together, rather than a separate loading
                                // placeholder replacing the whole lower section (that was the
                                // v3 design; user asked for this simpler one instead). No new
                                // disabled-visual styling - primary=false reuses the same muted
                                // color already used for unselected difficulty buttons.
                                Button(
                                    modifier = Modifier.width(200.dp),
                                    enabled = sceneReady,
                                    onClick = { if (sceneReady) startGame() },
                                    colors = candyButtonColors(primary = sceneReady),
                                ) {
                                    Text(if (sceneReady) "开始游戏" else "加载中")
                                }
                            }
                        }

                        if (showGameplayInfo) {
                            GameplayInfoOverlay(onDismiss = { showGameplayInfo = false })
                        }
                    }
                }
            }
            AttachmentPanel(id = "appearance_settings") {
                if (showAppearanceSettings) {
                    CandyCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = "外观设置",
                                color = CandyCardInk,
                                style = PicoTheme.typography.titleMedium,
                            )
                            BasePlateMaterialPicker(
                                selected = selectedBasePlateMaterial,
                                onSelect = { material ->
                                    selectedBasePlateMaterial = material
                                    basePlateMaterialStore.set(material)
                                },
                            )
                            PieceMaterialPicker(
                                selected = selectedPieceMaterial,
                                onSelect = { material ->
                                    selectedPieceMaterial = material
                                    pieceMaterialStore.set(material)
                                },
                            )
                            Button(
                                onClick = { showAppearanceSettings = false },
                                colors = candyButtonColors(primary = true),
                            ) { Text("完成") }
                        }
                    }
                }
            }
            AttachmentPanel(id = "score_hud") {
                if (started) {
                    CandyStatusCard {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "SCORE",
                                color = CandyCardInkDim,
                                style = PicoTheme.typography.labelSmall,
                            )
                            Text(
                                text = "${snapshot.score}",
                                color = CandyAccentMint,
                                style = PicoTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
            AttachmentPanel(id = "next_piece") {
                if (started) {
                    CandyStatusCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "NEXT",
                                color = CandyCardInkDim,
                                style = PicoTheme.typography.labelSmall,
                            )
                            // Mini candy blocks in the piece's own color, matching the
                            // design mockup - replaces the old enum-name text label.
                            NextPiecePreview(type = snapshot.nextType)
                        }
                    }
                }
            }
            AttachmentPanel(id = "pause_button") {
                if (started) {
                    Button(
                        onClick = {
                            if (snapshot.state == GameState.PAUSED) engine.resume() else engine.pause()
                            snapshot = engine.snapshot()
                        },
                        colors = candyButtonColors(primary = snapshot.state == GameState.PAUSED),
                    ) {
                        Text(if (snapshot.state == GameState.PAUSED) "继续" else "暂停")
                    }
                }
            }
            AttachmentPanel(id = "pause_overlay") {
                if (started && snapshot.state == GameState.PAUSED && !showExitConfirm) {
                    CandyCard {
                        Text(
                            "已暂停",
                            color = CandyCardInk,
                            style = PicoTheme.typography.titleMedium,
                        )
                    }
                }
            }
            AttachmentPanel(id = "game_over_overlay") {
                if (started && snapshot.state == GameState.GAME_OVER && !showExitConfirm) {
                    CandyCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "GAME OVER",
                                color = CandyCardInk,
                                style = PicoTheme.typography.titleMedium,
                            )
                            Text(
                                "${snapshot.score}",
                                color = CandyCardInk,
                                style = PicoTheme.typography.titleSmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { startGame() },
                                    colors = candyButtonColors(primary = true),
                                ) { Text("再来一局") }
                                Button(
                                    onClick = { started = false },
                                    colors = candyButtonColors(primary = false),
                                ) { Text("返回开始画面") }
                            }
                        }
                    }
                }
            }
            AttachmentPanel(id = "exit_confirm_overlay") {
                if (showExitConfirm) {
                    CandyCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "退出游戏？",
                                color = CandyCardInk,
                                style = PicoTheme.typography.titleMedium,
                            )
                            Text(
                                "当前进度将不会保存",
                                color = CandyCardInkDim,
                                style = PicoTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        showExitConfirm = false
                                        if (pausedForExitConfirm) {
                                            engine.resume()
                                            snapshot = engine.snapshot()
                                            pausedForExitConfirm = false
                                        }
                                    },
                                    colors = candyButtonColors(primary = false),
                                ) { Text("取消") }
                                Button(
                                    onClick = { activity?.finish() },
                                    colors = candyButtonColors(primary = true),
                                ) { Text("退出") }
                            }
                        }
                    }
                }
            }
        },
    )
}
