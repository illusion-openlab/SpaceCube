package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {
    @Test
    fun `a newly created board has no full rows`() {
        val board = Board(width = 4, height = 4)
        assertEquals(emptyList<Int>(), board.fullRows())
    }

    @Test
    fun `a row filled across its full width is detected as full`() {
        val board = Board(width = 4, height = 4)
        board.lock(listOf(3 to 0, 3 to 1, 3 to 2, 3 to 3), PieceType.O)
        assertEquals(listOf(3), board.fullRows())
    }

    @Test
    fun `clearing a row shifts every row above it down by one`() {
        val board = Board(width = 4, height = 4)
        board.lock(listOf(2 to 0), PieceType.T)
        board.lock(listOf(3 to 0, 3 to 1, 3 to 2, 3 to 3), PieceType.O)
        board.clearRows(listOf(3))
        assertEquals(PieceType.T, board.cellAt(3, 0))
        assertEquals(null, board.cellAt(2, 0))
    }

    @Test
    fun `canPlace rejects cells outside bounds or already occupied`() {
        val board = Board(width = 4, height = 4)
        board.lock(listOf(0 to 0), PieceType.I)
        assertFalse(board.canPlace(listOf(0 to 0)))
        assertFalse(board.canPlace(listOf(0 to 4)))
        assertFalse(board.canPlace(listOf(-1 to 0)))
        assertTrue(board.canPlace(listOf(0 to 1)))
    }

    @Test
    fun `clearAll empties every cell`() {
        val board = Board(width = 4, height = 4)
        board.lock(listOf(0 to 0, 1 to 1), PieceType.S)
        board.clearAll()
        assertEquals(emptyList<Int>(), board.fullRows())
        assertEquals(null, board.cellAt(0, 0))
    }

    @Test
    fun `rows returns an immutable snapshot with the board dimensions`() {
        val board = Board(width = 4, height = 3)
        board.lock(listOf(1 to 2), PieceType.Z)
        val snapshot = board.rows()
        assertEquals(3, snapshot.size)
        assertEquals(4, snapshot[1].size)
        assertEquals(PieceType.Z, snapshot[1][2])
    }
}
