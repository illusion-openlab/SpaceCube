package tech.illusion.spacecube

import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import tech.illusion.spacecube.content.GamePage

// The default container is a full-immersion Stage (StageStyle.Mixed - see
// AndroidManifest.xml), not a WindowContainer: no window chrome (caption bar,
// minimize/resize handles) to interfere with drag/tap gameplay gestures.
// GamePage renders its own start screen until the player taps "开始游戏".
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme {
                GamePage()
            }
        }
    }
