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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import tech.illusion.spacecube.game.BasePlateMaterial

private val SWATCH_SIZE = 44.dp
private val SWATCH_CORNER = 10.dp
private val SWATCH_GAP = 10.dp
private val SWATCH_LABEL_GAP = 4.dp
private val SWATCH_RING_WIDTH = 2.dp

// The glass swatch has no real texture (it's the app's own UnlitMaterial look,
// not a Spatial Editor asset) - approximated as a light translucent gradient,
// matching the picker layout mockup the user approved (see
// docs/superpowers/specs/2026-08-26-base-plate-material-picker-design.md,
// "Glass renders as a CSS-equivalent translucent gradient swatch").
private val GLASS_SWATCH_GRADIENT = Brush.linearGradient(
    listOf(
        Color(0xFFC9E2F5), // design-style: fixed-figma-color base-plate-material-picker design, glass swatch gradient start
        Color(0xFFEEF6FB), // design-style: fixed-figma-color base-plate-material-picker design, glass swatch gradient end
    )
)

private val BASE_PLATE_THUMBNAIL_ASSET_PATHS: Map<BasePlateMaterial, String> = mapOf(
    BasePlateMaterial.WOOD_02 to "base_plate_thumbnails/wood_02.png",
    BasePlateMaterial.TILES_04 to "base_plate_thumbnails/tiles_04.png",
    BasePlateMaterial.WOOD_12 to "base_plate_thumbnails/wood_12.png",
    BasePlateMaterial.TRAVERTINE_09 to "base_plate_thumbnails/travertine_09.png",
)

private fun loadBasePlateThumbnails(context: Context): Map<BasePlateMaterial, ImageBitmap> =
    BASE_PLATE_THUMBNAIL_ASSET_PATHS.mapValues { (_, path) ->
        context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream).asImageBitmap() }
    }

private fun basePlateMaterialLabel(material: BasePlateMaterial): String = when (material) {
    BasePlateMaterial.GLASS -> "玻璃"
    BasePlateMaterial.WOOD_02 -> "Wood_02"
    BasePlateMaterial.TILES_04 -> "Tiles_04"
    BasePlateMaterial.WOOD_12 -> "Wood_12"
    BasePlateMaterial.TRAVERTINE_09 -> "Travertine_09"
}

/**
 * The start-screen row for picking the base plate's visual material - 玻璃
 * (the existing look) plus 4 Spatial Editor PBR materials, each shown as a
 * small thumbnail swatch. Selecting one only updates [onSelect]'s state and
 * (via the caller) persists it; it does not itself touch any 3D entity.
 *
 * No built-in SpatialUI selection component (`Option`, `ToggleableChip`) fits
 * this swatch grid: both render a fixed-height horizontal icon+label pill
 * (`Option` is spec'd at a 48dp-tall row - see spatial-ui-design-style's
 * builtins reference), not the vertical thumbnail-over-label tile the
 * approved mockup calls for. This stays a custom component per that skill's
 * own "only customize when no built-in fits" rule, but picks up its
 * highest-priority custom-tap requirement: `Modifier.spatialHoverEffect` on
 * the tappable swatch (never a hand-rolled `hoverable` + scale animation).
 * Tap detection stays on `pointerInput` + `detectTapGestures` rather than
 * `Modifier.clickable` - the latter drags in a same-file `controllerHapticFeedback`
 * requirement whose import path is unconfirmed in this project, while
 * `pointerInput` is already proven to compile here (see `V2ControlSurface.kt`).
 */
@Composable
internal fun BasePlateMaterialPicker(
    selected: BasePlateMaterial,
    onSelect: (BasePlateMaterial) -> Unit,
) {
    val context = LocalContext.current
    val thumbnails = remember { loadBasePlateThumbnails(context) }

    Column {
        Text(
            text = "底座材质",
            color = CandyCardInkDim,
            style = PicoTheme.typography.labelSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP)) {
            BasePlateMaterial.entries.forEach { material ->
                BasePlateMaterialSwatch(
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
private fun BasePlateMaterialSwatch(
    material: BasePlateMaterial,
    thumbnail: ImageBitmap?,
    isSelected: Boolean,
    onSelect: (BasePlateMaterial) -> Unit,
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
            // Chain order per spatial-ui-design-style/references/custom-component.md
            // §2: outer layout/size -> shape -> hover -> interaction -> decoration.
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
                Box(modifier = Modifier.size(SWATCH_SIZE).background(GLASS_SWATCH_GRADIENT))
            }
        }
        Text(
            text = basePlateMaterialLabel(material),
            color = if (isSelected) CandyCardInk else CandyCardInkDim,
            style = PicoTheme.typography.bodySmall,
        )
    }
}
