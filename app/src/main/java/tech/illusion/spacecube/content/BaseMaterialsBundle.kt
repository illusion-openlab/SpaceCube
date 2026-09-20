package tech.illusion.spacecube.content

import android.util.Log
import com.pico.spatial.core.ecs.resource.AssetBundle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val BASE_MATERIALS_BUNDLE_LOG_TAG = "SpaceCubeBaseMaterialsBundle"
private const val BASE_MATERIALS_BUNDLE_PATH = "asset://base_materials.bundle"

/**
 * Handle onto the ONE process-wide `AssetBundle` for `base_materials.bundle`,
 * used by both `BasePlateMaterialLoader` and `PieceMaterialLoader` - opening it
 * twice would double the 25MB load and defeat the point of caching by path.
 *
 * **Why a handle and not an owner (2026-09-20).** Since the window/Stage split
 * there are two live Compose compositions at once - `ConfigPage`'s
 * WindowContainer and `GamePage`'s Stage - and each constructs its own
 * `BaseMaterialsBundle`. They are deliberately alive *simultaneously*: the
 * config window stays up for the whole 60~95s board build. When each instance
 * owned its own `AssetBundle` field and closed it on dispose, the Stage
 * disposing on the return trip would call `close()` while the config window was
 * still using the bundle - and per the SDK's own docs (`AssetBundle` resource
 * management: "Scenes or resources with the same path occupy memory only once,
 * regardless of how many times they are loaded", and `close()` "release[s] the
 * AssetBundle instance and all cached data of resources managed by it") that is
 * the *same* underlying data, pulled out from under the still-open window. The
 * symptom would have been silent: both loaders swallow failures with
 * `runCatching` and fall back to JELLY/GLASS, so a dead PBR swatch would leave
 * nothing in logcat.
 *
 * So instances are now cheap handles over a shared bundle plus a handle count in
 * [Companion]. Constructing a handle increments the count; [close] decrements it
 * and only really closes the underlying `AssetBundle` when the last handle goes.
 * A later handle re-opens it from scratch, which is correct: the bundle is
 * cached by path, so reopening after a genuine last-release is just a reload.
 *
 * **Mutex discipline is unchanged, just widened to match.** The mutex now lives
 * in [Companion] alongside the bundle it protects, because that is the scope the
 * shared field actually needs; it still spans the *entire* [withBundle] call,
 * not just the open. Extracted originally from `BasePlateMaterialLoader`; see
 * that file's history for why: two independent coroutines can call into a loader
 * concurrently (a swatch tapped during the ~60s cold-start board build races the
 * `initial` block's own resolve), and without full serialization both could see
 * the bundle as unopened at once and each call `AssetBundle.load(...)`. With two
 * containers in play that race is now cross-composition too, which a
 * per-instance mutex could not have covered at all.
 */
internal class BaseMaterialsBundle {
    /** Guards against a double [close] on one handle decrementing the count twice. */
    private var released = false

    init {
        synchronized(HANDLE_LOCK) { handleCount++ }
    }

    /**
     * Runs [action] with exclusive access to the shared bundle, opening it
     * (once per process, for as long as at least one handle is open) on first
     * use if it isn't already open. [action] receives `null` if the open failed -
     * callers should fall back to their own default material rather than
     * propagate the failure.
     */
    suspend fun <T> withBundle(action: suspend (AssetBundle?) -> T): T = mutex.withLock {
        val loadedBundle = sharedBundle ?: runCatching { AssetBundle.load(BASE_MATERIALS_BUNDLE_PATH) }
            .onFailure {
                Log.w(BASE_MATERIALS_BUNDLE_LOG_TAG, "failed to load $BASE_MATERIALS_BUNDLE_PATH", it)
            }
            .getOrNull()
            ?.also { sharedBundle = it }
        action(loadedBundle)
    }

    /**
     * Releases this handle. The shared `AssetBundle` is actually closed - per
     * its class-level docs ("Close the AssetBundle when no longer needed") -
     * only when this was the *last* open handle; while another page still holds
     * one, this is just a decrement and the bundle stays open for it.
     *
     * Safe to call more than once (a second call on the same handle no-ops).
     * Expected to run only after every [withBundle] call issued *through this
     * handle* has already been cancelled - callers should invoke this from a
     * `DisposableEffect(Unit) { onDispose { ... } }` at the same composable
     * scope that owns this instance. Calls in flight through *other* handles are
     * exactly what the handle count protects.
     */
    fun close() {
        val bundleToClose = synchronized(HANDLE_LOCK) {
            if (released) {
                Log.i(BASE_MATERIALS_BUNDLE_LOG_TAG, "close(): handle already released, no-op")
                return
            }
            released = true
            handleCount--
            if (handleCount > 0) {
                Log.i(
                    BASE_MATERIALS_BUNDLE_LOG_TAG,
                    "close(): handle released, $handleCount still open - AssetBundle kept",
                )
                return
            }
            sharedBundle.also { sharedBundle = null }
        }
        bundleToClose?.close()
        Log.i(
            BASE_MATERIALS_BUNDLE_LOG_TAG,
            "close(): last handle released, AssetBundle " +
                if (bundleToClose != null) "closed" else "was never opened, no-op",
        )
    }

    /**
     * Process-level state behind every handle. Plain `synchronized` rather than
     * the coroutine [mutex] for the counter, because [close] runs from
     * `onDispose` and cannot suspend; [mutex] keeps its original job of
     * serialising the open-and-use path.
     */
    private companion object {
        val HANDLE_LOCK = Any()
        val mutex = Mutex()

        @Volatile
        var sharedBundle: AssetBundle? = null

        var handleCount = 0
    }
}
