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
