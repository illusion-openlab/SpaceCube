package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class PieceTypeTest {
    @Test
    fun `rotating the O piece four times returns to the original cell set`() {
        var cells = PieceType.O.spawnCells
        repeat(4) { cells = rotateCellsClockwise(cells, PieceType.O.boxSize) }
        assertEquals(PieceType.O.spawnCells.toSet(), cells.toSet())
    }

    @Test
    fun `rotating the I piece once turns it vertical`() {
        val rotated = rotateCellsClockwise(PieceType.I.spawnCells, PieceType.I.boxSize)
        assertEquals(setOf(0 to 2, 1 to 2, 2 to 2, 3 to 2), rotated.toSet())
    }

    @Test
    fun `every piece returns to its original cell set after four rotations`() {
        for (type in PieceType.values()) {
            var cells = type.spawnCells
            repeat(4) { cells = rotateCellsClockwise(cells, type.boxSize) }
            assertEquals("failed for $type", type.spawnCells.toSet(), cells.toSet())
        }
    }

    @Test
    fun `rotating counter-clockwise is the exact inverse of rotating clockwise`() {
        for (type in PieceType.values()) {
            val clockwise = rotateCellsClockwise(type.spawnCells, type.boxSize)
            val roundTrip = rotateCellsCounterClockwise(clockwise, type.boxSize)
            assertEquals("failed for $type", type.spawnCells.toSet(), roundTrip.toSet())
        }
    }

    @Test
    fun `every piece returns to its original cell set after four counter-clockwise rotations`() {
        for (type in PieceType.values()) {
            var cells = type.spawnCells
            repeat(4) { cells = rotateCellsCounterClockwise(cells, type.boxSize) }
            assertEquals("failed for $type", type.spawnCells.toSet(), cells.toSet())
        }
    }
}
