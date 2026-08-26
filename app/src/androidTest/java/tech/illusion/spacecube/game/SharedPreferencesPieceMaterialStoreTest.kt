package tech.illusion.spacecube.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesPieceMaterialStoreTest {
    @Test
    fun selectionPersistsAcrossStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_piece_material", Context.MODE_PRIVATE)
            .edit().clear().apply()

        SharedPreferencesPieceMaterialStore(context).set(PieceMaterial.TILES_04)
        val reloaded = SharedPreferencesPieceMaterialStore(context)

        assertEquals(PieceMaterial.TILES_04, reloaded.get())
    }

    @Test
    fun defaultsToJellyWhenNothingStoredYet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_piece_material", Context.MODE_PRIVATE)
            .edit().clear().apply()

        assertEquals(PieceMaterial.JELLY, SharedPreferencesPieceMaterialStore(context).get())
    }

    @Test
    fun fallsBackToJellyOnUnrecognizedStoredValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_piece_material", Context.MODE_PRIVATE)
            .edit().putString("selected_material", "SOME_FUTURE_MATERIAL_THIS_VERSION_DOESNT_KNOW").apply()

        assertEquals(PieceMaterial.JELLY, SharedPreferencesPieceMaterialStore(context).get())
    }
}
