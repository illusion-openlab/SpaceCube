package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {
    @Test
    fun `clearing more lines at once scores more points per line`() {
        assertEquals(100, Scoring.pointsForClearedLines(1, level = 1))
        assertEquals(300, Scoring.pointsForClearedLines(2, level = 1))
        assertEquals(500, Scoring.pointsForClearedLines(3, level = 1))
        assertEquals(800, Scoring.pointsForClearedLines(4, level = 1))
    }

    @Test
    fun `points scale with the current level`() {
        assertEquals(200, Scoring.pointsForClearedLines(1, level = 2))
    }

    @Test
    fun `level increases every ten cleared lines`() {
        assertEquals(1, Scoring.levelForLinesCleared(0))
        assertEquals(1, Scoring.levelForLinesCleared(9))
        assertEquals(2, Scoring.levelForLinesCleared(10))
        assertEquals(3, Scoring.levelForLinesCleared(25))
    }

    @Test
    fun `fall interval speeds up with level but never drops below the floor`() {
        assertEquals(700L, Scoring.fallIntervalMs(Difficulty.NORMAL, level = 1))
        assertEquals(640L, Scoring.fallIntervalMs(Difficulty.NORMAL, level = 2))
        assertEquals(120L, Scoring.fallIntervalMs(Difficulty.NORMAL, level = 20))
    }
}
