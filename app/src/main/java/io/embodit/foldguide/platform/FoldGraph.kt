package io.embodit.foldguide.platform

import android.content.Context
import io.embodit.foldguide.data.FoldRepository
import io.embodit.foldguide.data.PrefsFoldRepository
import io.embodit.foldguide.platform.input.ControllerInput
import io.embodit.foldguide.platform.sense.PlaneTracker
import io.embodit.foldguide.platform.tracking.HeadTracker

/**
 * Process-wide wiring of the platform services (the Spatial Ruler's layout). The scene starts and
 * stops the sensing services (every start has a stop: `FoldScene.attach` / `detach`); the ViewModel
 * only sees the repository.
 */
object FoldGraph {
    val planes = PlaneTracker()
    val head = HeadTracker()
    val controller = ControllerInput()

    @Volatile var appContext: Context? = null

    @Volatile private var repository: FoldRepository? = null

    fun repository(context: Context): FoldRepository =
        repository ?: synchronized(this) { repository ?: PrefsFoldRepository(context).also { repository = it } }
}
