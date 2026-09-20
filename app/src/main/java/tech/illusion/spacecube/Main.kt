package tech.illusion.spacecube

import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage
import tech.illusion.spacecube.content.ConfigPage
import tech.illusion.spacecube.content.GAME_STAGE_ID
import tech.illusion.spacecube.content.GamePage

// 默认空间容器是 Volumetric 配置窗口（属性见 AndroidManifest.xml —— DefaultWindowContainer
// 这个 DSL 函数没有任何属性参数，属性只能写 manifest）。游戏本体是一个非默认 Stage：
// 只在这里声明 id 和内容，style 在 ConfigPage 调 openStage 时给（Stage() 没有 style 参数）。
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PicoTheme { ConfigPage() }
        }
        Stage(id = GAME_STAGE_ID) {
            // bundle 来自 StageScope（继承 SpatialContainerScope），承载 openStage 传来的难度。
            PicoTheme { GamePage(bundle) }
        }
    }
