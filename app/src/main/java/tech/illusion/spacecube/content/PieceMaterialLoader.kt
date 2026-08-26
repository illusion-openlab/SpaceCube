package tech.illusion.spacecube.content

import android.util.Log
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.ShaderGraphMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color3
import com.pico.spatial.core.math.Color4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.illusion.spacecube.game.PieceMaterial
import tech.illusion.spacecube.game.PieceType

private const val PIECE_MATERIAL_LOADER_LOG_TAG = "SpaceCubePieceMaterial"

// Same values BoardCubeRenderer.createCube() has always used for the
// falling-piece jelly look - reproduced here (not imported from there) so
// this loader owns its own JELLY material construction and never needs to
// reach into the renderer, matching how BasePlateMaterialLoader owns glass.
private const val CUBE_JELLY_OPACITY = 0.80f

// The Shader Graph input node name confirmed live on-device in Task 3 Step 1
// (see that step for how this was confirmed, not guessed).
private const val COLOR_TINT_PARAMETER_NAME = "color_tint"

private fun bundlePathFor(material: PieceMaterial): String = when (material) {
    PieceMaterial.JELLY -> error("JELLY has no AssetBundle path")
    PieceMaterial.WOOD_02 -> "BaseMaterials/Root/Wood_02/material/M_Wood_02"
    PieceMaterial.TILES_04 -> "BaseMaterials/Root/Tiles_04/material/M_Tiles_04"
    PieceMaterial.WOOD_12 -> "BaseMaterials/Root/Wood_12/material/M_Wood_12"
    PieceMaterial.TRAVERTINE_09 -> "BaseMaterials/Root/Travertine_09/material/M_Travertine_09"
}

private fun Color4.toColor3(): Color3 = Color3(red, green, blue)

/**
 * Resolves a [PieceMaterial] selection to one [Material] per [PieceType],
 * via the [bundle] shared with [BasePlateMaterialLoader].
 *
 * JELLY reproduces `BoardCubeRenderer.createCube()`'s existing jelly-look
 * construction exactly (fresh [UnlitMaterial] per type, not per cube - see
 * [createJellyMaterials]) rather than routing through the PBR machinery
 * below, so the already-shipped default look can never regress by sharing
 * code with a newer, less-tested path.
 *
 * The 4 PBR options each load one [ShaderGraphMaterial] per [PieceType]
 * from the same bundle path (`ShaderGraphMaterial` doesn't support deep
 * copy per SDK docs, so this is 7 real loads, not 7 copies of one
 * instance), tinted via [COLOR_TINT_PARAMETER_NAME] with that type's
 * [candyColorFor] color so switching materials doesn't erase the 7-color
 * identification the player relies on. If ANY of the 7 per-type loads or
 * tint calls fails, the WHOLE selection falls back to the JELLY map - never
 * a partial result mixing old and new materials across piece types.
 *
 * The 7 loads really are 7 independent instances, verified on-device
 * (2026-08-26): the same bundle path loaded twice produced two wrappers whose
 * `color_tint` values were set and read back independently (red / blue), and
 * two simultaneously-visible cubes rendered in those two different colors.
 * `AssetBundle.releaseResource(path)` being path-keyed does NOT imply a
 * path->instance cache on the load side. Without this the whole per-type tint
 * scheme would be meaningless - every piece type would render in whichever
 * color was written last.
 *
 * KNOWN GAP - these materials are never released. [BasePlateMaterialLoader]
 * gets its cleanup for free: `setBasePlateMaterial` destroys and recreates the
 * ground entity, and destroying an entity releases the resources it held. The
 * piece path is the exact opposite by design - `BoardCubeRenderer` swaps
 * `ModelComponent.materials[0]` on cube entities that are pooled at
 * `attachTo()` time and never destroyed - so nothing ever closes the 7
 * [ShaderGraphMaterial] instances a PBR selection creates. Browsing all 4 PBR
 * swatches in one session therefore orphans 28 material handles, bounded only
 * by the app process ending (`BaseMaterialsBundle.close()` on dispose drops the
 * bundle's strong references, but per `AssetBundle.close()`'s own docs that
 * deliberately does not invalidate resources still in use).
 *
 * Closing the outgoing set when a new selection replaces it is NOT safe as the
 * renderer stands today, which is why it isn't done: `render()` only rebinds
 * cubes that are currently *enabled*, so every disabled (empty-cell) cube keeps
 * the previous selection's material in its slot 0 until the cell next fills. A
 * `close()` after a PBR->PBR swap would leave those pooled entities holding an
 * invalidated handle, and `render()` sets `entity.enabled = true` *before*
 * calling `bindCubeMaterial`. Fixing this properly means giving
 * `setPieceMaterials` a rebind-every-pooled-entity pass for the PBR->PBR case
 * too (it already has one for PBR->null), and only then closing the old set -
 * a renderer change, not a loader change.
 */
internal class PieceMaterialLoader(private val bundle: BaseMaterialsBundle) {
    suspend fun load(selection: PieceMaterial): Map<PieceType, Material> = withContext(Dispatchers.IO) {
        if (selection == PieceMaterial.JELLY) return@withContext createJellyMaterials()

        bundle.withBundle { loadedBundle ->
            if (loadedBundle == null) return@withBundle createJellyMaterials()

            val path = bundlePathFor(selection)
            val results = PieceType.entries.associateWith { type ->
                runCatching {
                    ShaderGraphMaterial.loadFromAssetBundle(loadedBundle, path).apply {
                        setParameter(COLOR_TINT_PARAMETER_NAME, candyColorFor(type).toColor3())
                    }
                }
            }

            val firstFailure = results.values.firstOrNull { it.isFailure }?.exceptionOrNull()
            if (firstFailure != null) {
                Log.w(PIECE_MATERIAL_LOADER_LOG_TAG, "failed to load piece material for $selection, falling back to jelly", firstFailure)
                createJellyMaterials()
            } else {
                results.mapValues { (_, result) -> result.getOrThrow() }
            }
        }
    }

    private fun createJellyMaterials(): Map<PieceType, Material> =
        PieceType.entries.associateWith { type ->
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(candyJellyColorFor(type))
                setOpacity(CUBE_JELLY_OPACITY)
            }
        }
}
