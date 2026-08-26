package tech.illusion.spacecube.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesBasePlateMaterialStoreTest {
    @Test
    fun selectionPersistsAcrossStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_base_plate_material", Context.MODE_PRIVATE)
            .edit().clear().apply()

        SharedPreferencesBasePlateMaterialStore(context).set(BasePlateMaterial.TILES_04)
        val reloaded = SharedPreferencesBasePlateMaterialStore(context)

        assertEquals(BasePlateMaterial.TILES_04, reloaded.get())
    }

    @Test
    fun defaultsToGlassWhenNothingStoredYet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_base_plate_material", Context.MODE_PRIVATE)
            .edit().clear().apply()

        assertEquals(BasePlateMaterial.GLASS, SharedPreferencesBasePlateMaterialStore(context).get())
    }

    @Test
    fun fallsBackToGlassOnUnrecognizedStoredValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_base_plate_material", Context.MODE_PRIVATE)
            .edit().putString("selected_material", "SOME_FUTURE_MATERIAL_THIS_VERSION_DOESNT_KNOW").apply()

        assertEquals(BasePlateMaterial.GLASS, SharedPreferencesBasePlateMaterialStore(context).get())
    }
}
