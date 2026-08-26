package tech.illusion.spacecube.game

import android.content.Context

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

class SharedPreferencesBasePlateMaterialStore(context: Context) : BasePlateMaterialStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(): BasePlateMaterial {
        val stored = prefs.getString(KEY_SELECTED_MATERIAL, null) ?: return BasePlateMaterial.GLASS
        return runCatching { BasePlateMaterial.valueOf(stored) }.getOrDefault(BasePlateMaterial.GLASS)
    }

    override fun set(material: BasePlateMaterial) {
        prefs.edit().putString(KEY_SELECTED_MATERIAL, material.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "spacecube_base_plate_material"
        private const val KEY_SELECTED_MATERIAL = "selected_material"
    }
}
