package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class FallingPieceTest {
    @Test
    fun `spawn centers the piece horizontally on the board`() {
        val piece = FallingPiece.spawn(PieceType.T, boardWidth = 8)
        assertEquals(2, piece.anchorCol)
        assertEquals(0, piece.anchorRow)
    }

    @Test
    fun `movedBy shifts every absolute cell by the same delta`() {
        val piece = FallingPiece.spawn(PieceType.O, boardWidth = 8).movedBy(deltaRow = 1, deltaCol = 1)
        assertEquals(setOf(1 to 4, 1 to 5, 2 to 4, 2 to 5), piece.absoluteCells().toSet())
    }

    @Test
    fun `rotatedClockwise applies the box rotation to local cells only`() {
        val piece = FallingPiece.spawn(PieceType.I, boardWidth = 8).rotatedClockwise()
        assertEquals(setOf(0 to 2, 1 to 2, 2 to 2, 3 to 2), piece.localCells.toSet())
    }

    @Test
    fun `rotatedCounterClockwise undoes rotatedClockwise`() {
        val piece = FallingPiece.spawn(PieceType.I, boardWidth = 8)
        val roundTrip = piece.rotatedClockwise().rotatedCounterClockwise()
        assertEquals(piece.localCells.toSet(), roundTrip.localCells.toSet())
    }
}
