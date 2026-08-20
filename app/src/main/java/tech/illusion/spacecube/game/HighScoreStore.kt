package tech.illusion.spacecube.game

import android.content.Context

interface HighScoreStore {
    fun lastScore(): Int
    fun highScore(): Int
    fun recordScore(score: Int)
}

class InMemoryHighScoreStore : HighScoreStore {
    private var last = 0
    private var best = 0

    override fun lastScore(): Int = last
    override fun highScore(): Int = best

    override fun recordScore(score: Int) {
        last = score
        if (score > best) best = score
    }
}

class SharedPreferencesHighScoreStore(context: Context) : HighScoreStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun lastScore(): Int = prefs.getInt(KEY_LAST_SCORE, 0)
    override fun highScore(): Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    override fun recordScore(score: Int) {
        val newHigh = maxOf(score, highScore())
        prefs.edit()
            .putInt(KEY_LAST_SCORE, score)
            .putInt(KEY_HIGH_SCORE, newHigh)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "spacecube_scores"
        private const val KEY_LAST_SCORE = "last_score"
        private const val KEY_HIGH_SCORE = "high_score"
    }
}
