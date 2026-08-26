package tech.illusion.spacecube.game

import android.content.Context

interface PieceMaterialStore {
    fun get(): PieceMaterial
    fun set(material: PieceMaterial)
}

class InMemoryPieceMaterialStore : PieceMaterialStore {
    private var current: PieceMaterial = PieceMaterial.JELLY

    override fun get(): PieceMaterial = current
    override fun set(material: PieceMaterial) {
        current = material
    }
}

class SharedPreferencesPieceMaterialStore(context: Context) : PieceMaterialStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(): PieceMaterial {
        val stored = prefs.getString(KEY_SELECTED_MATERIAL, null) ?: return PieceMaterial.JELLY
        return runCatching { PieceMaterial.valueOf(stored) }.getOrDefault(PieceMaterial.JELLY)
    }

    override fun set(material: PieceMaterial) {
        prefs.edit().putString(KEY_SELECTED_MATERIAL, material.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "spacecube_piece_material"
        private const val KEY_SELECTED_MATERIAL = "selected_material"
    }
}
