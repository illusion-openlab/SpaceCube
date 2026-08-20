package tech.illusion.spacecube.game

enum class Difficulty(val baseIntervalMs: Long) {
    SLOW(1000L),
    NORMAL(700L),
    FAST(450L),
}

object Scoring {
    private const val MIN_INTERVAL_MS = 120L
    private const val INTERVAL_STEP_MS = 60L
    private const val LINES_PER_LEVEL = 10
    private val POINTS_PER_LINE_COUNT = mapOf(1 to 100, 2 to 300, 3 to 500, 4 to 800)

    fun pointsForClearedLines(lineCount: Int, level: Int): Int {
        val basePoints = POINTS_PER_LINE_COUNT[lineCount] ?: 0
        return basePoints * level
    }

    fun levelForLinesCleared(totalLinesCleared: Int): Int =
        1 + totalLinesCleared / LINES_PER_LEVEL

    fun fallIntervalMs(difficulty: Difficulty, level: Int): Long =
        maxOf(MIN_INTERVAL_MS, difficulty.baseIntervalMs - (level - 1) * INTERVAL_STEP_MS)
}
