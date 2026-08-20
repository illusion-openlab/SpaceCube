package tech.illusion.spacecube.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.illusion.spacecube.game.PieceType

private val PREVIEW_CELL_SIZE = 16.dp
private val PREVIEW_CELL_GAP = 3.dp
private val PREVIEW_CELL_CORNER = 4.dp

// The frame is fixed at the largest bounding box any piece needs (I is 4 wide x
// 1 tall; every other piece is at most 3 wide x 2 tall), with the piece centered
// inside it. Sizing the frame to the *current* piece instead would make the NEXT
// card visibly resize every time the upcoming piece changed.
private const val PREVIEW_FRAME_COLS = 4
private const val PREVIEW_FRAME_ROWS = 2

/**
 * Renders the upcoming piece as a mini grid of candy-colored rounded squares -
 * matching the design mockup and the actual 3D blocks (same palette, see
 * [candyPieceComposeColor]) - rather than the piece's enum name as text.
 */
@Composable
internal fun NextPiecePreview(type: PieceType) {
    val cells = type.spawnCells
    // Trim to the piece's occupied bounding box so it reads as the shape itself,
    // not as a shape floating inside its (mostly empty) rotation box - the I
    // piece's spawn box is 4x4 but only one row of it is filled.
    val minRow = cells.minOf { it.first }
    val maxRow = cells.maxOf { it.first }
    val minCol = cells.minOf { it.second }
    val maxCol = cells.maxOf { it.second }
    val color = candyPieceComposeColor(type)

    Box(
        modifier = Modifier
            .width(PREVIEW_CELL_SIZE * PREVIEW_FRAME_COLS + PREVIEW_CELL_GAP * (PREVIEW_FRAME_COLS - 1))
            .height(PREVIEW_CELL_SIZE * PREVIEW_FRAME_ROWS + PREVIEW_CELL_GAP * (PREVIEW_FRAME_ROWS - 1)),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PREVIEW_CELL_GAP)) {
            for (row in minRow..maxRow) {
                Row(horizontalArrangement = Arrangement.spacedBy(PREVIEW_CELL_GAP)) {
                    for (col in minCol..maxCol) {
                        val filled = (row to col) in cells
                        Box(
                            modifier = Modifier
                                .size(PREVIEW_CELL_SIZE)
                                .then(
                                    if (filled) {
                                        Modifier.background(color, RoundedCornerShape(PREVIEW_CELL_CORNER))
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
