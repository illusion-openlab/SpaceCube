package tech.illusion.spacecube.game

interface BasePlateMaterialStore {
    fun get(): BasePlateMaterial
    fun set(material: BasePlateMaterial)
}

class InMemoryBasePlateMaterialStore : BasePlateMaterialStore {
    private var current: BasePlateMaterial = BasePlateMaterial.GLASS

    override fun get(): BasePlateMaterial = current
    override fun set(material: BasePlateMaterial) {
        current = material
    }
}
