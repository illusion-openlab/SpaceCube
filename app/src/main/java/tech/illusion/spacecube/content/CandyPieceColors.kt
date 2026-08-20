package tech.illusion.spacecube.content

import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.math.Color4
import tech.illusion.spacecube.game.PieceType

// Single source of truth for the 7 candy piece colors, consumed both by the 3D
// cube renderer (as Color4) and by the 2D next-piece preview (as a Compose
// Color). Defined once as RGB hex so the preview can never drift out of sync
// with the actual blocks on the board - previously only the 3D side existed.
private fun candyPieceHex(type: PieceType): Int = when (type) {
    PieceType.I -> 0x52D1E8 // design-style: fixed-figma-color spacecube candy palette, I piece
    PieceType.O -> 0xFFD24C // design-style: fixed-figma-color spacecube candy palette, O piece
    PieceType.T -> 0xFF6FA0 // design-style: fixed-figma-color spacecube candy palette, T piece
    PieceType.S -> 0x57E0A0 // design-style: fixed-figma-color spacecube candy palette, S piece
    PieceType.Z -> 0xFF5D5D // design-style: fixed-figma-color spacecube candy palette, Z piece
    PieceType.L -> 0xFFA23C // design-style: fixed-figma-color spacecube candy palette, L piece
    PieceType.J -> 0x8B93F8 // design-style: fixed-figma-color spacecube candy palette, J piece
}

/** The 3D cube color for a piece type. */
internal fun candyColorFor(type: PieceType): Color4 {
    val hex = candyPieceHex(type)
    return Color4(
        ((hex shr 16) and 0xFF) / 255f,
        ((hex shr 8) and 0xFF) / 255f,
        (hex and 0xFF) / 255f,
        1f,
    )
}

// How far the jelly variant mixes toward white. UnlitMaterial has no roughness /
// scattering parameter (that's PBR-only), so "磨砂" can only be *suggested*: mixing
// toward white gives the milky, light-diffusing quality frosted material has,
// instead of the deep saturated look a clear translucent block would have.
private const val JELLY_WHITE_MIX = 0.16f

/**
 * The frosted-jelly variant of a piece color, used for the translucent cubes.
 * Slightly milkier than [candyColorFor] so a semi-transparent cube still reads
 * as frosted candy rather than as a dark tinted pane.
 */
internal fun candyJellyColorFor(type: PieceType): Color4 {
    val solid = candyColorFor(type)
    fun milky(channel: Float) = channel + (1f - channel) * JELLY_WHITE_MIX
    return Color4(milky(solid.red), milky(solid.green), milky(solid.blue), 1f)
}

/** The same piece color, for 2D Compose UI (next-piece preview). */
internal fun candyPieceComposeColor(type: PieceType): Color =
    Color(0xFF000000.toInt() or candyPieceHex(type))