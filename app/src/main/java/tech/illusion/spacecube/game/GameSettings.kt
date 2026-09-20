package tech.illusion.spacecube.game

object GameSettings {
    var difficulty: Difficulty = Difficulty.NORMAL
}

/**
 * 配置窗口经 `openStage` 的 `Bundle` 把难度交给游戏 Stage 时用的 key。
 *
 * 用 Bundle 而不是直接写 [GameSettings] 单例：两个容器各自绑一个 Activity，
 * 无法确定它们一定同进程；真跨进程时单例会**静默**退回默认值，是一种不报错的错误。
 */
const val DIFFICULTY_BUNDLE_KEY = "spacecube.difficulty"

/**
 * 把 Bundle 里带过来的难度名解析回 [Difficulty]。
 *
 * 名字缺失或不认识时退回 [Difficulty.NORMAL] 而不是抛异常：这个值跨了容器
 * （可能还跨进程）边界，"以中等速度开局" 比 "启动即崩" 是更好的失败方式。
 */
fun parseDifficulty(name: String?): Difficulty =
    Difficulty.entries.firstOrNull { it.name == name } ?: Difficulty.NORMAL
