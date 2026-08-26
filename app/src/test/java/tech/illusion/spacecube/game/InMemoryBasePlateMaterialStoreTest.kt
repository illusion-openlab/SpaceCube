package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryBasePlateMaterialStoreTest {
    @Test
    fun `defaults to glass and remembers the last value set`() {
        val store = InMemoryBasePlateMaterialStore()
        assertEquals(BasePlateMaterial.GLASS, store.get())

        store.set(BasePlateMaterial.WOOD_02)
        assertEquals(BasePlateMaterial.WOOD_02, store.get())

        store.set(BasePlateMaterial.TRAVERTINE_09)
        assertEquals(BasePlateMaterial.TRAVERTINE_09, store.get())
    }
}
