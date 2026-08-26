package tech.illusion.spacecube.content

import android.util.Log
import com.pico.spatial.core.ecs.resource.AssetBundle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val BASE_MATERIALS_BUNDLE_LOG_TAG = "SpaceCubeBaseMaterialsBundle"
private const val BASE_MATERIALS_BUNDLE_PATH = "asset://base_materials.bundle"

/**
 * Owns the one shared `AssetBundle` for `base_materials.bundle`, used by
 * both `BasePlateMaterialLoader` and `PieceMaterialLoader` - opening it
 * twice (once per loader) would double the 25MB load and defeat the point
 * of caching by path. Extracted from `BasePlateMaterialLoader`, which
 * previously owned this alone; see that file's history for why the mutex
 * spans the *entire* [withBundle] call, not just the open: two independent
 * coroutines can call into a loader concurrently (a swatch tapped during
 * the ~60s cold-start board build races the `initial` block's own
 * resolve), and without full serialization both could see the bundle as
 * unopened at once and each call `AssetBundle.load(...)`.
 */
internal class BaseMaterialsBundle {
    private var bundle: AssetBundle? = null
    private val mutex = Mutex()

    /**
     * Runs [action] with exclusive access to the shared bundle, opening it
     * (once, cached for this instance's lifetime) on first use if it isn't
     * already open. [action] receives `null` if the open failed - callers
     * should fall back to their own default material rather than propagate
     * the failure.
     */
    suspend fun <T> withBundle(action: suspend (AssetBundle?) -> T): T = mutex.withLock {
        val loadedBundle = bundle ?: runCatching { AssetBundle.load(BASE_MATERIALS_BUNDLE_PATH) }
            .onFailure {
                Log.w(BASE_MATERIALS_BUNDLE_LOG_TAG, "failed to load $BASE_MATERIALS_BUNDLE_PATH", it)
            }
            .getOrNull()
            ?.also { bundle = it }
        action(loadedBundle)
    }

    /**
     * Closes the cached `AssetBundle`, if one was ever opened - per its
     * class-level docs ("Close the AssetBundle when no longer needed").
     * Safe to call more than once (a second call sees `bundle == null` and
     * no-ops). Expected to run only after every in-flight [withBundle] call
     * has already been cancelled - callers should invoke this from a
     * `DisposableEffect(Unit) { onDispose { ... } }` at the same composable
     * scope that owns this instance.
     */
    fun close() {
        val hadBundle = bundle != null
        bundle?.close()
        bundle = null
        Log.i(BASE_MATERIALS_BUNDLE_LOG_TAG, "close(): AssetBundle ${if (hadBundle) "closed" else "was already null, no-op"}")
    }
}
