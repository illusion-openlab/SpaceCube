package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryHighScoreStoreTest {
    @Test
    fun `recording a score updates last score and raises high score only on a new best`() {
        val store = InMemoryHighScoreStore()
        store.recordScore(120)
        assertEquals(120, store.lastScore())
        assertEquals(120, store.highScore())
        store.recordScore(80)
        assertEquals(80, store.lastScore())
        assertEquals(120, store.highScore())
        store.recordScore(200)
        assertEquals(200, store.lastScore())
        assertEquals(200, store.highScore())
    }
}
