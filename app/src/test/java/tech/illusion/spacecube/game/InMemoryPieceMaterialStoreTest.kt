package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryPieceMaterialStoreTest {
    @Test
    fun `defaults to jelly and remembers the last value set`() {
        val store = InMemoryPieceMaterialStore()
        assertEquals(PieceMaterial.JELLY, store.get())

        store.set(PieceMaterial.WOOD_02)
        assertEquals(PieceMaterial.WOOD_02, store.get())

        store.set(PieceMaterial.TRAVERTINE_09)
        assertEquals(PieceMaterial.TRAVERTINE_09, store.get())
    }
}
