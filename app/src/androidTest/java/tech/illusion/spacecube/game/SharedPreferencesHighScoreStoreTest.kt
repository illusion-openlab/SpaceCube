package tech.illusion.spacecube.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesHighScoreStoreTest {
    @Test
    fun recordedScorePersistsAcrossStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_scores", Context.MODE_PRIVATE).edit().clear().apply()

        SharedPreferencesHighScoreStore(context).recordScore(150)
        val reloaded = SharedPreferencesHighScoreStore(context)

        assertEquals(150, reloaded.lastScore())
        assertEquals(150, reloaded.highScore())
    }
}
