package io.embodit.foldguide.ui.fold.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import io.embodit.foldguide.ui.fold.FoldEvent
import io.embodit.foldguide.ui.fold.FoldUiState
import io.embodit.foldguide.ui.fold.debug.LocalProbePanel
import io.embodit.foldguide.ui.fold.scene.FoldScene
import io.embodit.foldguide.ui.fold.scene.PanelSpec

/**
 * The Stage content: one full-size SpatialView that carries the 3D scene and the panel pool. Every
 * attachment panel is declared here once, with a fixed size, whether it is showing or not; the scene
 * parents each panel entity under its holder and shows or hides the holder.
 *
 * Window model: single panel with popups. The guide panel is the only standing panel; settings and
 * the more menu are popups above it.
 */
@Composable
internal fun FoldStage(state: FoldUiState, scene: FoldScene, onEvent: (FoldEvent) -> Unit) {
    val context = LocalContext.current
    val bindTick = scene.bindTick.intValue

    SpatialView(
        modifier =
            Modifier
                .fillMaxSize()
                // tap and drag live in separate pointerInput blocks (two spatial recognizers must not share one)
                .pointerInput(Unit) {
                    detectSpatialTapGesture(context = context, targetedToEntity = TargetEntity.any()) { value -> scene.onSpatialTap(value) }
                }
                .pointerInput(Unit) {
                    detectSpatialDragGesture(
                        context = context,
                        targetedToEntity = TargetEntity.any(),
                        onDragStart = { start, value -> scene.onSpatialDrag("start", start, value) },
                        onDragEnd = { value -> scene.onSpatialDrag("end", null, value) },
                        onDragCancel = { value -> scene.onSpatialDrag("cancel", null, value) },
                    ) { value ->
                        scene.onSpatialDrag("move", null, value)
                    }
                },
        attachments = {
            AttachmentPanel(PanelSpec.GUIDE) {
                PoolPanel(PanelSpec.GUIDE, scene) { inView -> GuidePanel(state = state, filled = inView, onEvent = onEvent) }
            }
            AttachmentPanel(PanelSpec.SETTINGS) {
                PoolPanel(PanelSpec.SETTINGS, scene) { inView -> SettingsPanel(state = state, filled = inView, onEvent = onEvent) }
            }
            AttachmentPanel(PanelSpec.MENU) {
                PoolPanel(PanelSpec.MENU, scene) { inView -> MenuPanel(filled = inView, onEvent = onEvent) }
            }
        },
        update = { _, attachments ->
            @Suppress("UNUSED_EXPRESSION") bindTick
            scene.bindPanels(attachments)
        },
        initial = { content, attachments -> scene.attach(content, attachments) },
    )
}

/**
 * Root of a pooled panel. [content] gets `inView`: the panel has been moved into view. A pooled
 * panel must draw its content only then (what it draws while parked out of sight stays broken on the
 * emulator). Presses on it hold the lazy follow still.
 */
@Composable
private fun PoolPanel(id: String, scene: FoldScene, content: @Composable (inView: Boolean) -> Unit) {
    val inView = scene.panelShown[id] == true
    CompositionLocalProvider(LocalProbePanel provides id) {
        Box(
            modifier =
                Modifier.pointerInput(id) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) scene.onPanelPointer(true)
                            if (event.type == PointerEventType.Release) scene.onPanelPointer(false)
                        }
                    }
                }
        ) {
            content(inView)
        }
    }
}
