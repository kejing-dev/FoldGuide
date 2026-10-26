package io.embodit.foldguide.ui.fold

import io.embodit.foldguide.domain.fold.FoldLibrary
import io.embodit.foldguide.domain.fold.FoldModel
import io.embodit.foldguide.domain.fold.Step
import io.embodit.foldguide.domain.fold.StepKind
import io.embodit.foldguide.domain.model.BlockReason
import io.embodit.foldguide.domain.model.CurrentFold
import io.embodit.foldguide.domain.model.Hand
import io.embodit.foldguide.domain.model.LanguagePref
import io.embodit.foldguide.domain.model.Progress
import io.embodit.foldguide.domain.model.Settings
import io.embodit.foldguide.domain.model.Vec3

/** Base states of the design package, as the app's mode (S13 / S18 are the settings popup over any of them). */
enum class Mode {
    LOADING, // S00
    WELCOME, // S01
    FINDING_DESK, // S02
    PLACE_DEMO, // S03
    LIBRARY, // S07
    STEP, // S08 fold / S09 flip / S10 form, by the step kind
    DONE, // S12
    RESUME, // S14
    NO_DESK, // S15
    NO_PERMISSION, // S17
}

/** Where the demo sheet lies: its centre on the desk and its yaw (the "away" axis = yaw applied to -Z). */
data class DemoPose(val center: Vec3, val yaw: Float)

data class FoldUiState(
    val mode: Mode = Mode.LOADING,
    val modelIndex: Int = 1,
    val step: Int = 0,
    val demo: DemoPose? = null,
    /** Bumped on every step entry and replay: the scene restarts the demo, the dwell timer resets. */
    val stepSerial: Int = 0,
    /** The demo has turned the flap at least once since the step began (gates look-to-advance). */
    val playedOnce: Boolean = false,
    val menuOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val confirmClear: Boolean = false,
    val settings: Settings = Settings(),
    val progress: Progress = Progress(),
    /** A controller delivers poses: the step panel says "put the controller down" + the stick hint. */
    val controllerActive: Boolean = false,
    val permissionTries: Int = 0,
    /** How many desk planes are known (S02 waits for the first). */
    val deskCount: Int = 0,
    /** The settings popup was opened from the step panel's more menu (anchors above the guide panel either way). */
    val sceneReady: Boolean = false,
    /** Debug: freeze the demo animation at this share of the fold (0..1). */
    val ghost: Float? = null,
    /** Debug: `state Sxx hold` keeps a timed state from moving on. */
    val hold: Boolean = false,
) {
    val model: FoldModel
        get() = FoldLibrary.all[modelIndex.coerceIn(0, FoldLibrary.all.size - 1)]

    val currentStep: Step
        get() = model.steps[step.coerceIn(0, model.steps.size - 1)]

    val lastStep: Boolean
        get() = step >= model.steps.size - 1

    /** The designed state id the screen is in (for logs and QA). */
    val stateId: String
        get() =
            when {
                settingsOpen && confirmClear -> "S18"
                settingsOpen -> "S13"
                else ->
                    when (mode) {
                        Mode.LOADING -> "S00"
                        Mode.WELCOME -> "S01"
                        Mode.FINDING_DESK -> "S02"
                        Mode.PLACE_DEMO -> "S03"
                        Mode.LIBRARY -> "S07"
                        Mode.STEP ->
                            when (currentStep.kind) {
                                StepKind.FLIP -> "S09"
                                StepKind.FORM -> "S10"
                                else -> "S08"
                            }
                        Mode.DONE -> "S12"
                        Mode.RESUME -> "S14"
                        Mode.NO_DESK -> "S15"
                        Mode.NO_PERMISSION -> "S17"
                    }
            }

    /** Look-to-advance applies: S08 "Next" only, not on the last step, once the demo has played (design `input.dwell`). */
    val dwellArmed: Boolean
        get() = settings.dwell && mode == Mode.STEP && currentStep.kind != StepKind.FLIP && currentStep.kind != StepKind.FORM && !lastStep && playedOnce && !menuOpen && !settingsOpen

    val language: LanguagePref
        get() = settings.language

    val hand: Hand
        get() = settings.hand

    val resume: CurrentFold?
        get() = progress.current
}

/** Every input of the ViewModel: scene callbacks, panel controls, lifecycle and the debug hooks. */
sealed class FoldEvent {
    // scene
    data object SceneReady : FoldEvent()

    data class PlanesChanged(val deskCount: Int) : FoldEvent()

    /** A pinch on a desk plane while placing: the demo centre and the yaw towards the user. */
    data class DemoPlaceRequested(val center: Vec3, val yaw: Float, val force: Boolean = false) : FoldEvent()

    data class DemoPlayed(val serial: Int) : FoldEvent()

    data class ControllerChanged(val active: Boolean) : FoldEvent()

    /** The gaze rested on "Next" for a second (scene frame system). */
    data class DwellFired(val serial: Int) : FoldEvent()

    data class SensingBlocked(val reason: BlockReason?) : FoldEvent()

    // panels
    data object WelcomeDone : FoldEvent()

    data class ModelPicked(val index: Int) : FoldEvent()

    data object SettingsClicked : FoldEvent()

    data object BackClicked : FoldEvent()

    data object CancelClicked : FoldEvent()

    data object NextClicked : FoldEvent()

    data object PrevClicked : FoldEvent()

    data object ReplayClicked : FoldEvent()

    data object MoreClicked : FoldEvent()

    data object MenuMove : FoldEvent()

    data object MenuRestart : FoldEvent()

    data object MenuSettings : FoldEvent()

    data object MenuHome : FoldEvent()

    data object MenuDismissed : FoldEvent()

    data object AgainClicked : FoldEvent()

    data object ResumeClicked : FoldEvent()

    data object RescanClicked : FoldEvent()

    data object AllowClicked : FoldEvent()

    data class LanguageSelected(val language: LanguagePref) : FoldEvent()

    data class HandSelected(val hand: Hand) : FoldEvent()

    data class DwellSelected(val on: Boolean) : FoldEvent()

    data object ClearClicked : FoldEvent()

    data object ClearConfirmed : FoldEvent()

    data object ClearCancelled : FoldEvent()

    data object SettingsDismissed : FoldEvent()

    // lifecycle
    data object WentBackground : FoldEvent()

    data object CameForeground : FoldEvent()

    // debug hooks
    data class DebugState(val spec: String) : FoldEvent()

    data class DebugModel(val id: String) : FoldEvent()

    data class DebugStep(val step: Int) : FoldEvent()

    data class DebugGhost(val share: Float?) : FoldEvent()

    data class DebugDwell(val on: Boolean) : FoldEvent()

    data object DebugNoPlanes : FoldEvent()

    data object DebugClear : FoldEvent()
}

/** One-shot effects the screen carries out. */
sealed class FoldUiEffect {
    /** Debug: put the demo sheet on the nearest table at a fixed pose. */
    data object SeedDemo : FoldUiEffect()

    data object RestartSensing : FoldUiEffect()

    data object OpenAppSettings : FoldUiEffect()
}
