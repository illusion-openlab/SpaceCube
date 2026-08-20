package tech.illusion.spacecube.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.ButtonColors
import com.pico.spatial.ui.design.ButtonDefaults

// A small candy-cream card system for GamePage's AttachmentPanel content, matching
// the game's own candy piece palette rather than PicoTheme's dark-glass roles -
// these panels sit on opaque cream cards, not the transparent volumetric glass.
internal val CandyCardBackground = Color(0xFFFFF3E0) // design-style: fixed-figma-color spacecube-panel-redesign mockup, cream card
internal val CandyCardInk = Color(0xFF4A2E2A) // design-style: fixed-figma-color spacecube-panel-redesign mockup, card ink
internal val CandyCardInkDim = Color(0xFF9C7A6E) // design-style: fixed-figma-color spacecube-panel-redesign mockup, card ink dim
internal val CandyAccentMint = Color(0xFF57E0A0) // design-style: fixed-figma-color spacecube-panel-redesign mockup, mint accent (matches S piece)
internal val CandyAccentMintInk = Color(0xFF0A3323) // design-style: fixed-figma-color spacecube-panel-redesign mockup, mint accent ink
internal val CandyNeutralFill = Color(0x144A2E2A) // design-style: fixed-figma-color spacecube-panel-redesign mockup, neutral pill fill

@Composable
internal fun CandyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(CandyCardBackground, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .padding(18.dp),
        content = { content() },
    )
}

/**
 * Card for the in-game status readouts (SCORE / NEXT), styled to match the pause
 * button sitting alongside them rather than the opaque cream [CandyCard] used by
 * the big menu overlays (user request 2026-08-07: "下边几个按钮状态的容器背景色应该和暂停
 * 按钮的容器背景色保持一致").
 *
 * The fill is deliberately the SAME [CandyNeutralFill] that `candyButtonColors`
 * gives a non-primary button, so the three bottom panels read as one set. That fill
 * is only ~8% opaque, so these read far lighter than the menu cards do - if the
 * numbers turn out hard to read against passthrough, the fix is to change this one
 * background (or to move the pause button onto the cream card instead, flipping
 * which side of the pair conforms).
 */
@Composable
internal fun CandyStatusCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(CandyNeutralFill, RoundedCornerShape(20.dp))
            .padding(18.dp),
        content = { content() },
    )
}

@Composable
internal fun candyButtonColors(primary: Boolean): ButtonColors = if (primary) {
    ButtonDefaults.buttonColors(containerColor = CandyAccentMint, contentColor = CandyAccentMintInk)
} else {
    ButtonDefaults.buttonColors(containerColor = CandyNeutralFill, contentColor = CandyCardInkDim)
}
