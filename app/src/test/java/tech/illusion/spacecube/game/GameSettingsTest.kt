package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameSettingsTest {
    @Test
    fun `every difficulty round-trips through its own name`() {
        for (difficulty in Difficulty.values()) {
            assertEquals("round trip for $difficulty", difficulty, parseDifficulty(difficulty.name))
        }
    }

    @Test
    fun `a missing name falls back to normal`() {
        assertEquals(Difficulty.NORMAL, parseDifficulty(null))
    }

    @Test
    fun `an unrecognised name falls back to normal`() {
        assertEquals(Difficulty.NORMAL, parseDifficulty("TURBO"))
    }

    @Test
    fun `an empty name falls back to normal`() {
        assertEquals(Difficulty.NORMAL, parseDifficulty(""))
    }
}
