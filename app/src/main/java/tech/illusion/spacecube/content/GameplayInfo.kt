package tech.illusion.spacecube.content

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

// "玩法" entry button on the config window's config_card (this file is used only by
// ConfigPage now - the in-Stage start screen it was written for is gone): its
// containerColor reuses the SAME role the
// "开始游戏" button uses once ready (candyButtonColors(primary = true) -> CandyAccentMint /
// CandyAccentMintInk), just diluted, so it reads as "same family, quieter" rather than a
// second primary action competing with the actual start button. contentColor (icon + label)
// stays at full CandyAccentMintInk opacity so it's still sharply legible against the diluted
// fill - only the container gets the alpha cut, per the confirmed visual spec.
internal const val GAMEPLAY_BUTTON_CONTAINER_ALPHA = 0.3f

// The circular "?" badge's fill: a bit more opaque than the button container so the dot
// itself still reads as a distinct shape against the diluted button background it sits on.
private const val GAMEPLAY_BADGE_FILL_ALPHA = 0.68f

// Scrim behind the gameplay-info card. PicoTheme.ColorScheme (SDK 6.0) has no literal
// surface/scrim field - fillPrimary ("background color for small areas / important
// elements", backed by ColorTokens.Default.FillDarkerAlpha - the darker of the two fill
// tokens) is used here over fillSecondary because a scrim's job is to visibly dim
// everything behind it, which the darker token serves better than the semi-light one, even
// though geometrically the scrim covers a large area (fillSecondary's literal use case).
// Needs a real on-device screenshot check per this project's own history of dark-overlay
// alpha values not being judgeable from code/WCAG numbers alone.
private const val GAMEPLAY_SCRIM_ALPHA = 0.7f

private val GAMEPLAY_OVERLAY_CARD_WIDTH = 480.dp

// Caps just the scrollable section column, not the title row above it, so "玩法说明" + the
// close button stay visible unscrolled while intro/controls/rules scroll underneath. Sized
// so padding(18dp*2) + title row(~40dp) + spacing(16dp) + this stays under the ~560dp overall
// card height the confirmed design suggested.
private val GAMEPLAY_OVERLAY_SCROLL_MAX_HEIGHT = 420.dp

// Gameplay-info overlay copy - user-finalized verbatim wording (2026-08-26 design gate),
// must not be rewritten/summarized/trimmed. Pulled out as constants so the composables below
// only ever reference one copy of each string.
private const val GAMEPLAY_INFO_INTRO_TEXT =
    "从空中的方块井顶端不断落下方块，你要用手势把它们移动、加速、旋转到合适的位置，拼满整行来消除得分。方块堆到井口顶端、新方块放不下的那一刻，这一局就结束——目标只有一个：把分数刷得尽量高。"
private val GAMEPLAY_INFO_CONTROLS_LINES = listOf(
    "移动方块：对着方块捏合手指左、右、下移动，方块就跟着你的手滑动；碰到墙壁或者已经堆好的方块会自动停住，不会硬挤过去。",
    "旋转方块：对着方块快速捏合手指两下，方块顺时针转90度；转不过去的时候方块原地不动，方向固定只有顺时针一种。",
)

// Added 2026-08-28 alongside ControllerMoveController - user request "在玩法中补充手柄控制规则".
// Always shown (not gated on whether a controller is currently paired), matching how
// GAMEPLAY_INFO_CONTROLS_LINES above documents hand gestures unconditionally too - this is
// reference documentation for an available control method, not a live status readout.
private val GAMEPLAY_INFO_CONTROLLER_LINES = listOf(
    "手柄移动：推动任意一支手柄的摇杆，左、右移动方块，推下方向加快下落；摇杆保持推着就会连续移动，回中后可以换方向。",
    "手柄旋转：把摇杆推向正上方，方块顺时针转90度；每转一次都要先把摇杆回中再推一次上，转不过去的时候方块原地不动。",
)

private val GAMEPLAY_INFO_RULES_LINES = listOf(
    "1. 填满一整行才会消除；一次锁定最多能同时清掉4行，被消掉的行整体消失，上面的方块整体下移补位。",
    "2. 暂停的时候，下落、移动、旋转全部冻结，恢复后从暂停那一刻接着玩，不会丢进度。",
)

/** The "玩法" button's leading icon: a small circle filled with the same mint accent role, "?" in its ink. */
@Composable
internal fun GameplayButtonBadge() {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(CandyAccentMint.copy(alpha = GAMEPLAY_BADGE_FILL_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "?",
            color = CandyAccentMintInk,
            style = PicoTheme.typography.labelSmall,
        )
    }
}

/** One "小标题 + 正文" block inside the gameplay-info card (简介 / 操作 / 规则). */
@Composable
private fun GameplayInfoSection(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = CandyAccentMintInk,
            style = PicoTheme.typography.titleSmall,
        )
        lines.forEach { line ->
            Text(
                text = line,
                color = CandyCardInkDim,
                style = PicoTheme.typography.bodyMediumMultiline,
            )
        }
    }
}

/**
 * Scrim + info card for the "玩法" entry button. A [BoxScope] extension so the scrim can use
 * [BoxScope.matchParentSize] to cover the whole config_card Box (CandyCard + gameplay_button
 * both included), whatever size that Box ends up being. That Box lives in `ConfigPage`'s
 * `config_card` AttachmentPanel - it used to be the Stage's `start_screen` panel, which no
 * longer exists.
 *
 * Reuses CandyCard - the same opaque cream-card system every other overlay in this file
 * (pause/game-over/exit-confirm) already uses - rather than introducing a new glass
 * background: `backgroundMaterial`/`Material.Regular` has zero uses anywhere in this project,
 * so CandyCard is the project's actual established convention for this kind of overlay panel.
 *
 * Both the scrim and the card consume their own tap gesture via `pointerInput` +
 * `detectTapGestures` (the same pattern already proven to compile/work in this file's sibling
 * composables, e.g. BasePlateMaterialPicker/PieceMaterialPicker): the scrim's tap closes the
 * overlay and dismissing that way must not also register as a tap on whatever sits under it
 * (namely 开始游戏), and the card's own tap is a no-op that exists purely so a tap on the
 * card's background doesn't fall through to the scrim underneath and close the overlay out
 * from under the user while they're mid-read.
 */
@Composable
internal fun BoxScope.GameplayInfoOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(PicoTheme.colorScheme.fillPrimary.copy(alpha = GAMEPLAY_SCRIM_ALPHA))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {}
    CandyCard(
        modifier = Modifier
            .align(Alignment.Center)
            .width(GAMEPLAY_OVERLAY_CARD_WIDTH)
            .pointerInput(Unit) { detectTapGestures(onTap = {}) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "玩法说明",
                    color = CandyCardInk,
                    style = PicoTheme.typography.titleMedium,
                )
                Button(
                    onClick = onDismiss,
                    size = ButtonDefaults.Small,
                    colors = candyButtonColors(primary = false),
                ) { Text("×") }
            }
            Column(
                modifier = Modifier
                    .heightIn(max = GAMEPLAY_OVERLAY_SCROLL_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GameplayInfoSection(title = "简介", lines = listOf(GAMEPLAY_INFO_INTRO_TEXT))
                GameplayInfoSection(title = "操作", lines = GAMEPLAY_INFO_CONTROLS_LINES)
                GameplayInfoSection(title = "手柄操作", lines = GAMEPLAY_INFO_CONTROLLER_LINES)
                GameplayInfoSection(title = "规则", lines = GAMEPLAY_INFO_RULES_LINES)
            }
        }
    }
}
