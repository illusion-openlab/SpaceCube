package tech.illusion.spacecube.content

import android.util.Log
import com.pico.spatial.core.ecs.resource.AssetBundle
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.ShaderGraphMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.illusion.spacecube.game.BasePlateMaterial

private const val BASE_PLATE_LOADER_LOG_TAG = "SpaceCubeBasePlateMaterial"

// Unchanged from BoardCubeRenderer's original attachGround() - moved here
// verbatim so the glass look never depends on whether the AssetBundle exists.
private const val GROUND_OPACITY = 0.80f

private const val BASE_MATERIALS_BUNDLE_PATH = "asset://base_materials.bundle"

// Filled in for real by Task 7, using whatever scene/material names Spatial
// Editor's export actually produced - see
// docs/superpowers/plans/2026-08-26-base-plate-material-picker-editor-notes.md
// for the confirmed values. These are best-guess placeholders until then;
// the try/catch in load() below means a wrong path here degrades to glass
// with a logged warning instead of crashing.
private fun bundlePathFor(material: BasePlateMaterial): String = when (material) {
    BasePlateMaterial.GLASS -> error("GLASS has no AssetBundle path")
    BasePlateMaterial.WOOD_02 -> "BaseMaterials/Root/MyMaterials/Wood_02"
    BasePlateMaterial.TILES_04 -> "BaseMaterials/Root/MyMaterials/Tiles_04"
    BasePlateMaterial.WOOD_12 -> "BaseMaterials/Root/MyMaterials/Wood_12"
    BasePlateMaterial.TRAVERTINE_09 -> "BaseMaterials/Root/MyMaterials/Travertine_09"
}

/**
 * Resolves a [BasePlateMaterial] selection to a live [Material] instance.
 *
 * Lazy by design: glass never touches the AssetBundle at all. The bundle
 * itself is loaded at most once (cached for the loader's lifetime) on first
 * request for any non-glass material; each individual material load after
 * that is a fresh [ShaderGraphMaterial.loadFromAssetBundle] call rather than
 * an instance cache, because destroying the ModelEntity that held a previous
 * material auto-releases it (see AssetBundle release-semantics docs) - an
 * instance cache would hand back an already-released material on the second
 * selection of the same swatch.
 */
internal class BasePlateMaterialLoader {
    private var bundle: AssetBundle? = null

    suspend fun load(selection: BasePlateMaterial): Material = withContext(Dispatchers.IO) {
        if (selection == BasePlateMaterial.GLASS) return@withContext createGlassMaterial()

        val loadedBundle = bundle ?: runCatching { AssetBundle.load(BASE_MATERIALS_BUNDLE_PATH) }
            .onFailure {
                Log.w(BASE_PLATE_LOADER_LOG_TAG, "failed to load $BASE_MATERIALS_BUNDLE_PATH, falling back to glass", it)
            }
            .getOrNull()
            ?.also { bundle = it }
            ?: return@withContext createGlassMaterial()

        runCatching { ShaderGraphMaterial.loadFromAssetBundle(loadedBundle, bundlePathFor(selection)) }
            .onFailure {
                Log.w(BASE_PLATE_LOADER_LOG_TAG, "failed to load material for $selection, falling back to glass", it)
            }
            .getOrDefault(createGlassMaterial())
    }

    private fun createGlassMaterial(): UnlitMaterial = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
        setBaseColor(Color4(0.92f, 0.89f, 0.85f, 1f))
        setOpacity(GROUND_OPACITY)
        setDepthWrite(false)
    }
}
