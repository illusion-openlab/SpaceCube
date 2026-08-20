package tech.illusion.spacecube.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import tech.illusion.spacecube.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
