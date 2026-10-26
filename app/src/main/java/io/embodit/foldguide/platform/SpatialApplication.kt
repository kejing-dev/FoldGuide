package io.embodit.foldguide.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import io.embodit.foldguide.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FoldGraph.appContext = applicationContext
        launch(::mainApp)
    }
}
