package io.embodit.foldguide.ui.fold.components

import androidx.compose.runtime.mutableFloatStateOf

/**
 * Where the "Next" button lies on the guide panel, in the panel's root pixels, and how far the
 * look-to-advance count has come. The button registers its bounds (`Modifier.dwellTarget`); the
 * scene's frame system tests the gaze ray against them every frame and writes [progress] (0..1,
 * quantised) for the ring. Always on, in every build: the system does not tell the app where its
 * pointer hovers, so the gaze is the head's forward ray.
 */
object DwellTarget {
    data class Bounds(val cx: Float, val cy: Float, val w: Float, val h: Float, val rootW: Int, val rootH: Int)

    @Volatile var bounds: Bounds? = null

    /** 0..1 in steps of 0.05 (written by the scene while the count runs). */
    val progress = mutableFloatStateOf(0f)
}
