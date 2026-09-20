package tech.illusion.spacecube.content

import android.util.Log
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.ShaderGraphMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
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

private fun bundlePathFor(material: PieceMaterial): String = when (material) {
    PieceMaterial.JELLY -> error("JELLY has no AssetBundle path")
    PieceMaterial.WOOD_02 -> "BaseMaterials/Root/Wood_02/material/M_Wood_02"
    PieceMaterial.TILES_04 -> "BaseMaterials/Root/Tiles_04/material/M_Tiles_04"
    PieceMaterial.WOOD_12 -> "BaseMaterials/Root/Wood_12/material/M_Wood_12"
    PieceMaterial.TRAVERTINE_09 -> "BaseMaterials/Root/Travertine_09/material/M_Travertine_09"
}

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
 * The 4 PBR options load ONE [ShaderGraphMaterial] per selection and reuse
 * that same instance as the value for all 7 [PieceType] keys in the
 * returned map - every piece type renders identically under a PBR
 * material (its natural, untinted look). This is deliberate (2026-08-26):
 * an earlier version of this loader tinted each of 7 separately-loaded
 * per-type instances via a `color_tint` Shader Graph parameter so piece
 * types stayed color-distinct under any material, but real-headset testing
 * found the forced tint on top of a wood/tile/stone texture looked bad, so
 * per-type tinting was removed. Piece-type identification under a PBR
 * material now relies on shape and the always-candy-colored "next piece"
 * preview panel ([NextPiecePreview]), not color on the pieces themselves.
 *
 * Historical note, kept so this isn't rediscovered by accident: whether 7
 * independently-loaded instances from the same bundle path are genuinely
 * independent (as opposed to sharing one cached native resource) WAS a
 * real, verified question when per-type tinting existed - confirmed
 * independent via two separate on-device probes (2026-08-26). The detailed
 * evidence (the actual readback values, the screenshot) lives in this
 * project's git history around commit 2e6d715, not in this KDoc anymore -
 * the design spec at
 * `docs/superpowers/specs/2026-08-26-piece-material-picker-design.md`
 * predates that verification and only poses it as an open question, so
 * don't follow that pointer expecting the confirmation itself. That
 * question is moot now that every key deliberately shares one instance,
 * but if per-type visual distinction is ever reintroduced, re-read that git
 * history before assuming a shared instance can be tinted per-key - it
 * cannot: `setParameter` on a shared [ShaderGraphMaterial] changes every
 * entity that references it, since they all reference the same object.
 *
 * If the load fails, the WHOLE selection falls back to the JELLY map.
 *
 * KNOWN GAP - the loaded material is never released. [BasePlateMaterialLoader]
 * gets its cleanup for free: `setBasePlateMaterial` destroys and recreates the
 * ground entity, and destroying an entity releases the resources it held. The
 * piece path is the exact opposite by design - `BoardCubeRenderer` swaps
 * `ModelComponent.materials[0]` on cube entities that are pooled at
 * `attachTo()` time and never destroyed - so nothing ever closes the
 * [ShaderGraphMaterial] instance a PBR selection creates. Browsing all 4 PBR
 * swatches in one session therefore orphans 4 material handles (one per
 * selection - down from 7 per selection before per-type tinting was
 * removed, since every piece type now shares a single loaded instance),
 * bounded only by the app process ending.
 *
 * Correction (2026-09-20): an earlier version of this paragraph claimed
 * `BaseMaterialsBundle.close()` on dispose "drops the bundle's strong
 * references, but per `AssetBundle.close()`'s own docs that deliberately does
 * not invalidate resources still in use". The SDK docs say no such thing - what
 * they actually say (`AssetBundle` resource management, "Release AssetBundle
 * instances") is that `close()` releases "the `AssetBundle` instance and all
 * cached data of resources managed by it", and that it must be called only once
 * every `Entity.loadSuspend()` against it has completed. So `close()` is not a
 * safe way to reclaim an orphaned material while anything is still using the
 * bundle; it is the opposite. That is precisely why `BaseMaterialsBundle` is
 * reference-counted (see its KDoc) and why the orphaned handles above really do
 * live until the process ends rather than being quietly reclaimed at dispose.
 *
 * Closing the outgoing material when a new selection replaces it is NOT safe
 * as the renderer stands today, which is why it isn't done: `render()` only
 * rebinds cubes that are currently *enabled*, so every disabled (empty-cell)
 * cube keeps the previous selection's material in its slot 0 until the cell
 * next fills. A `close()` after a PBR->PBR swap would leave those pooled
 * entities holding an invalidated handle, and `render()` sets
 * `entity.enabled = true` *before* calling `bindCubeMaterial`. Fixing this
 * properly means giving `setPieceMaterials` a rebind-every-pooled-entity pass
 * for the PBR->PBR case too (it already has one for PBR->null), and only then
 * closing the old material - a renderer change, not a loader change.
 */
internal class PieceMaterialLoader(private val bundle: BaseMaterialsBundle) {
    suspend fun load(selection: PieceMaterial): Map<PieceType, Material> = withContext(Dispatchers.IO) {
        if (selection == PieceMaterial.JELLY) return@withContext createJellyMaterials()

        bundle.withBundle { loadedBundle ->
            if (loadedBundle == null) return@withBundle createJellyMaterials()

            val path = bundlePathFor(selection)
            runCatching { ShaderGraphMaterial.loadFromAssetBundle(loadedBundle, path) }
                .fold(
                    onSuccess = { material -> PieceType.entries.associateWith { material } },
                    onFailure = { error ->
                        Log.w(
                            PIECE_MATERIAL_LOADER_LOG_TAG,
                            "failed to load piece material for $selection, falling back to jelly",
                            error,
                        )
                        createJellyMaterials()
                    },
                )
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
