package tech.illusion.spacecube.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class PieceBagTest {
    @Test
    fun `each bag of seven draws contains every piece type exactly once`() {
        val bag = PieceBag(Random(42))
        val drawn = List(7) { bag.next() }
        assertEquals(PieceType.values().toSet(), drawn.toSet())
        assertEquals(7, drawn.size)
    }

    @Test
    fun `drawing fourteen pieces produces two complete bags`() {
        val bag = PieceBag(Random(7))
        val drawn = List(14) { bag.next() }
        assertEquals(PieceType.values().toSet(), drawn.subList(0, 7).toSet())
        assertEquals(PieceType.values().toSet(), drawn.subList(7, 14).toSet())
    }
}
