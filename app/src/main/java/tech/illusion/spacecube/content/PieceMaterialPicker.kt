package tech.illusion.spacecube.content

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import tech.illusion.spacecube.game.PieceMaterial
import tech.illusion.spacecube.game.PieceType

private val SWATCH_SIZE = 44.dp
private val SWATCH_CORNER = 10.dp
private val SWATCH_GAP = 10.dp
private val SWATCH_LABEL_GAP = 4.dp
private val SWATCH_RING_WIDTH = 2.dp
private val JELLY_SWATCH_CELL_GAP = 2.dp

// JELLY has no single-image thumbnail (it's the app's own per-piece UnlitMaterial
// look, not a Spatial Editor asset, and unlike glass it's meant to read as
// "still multicolor" rather than one flat tone) - shown as a small 2x2 grid of
// 4 representative candy colors, echoing NextPiecePreview's mini-grid language
// rather than inventing a new visual idiom.
private val JELLY_SWATCH_COLORS = listOf(
    candyPieceComposeColor(PieceType.I),
    candyPieceComposeColor(PieceType.O),
    candyPieceComposeColor(PieceType.T),
    candyPieceComposeColor(PieceType.S),
)

private val PIECE_THUMBNAIL_ASSET_PATHS: Map<PieceMaterial, String> = mapOf(
    PieceMaterial.WOOD_02 to "base_plate_thumbnails/wood_02.png",
    PieceMaterial.TILES_04 to "base_plate_thumbnails/tiles_04.png",
    PieceMaterial.WOOD_12 to "base_plate_thumbnails/wood_12.png",
    PieceMaterial.TRAVERTINE_09 to "base_plate_thumbnails/travertine_09.png",
)

private fun loadPieceThumbnails(context: Context): Map<PieceMaterial, ImageBitmap> =
    PIECE_THUMBNAIL_ASSET_PATHS.mapValues { (_, path) ->
        context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream).asImageBitmap() }
    }

private fun pieceMaterialLabel(material: PieceMaterial): String = when (material) {
    PieceMaterial.JELLY -> "果冻"
    PieceMaterial.WOOD_02 -> "Wood_02"
    PieceMaterial.TILES_04 -> "Tiles_04"
    PieceMaterial.WOOD_12 -> "Wood_12"
    PieceMaterial.TRAVERTINE_09 -> "Travertine_09"
}

/**
 * The "外观设置" panel row for picking the falling/locked pieces' visual
 * material - 果冻 (the existing look) plus the same 4 PBR materials shown
 * by [BasePlateMaterialPicker], reusing their thumbnails. Selecting one
 * only updates [onSelect]'s state and (via the caller) persists it; it does
 * not itself touch any 3D entity - see `BoardCubeRenderer.setPieceMaterials`
 * for where the actual swap happens.
 */
@Composable
internal fun PieceMaterialPicker(
    selected: PieceMaterial,
    onSelect: (PieceMaterial) -> Unit,
) {
    val context = LocalContext.current
    val thumbnails = remember { loadPieceThumbnails(context) }

    Column {
        Text(
            text = "方块材质",
            color = CandyCardInkDim,
            style = PicoTheme.typography.labelSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP)) {
            PieceMaterial.entries.forEach { material ->
                PieceMaterialSwatch(
                    material = material,
                    thumbnail = thumbnails[material],
                    isSelected = material == selected,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun PieceMaterialSwatch(
    material: PieceMaterial,
    thumbnail: ImageBitmap?,
    isSelected: Boolean,
    onSelect: (PieceMaterial) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SWATCH_LABEL_GAP),
    ) {
        val ringModifier = if (isSelected) {
            Modifier.border(SWATCH_RING_WIDTH, CandyAccentMint, RoundedCornerShape(SWATCH_CORNER))
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .size(SWATCH_SIZE)
                .clip(RoundedCornerShape(SWATCH_CORNER))
                .spatialHoverEffect()
                .pointerInput(material) {
                    detectTapGestures(onTap = { onSelect(material) })
                }
                .then(ringModifier),
        ) {
            if (thumbnail != null) {
                Canvas(modifier = Modifier.size(SWATCH_SIZE)) {
                    drawImage(thumbnail, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(JELLY_SWATCH_CELL_GAP)) {
                    JELLY_SWATCH_COLORS.chunked(2).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(JELLY_SWATCH_CELL_GAP)) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size((SWATCH_SIZE - JELLY_SWATCH_CELL_GAP) / 2)
                                        .background(color),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = pieceMaterialLabel(material),
            color = if (isSelected) CandyCardInk else CandyCardInkDim,
            style = PicoTheme.typography.bodySmall,
        )
    }
}
