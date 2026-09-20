package tech.illusion.spacecube.content

/**
 * 配置窗口（默认空间容器）的 id。
 *
 * **必须与 `AndroidManifest.xml` 里 `pico.spatial.windowcontainer.id` 的值逐字一致** ——
 * `minimizeWindowContainer(id)` / `restoreWindowContainer(id)` 就是按这个字符串定位窗口的，
 * 对不上不会报错，只会静默什么都不发生。
 */
const val CONFIG_WINDOW_ID = "SpaceCubeConfigWindow"

/**
 * 游戏 Stage 的 id。非默认 Stage 只需在 `mainApp` 的 DSL 里 `Stage(id = …)` 声明，
 * 不必写进 manifest（官方文档 declare-a-stage 明说"can also be declared in
 * AndroidManifest.xml **as needed**"）。
 */
const val GAME_STAGE_ID = "SpaceCubeStage"
