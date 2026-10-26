package io.embodit.foldguide.ui.fold

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import io.embodit.foldguide.platform.FoldLog
import io.embodit.foldguide.ui.fold.components.FoldStage
import io.embodit.foldguide.ui.fold.components.LocalFoldResources
import io.embodit.foldguide.ui.fold.components.localizedResources
import io.embodit.foldguide.ui.fold.debug.FoldProbe
import io.embodit.foldguide.ui.fold.scene.FoldFrameSystem
import io.embodit.foldguide.ui.fold.scene.FoldScene

/**
 * The one screen of the app, living in a Mixed Stage. It wires the three parts together: the
 * ViewModel holds the state, the [FoldScene] turns state into entities and reports taps, planes and
 * the demo's progress, and [FoldStage] declares the panels.
 */
@Composable
fun FoldScreen() {
    val context = LocalContext.current
    val viewModel: FoldViewModel = viewModel(factory = FoldViewModel.Factory(context))
    val state by viewModel.state.collectAsStateWithLifecycle()

    remember(context) {
        FoldProbe.enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        true
    }
    val scene = remember(viewModel) { FoldScene(viewModel::onEvent) }

    DisposableEffect(scene) {
        registerSystem<FoldFrameSystem>()
        FoldProbe.host =
            object : FoldProbe.Host {
                override fun dispatch(event: FoldEvent) = viewModel.onEvent(event)

                override fun uiState(): FoldUiState = viewModel.state.value

                override fun dumpJson(): String = viewModel.dumpJson()

                override fun scene(): FoldProbe.SceneProbe = scene

                override fun pressControl(name: String): Boolean = FoldProbe.click(name)

                override fun sceneHandle(): Any = scene
            }
        FoldLog.i("STAGE enter")
        onDispose {
            FoldProbe.host = null
            unregisterSystem<FoldFrameSystem>()
            scene.detach()
            FoldLog.i("STAGE disposed")
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    when (event) {
                        Lifecycle.Event.ON_STOP -> viewModel.onEvent(FoldEvent.WentBackground)
                        Lifecycle.Event.ON_START -> viewModel.onEvent(FoldEvent.CameForeground)
                        else -> Unit
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(scene, state) { scene.render(state) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect: FoldUiEffect ->
            when (effect) {
                FoldUiEffect.SeedDemo -> scene.seedDemo()
                FoldUiEffect.RestartSensing -> scene.restartSensing()
                FoldUiEffect.OpenAppSettings ->
                    runCatching {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }.onFailure { FoldLog.w("SETTINGS open failed: $it") }
            }
        }
    }

    val resources = remember(state.language, context) { localizedResources(context, state.language) }
    CompositionLocalProvider(LocalFoldResources provides resources) {
        FoldStage(state = state, scene = scene, onEvent = viewModel::onEvent)
    }
}
