package io.embodit.foldguide.ui.fold

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.embodit.foldguide.domain.fold.Category
import io.embodit.foldguide.domain.fold.FoldLibrary
import io.embodit.foldguide.domain.fold.Paper
import io.embodit.foldguide.domain.fold.StepKind
import io.embodit.foldguide.domain.model.BlockReason
import io.embodit.foldguide.domain.model.CurrentFold
import io.embodit.foldguide.domain.model.Hand
import io.embodit.foldguide.domain.model.LanguagePref
import io.embodit.foldguide.domain.model.Progress
import io.embodit.foldguide.domain.model.Settings
import io.embodit.foldguide.domain.model.Vec3
import io.embodit.foldguide.platform.FoldGraph
import io.embodit.foldguide.platform.FoldLog
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The whole state machine of the app (design `states` S00 - S18). Events in, [FoldUiState] out; the
 * scene renders the state and the panels read it. Progress and settings are written to the
 * repository on every change (design: all offline, local JSON).
 */
class FoldViewModel(private val appContext: Context) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FoldViewModel(context.applicationContext) as T
    }

    private val repository = FoldGraph.repository(appContext)
    private val debuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<FoldUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<FoldUiEffect>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val effects: SharedFlow<FoldUiEffect> = _effects

    private var blockReason: BlockReason? = null
    private var deskJob: Job? = null
    private var loadJob: Job? = null
    private var deskEnteredMs = 0L
    private var noPlanes = false
    private var lastStateId = ""

    /** Where to go back after "move the demo sheet" / cancel: the step (true) or the library. */
    private var placingFromStep = false

    private fun initialState(): FoldUiState {
        val settings = repository.loadSettings(defaultDwell = !debuggable)
        val progress = repository.loadProgress()
        val cur = progress.current
        val start = if (cur != null && FoldLibrary.model(cur.modelId, cur.paper) != null) FoldLibrary.entry(cur.modelId) else null
        return FoldUiState(
            settings = settings,
            progress = progress,
            paper = settings.paper,
            category = start?.category?.takeIf { FoldLibrary.shelf(it, settings.paper).isNotEmpty() } ?: Category.BASICS,
        )
    }

    init {
        FoldLog.i("STATE S00 (init) progress=${s.progress} settings=${s.settings}")
        loadJob =
            viewModelScope.launch {
                delay(LOADING_MIN_MS)
                if (s.mode == Mode.LOADING && !s.hold && s.sceneReady) leaveLoading()
            }
    }

    private val s: FoldUiState
        get() = _state.value

    private fun update(block: FoldUiState.() -> FoldUiState) {
        _state.value = _state.value.block()
        val id = s.stateId
        if (id != lastStateId) {
            lastStateId = id
            FoldLog.i("STATE $id model=${s.model.id} step=${s.step + 1}/${s.model.steps.size} demo=${s.demo != null}")
        }
    }

    fun onEvent(event: FoldEvent) {
        when (event) {
            FoldEvent.SceneReady -> {
                update { copy(sceneReady = true) }
                if (s.mode == Mode.LOADING && !s.hold) viewModelScope.launch {
                    delay(LOADING_MIN_MS / 2)
                    if (s.mode == Mode.LOADING && !s.hold) leaveLoading()
                }
            }
            is FoldEvent.PlanesChanged -> {
                val count = if (noPlanes) 0 else event.deskCount
                update { copy(deskCount = count) }
                if (s.mode == Mode.FINDING_DESK && count > 0 && !s.hold) deskFound()
            }
            is FoldEvent.DemoPlaceRequested ->
                if (s.mode == Mode.PLACE_DEMO || (event.virtual && (s.mode == Mode.FINDING_DESK || s.mode == Mode.NO_DESK))) placeDemo(event.center, event.yaw, event.virtual)
                else if (event.force) {
                    FoldLog.i("DEMO seeded center=${FoldLog.pos(event.center)} virtual=${event.virtual}")
                    update { copy(demo = DemoPose(event.center, event.yaw, event.virtual)) }
                }
            is FoldEvent.DemoPlayed -> if (event.serial == s.stepSerial && !s.playedOnce) update { copy(playedOnce = true) }
            is FoldEvent.ControllerChanged -> if (event.active != s.controllerActive) update { copy(controllerActive = event.active) }
            is FoldEvent.DwellFired -> if (event.serial == s.stepSerial && s.dwellArmed) {
                FoldLog.i("DWELL advance serial=${event.serial}")
                next()
            }
            is FoldEvent.SensingBlocked -> {
                blockReason = event.reason
                if (event.reason == BlockReason.WITHOUT_PERMISSION && s.mode != Mode.NO_PERMISSION) update { copy(mode = Mode.NO_PERMISSION, menuOpen = false, settingsOpen = false) }
            }
            FoldEvent.WelcomeDone -> {
                saveSettings(s.settings.copy(welcomeSeen = true))
                update { copy(mode = if (settings.paperChosen) Mode.LIBRARY else Mode.PAPER) }
            }
            is FoldEvent.ModelPicked -> pickModel(event.id)
            is FoldEvent.PaperPicked -> {
                saveSettings(s.settings.copy(paper = event.paper, paperChosen = true))
                FoldLog.i("PAPER ${event.paper.id}")
                update { copy(mode = Mode.LIBRARY, category = shelfFor(category, event.paper)) }
            }
            FoldEvent.PaperChipClicked -> update { copy(mode = Mode.PAPER, settingsOpen = false, menuOpen = false, confirmClear = false) }
            is FoldEvent.CategoryPicked -> update { copy(category = event.category) }
            FoldEvent.PlaceInFrontClicked -> {
                deskJob?.cancel()
                _effects.tryEmit(FoldUiEffect.PlaceVirtual)
            }
            FoldEvent.SettingsClicked -> update { copy(settingsOpen = !settingsOpen, confirmClear = false, menuOpen = false) }
            FoldEvent.BackClicked -> goLibrary()
            FoldEvent.CancelClicked -> cancelPlacing()
            FoldEvent.NextClicked -> next()
            FoldEvent.PrevClicked -> if (s.mode == Mode.STEP && s.step > 0) enterStep(s.step - 1)
            FoldEvent.ReplayClicked -> if (s.mode == Mode.STEP) update { copy(stepSerial = stepSerial + 1, playedOnce = false, ghost = null) }
            FoldEvent.MoreClicked -> update { copy(menuOpen = !menuOpen, settingsOpen = false, confirmClear = false) }
            FoldEvent.MenuMove -> {
                placingFromStep = true
                update { copy(menuOpen = false) }
                startPlacing()
            }
            FoldEvent.MenuRestart -> {
                update { copy(menuOpen = false) }
                enterStep(0)
            }
            FoldEvent.MenuSettings -> update { copy(menuOpen = false, settingsOpen = true, confirmClear = false) }
            FoldEvent.MenuHome -> goLibrary()
            FoldEvent.MenuDismissed -> if (s.menuOpen) update { copy(menuOpen = false) }
            FoldEvent.AgainClicked -> enterStep(0)
            FoldEvent.ResumeClicked -> {
                val cur = s.progress.current ?: return goLibrary()
                val m = FoldLibrary.model(cur.modelId, cur.paper) ?: return goLibrary()
                update { copy(modelId = m.id, paper = m.paper, step = cur.step.coerceIn(0, m.steps.size - 1)) }
                placingFromStep = true
                startPlacing()
            }
            FoldEvent.RescanClicked -> startPlacing()
            FoldEvent.AllowClicked -> {
                val tries = s.permissionTries + 1
                update { copy(permissionTries = tries) }
                if (tries >= 2) {
                    _effects.tryEmit(FoldUiEffect.OpenAppSettings)
                } else {
                    _effects.tryEmit(FoldUiEffect.RestartSensing)
                    blockReason = null
                    update { copy(mode = Mode.LOADING) }
                    viewModelScope.launch {
                        delay(PERMISSION_RETRY_MS)
                        if (s.mode == Mode.LOADING) leaveLoading()
                    }
                }
            }
            is FoldEvent.LanguageSelected -> saveSettings(s.settings.copy(language = event.language))
            is FoldEvent.HandSelected -> saveSettings(s.settings.copy(hand = event.hand))
            is FoldEvent.DwellSelected -> saveSettings(s.settings.copy(dwell = event.on))
            FoldEvent.ClearClicked -> update { copy(confirmClear = true) }
            FoldEvent.ClearCancelled -> update { copy(confirmClear = false) }
            FoldEvent.ClearConfirmed -> {
                saveProgress(Progress())
                update { copy(settingsOpen = false, confirmClear = false, menuOpen = false) }
                if (s.mode != Mode.STEP) update { copy(mode = Mode.LIBRARY) } else goLibrary()
            }
            FoldEvent.SettingsDismissed -> if (s.settingsOpen) update { copy(settingsOpen = false, confirmClear = false) }
            FoldEvent.WentBackground -> Unit
            FoldEvent.CameForeground -> {
                // back from the system settings after "open system settings": try sensing again
                if (s.mode == Mode.NO_PERMISSION && s.permissionTries >= 2) {
                    _effects.tryEmit(FoldUiEffect.RestartSensing)
                    blockReason = null
                    update { copy(mode = Mode.LOADING, permissionTries = 0) }
                    viewModelScope.launch {
                        delay(PERMISSION_RETRY_MS)
                        if (s.mode == Mode.LOADING) leaveLoading()
                    }
                }
            }
            is FoldEvent.DebugState -> debugState(event.spec)
            is FoldEvent.DebugModel -> {
                val e = FoldLibrary.entry(event.id)
                if (e != null) {
                    val paper = if (FoldLibrary.available(e, s.settings.paper)) s.settings.paper else Paper.A4
                    update { copy(modelId = e.id, paper = paper, step = 0, category = e.category) }
                    if (s.mode == Mode.STEP || s.mode == Mode.DONE) enterStep(0)
                }
            }
            is FoldEvent.DebugPaper -> {
                saveSettings(s.settings.copy(paper = event.paper, paperChosen = true))
                val keep = FoldLibrary.model(s.modelId, event.paper) != null
                update { copy(paper = event.paper, modelId = if (keep) modelId else "basics", step = 0, category = shelfFor(category, event.paper)) }
                if (s.mode == Mode.STEP || s.mode == Mode.DONE) enterStep(0)
            }
            is FoldEvent.DebugStep -> {
                val n = (event.step - 1).coerceIn(0, s.model.steps.size - 1)
                if (s.demo == null) _effects.tryEmit(FoldUiEffect.SeedDemo)
                enterStep(n)
            }
            is FoldEvent.DebugGhost -> update { copy(ghost = event.share) }
            is FoldEvent.DebugDwell -> saveSettings(s.settings.copy(dwell = event.on))
            FoldEvent.DebugNoPlanes -> {
                noPlanes = true
                update { copy(deskCount = 0) }
            }
            FoldEvent.DebugClear -> {
                saveProgress(Progress())
                saveSettings(Settings(dwell = !debuggable))
                update { copy(mode = Mode.WELCOME, demo = null, menuOpen = false, settingsOpen = false, confirmClear = false) }
            }
        }
    }

    // ------------------------------------------------------------------------------------------

    /** S00 -> S17 / S14 / S01 / S07. */
    private fun leaveLoading() {
        loadJob?.cancel()
        val next =
            when {
                blockReason == BlockReason.WITHOUT_PERMISSION -> Mode.NO_PERMISSION
                s.progress.current?.let { FoldLibrary.model(it.modelId, it.paper) } != null -> Mode.RESUME
                !s.settings.welcomeSeen -> Mode.WELCOME
                !s.settings.paperChosen -> Mode.PAPER
                else -> Mode.LIBRARY
            }
        update { copy(mode = next, hold = false) }
    }

    /** A shelf that has models for [paper]: [wanted] when it has, else the first. */
    private fun shelfFor(wanted: Category, paper: Paper): Category = if (FoldLibrary.shelf(wanted, paper).isNotEmpty()) wanted else FoldLibrary.categories(paper).first()

    private fun pickModel(id: String) {
        val paper = s.settings.paper
        val m = FoldLibrary.model(id, paper) ?: return
        FoldLog.i("PICK ${m.key} steps=${m.steps.size} prep=${m.prepSteps}")
        update { copy(modelId = m.id, paper = paper, step = 0, settingsOpen = false, menuOpen = false) }
        // a sheet on the virtual desk top moves to the user's current heading (they may have turned since: D9)
        if (s.demo?.virtual == true) _effects.tryEmit(FoldUiEffect.PlaceVirtual)
        if (s.demo != null) enterStep(0)
        else {
            placingFromStep = false
            startPlacing()
        }
    }

    /** S02: wait for a desk (20 s), then S03. */
    private fun startPlacing() {
        deskJob?.cancel()
        update { copy(mode = Mode.FINDING_DESK, menuOpen = false, settingsOpen = false, confirmClear = false) }
        deskEnteredMs = android.os.SystemClock.elapsedRealtime()
        if (s.deskCount > 0 && !s.hold) {
            deskJob =
                viewModelScope.launch {
                    delay(FINDING_MIN_MS)
                    if (s.mode == Mode.FINDING_DESK) deskFound()
                }
            return
        }
        // factory rule D7: never stuck scanning; after ~5 s without a desk the sheet goes on a virtual desk top ahead
        deskJob =
            viewModelScope.launch {
                delay(VIRTUAL_AFTER_MS)
                if (s.mode == Mode.FINDING_DESK && !s.hold) {
                    FoldLog.i("DESK none after $VIRTUAL_AFTER_MS ms: virtual desk")
                    _effects.tryEmit(FoldUiEffect.PlaceVirtual)
                }
            }
    }

    private fun deskFound() {
        deskJob?.cancel()
        val waited = android.os.SystemClock.elapsedRealtime() - deskEnteredMs
        if (waited < FINDING_MIN_MS) {
            deskJob =
                viewModelScope.launch {
                    delay(FINDING_MIN_MS - waited)
                    if (s.mode == Mode.FINDING_DESK) update { copy(mode = Mode.PLACE_DEMO) }
                }
        } else {
            update { copy(mode = Mode.PLACE_DEMO) }
        }
    }

    private fun placeDemo(center: Vec3, yaw: Float, virtual: Boolean = false) {
        deskJob?.cancel()
        FoldLog.i("DEMO placed center=${FoldLog.pos(center)} yaw=${FoldLog.f(Math.toDegrees(yaw.toDouble()).toFloat(), 1)} virtual=$virtual model=${s.model.key} step=${s.step + 1}")
        update { copy(demo = DemoPose(center, yaw, virtual)) }
        enterStep(s.step)
    }

    private fun cancelPlacing() {
        deskJob?.cancel()
        if (s.demo != null && placingFromStep) enterStep(s.step) else update { copy(mode = Mode.LIBRARY) }
    }

    private fun enterStep(index: Int) {
        val n = index.coerceIn(0, s.model.steps.size - 1)
        update { copy(mode = Mode.STEP, step = n, stepSerial = stepSerial + 1, playedOnce = false, menuOpen = false, settingsOpen = false, confirmClear = false, ghost = null) }
        saveProgress(s.progress.copy(current = CurrentFold(s.model.id, n, s.paper)))
        placingFromStep = true
        FoldLog.i("STEP ${s.model.id} ${n + 1}/${s.model.steps.size} kind=${s.currentStep.kind} serial=${s.stepSerial}")
    }

    private fun next() {
        if (s.mode != Mode.STEP) return
        if (s.lastStep) {
            saveProgress(Progress(s.progress.done + s.model.id, current = null))
            update { copy(mode = Mode.DONE, menuOpen = false, settingsOpen = false) }
            FoldLog.i("DONE ${s.model.id}")
        } else {
            enterStep(s.step + 1)
        }
    }

    private fun goLibrary() {
        deskJob?.cancel()
        update { copy(mode = Mode.LIBRARY, menuOpen = false, settingsOpen = false, confirmClear = false) }
    }

    private fun saveSettings(settings: Settings) {
        update { copy(settings = settings) }
        repository.saveSettings(settings)
    }

    private fun saveProgress(progress: Progress) {
        update { copy(progress = progress) }
        repository.saveProgress(progress)
    }

    // ------------------------------------------------------------------------------------------
    // debug

    /** Straight to a designed state: `S00 [hold]`, `S02`, `S03`, `S07`, `S08 [model] [step]`, ... (see DebugCommands). */
    private fun debugState(spec: String) {
        val parts = spec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val name = parts.firstOrNull()?.uppercase(Locale.US) ?: return
        val args = parts.drop(1).map { it.lowercase(Locale.US) }
        val hold = "hold" in args
        deskJob?.cancel()
        loadJob?.cancel()
        noPlanes = false
        val modelArg = args.firstOrNull { FoldLibrary.entry(it) != null }
        val stepArg = args.firstNotNullOfOrNull { it.toIntOrNull() }
        if (modelArg != null) {
            val e = FoldLibrary.entry(modelArg)!!
            val paper = if (FoldLibrary.available(e, s.paper)) s.paper else if (FoldLibrary.available(e, s.settings.paper)) s.settings.paper else Paper.A4
            update { copy(modelId = modelArg, paper = paper, category = e.category) }
        }
        if (stepArg != null) update { copy(step = (stepArg - 1).coerceIn(0, model.steps.size - 1)) }
        fun needDemo() {
            if (s.demo == null) _effects.tryEmit(FoldUiEffect.SeedDemo)
        }
        update { copy(menuOpen = false, settingsOpen = false, confirmClear = false, hold = hold, ghost = null) }
        when (name) {
            "S00" -> update { copy(mode = Mode.LOADING) }
            "S01" -> update { copy(mode = Mode.WELCOME, demo = null) }
            "S02" -> {
                placingFromStep = s.demo != null
                update { copy(mode = Mode.FINDING_DESK, demo = null) }
                deskEnteredMs = android.os.SystemClock.elapsedRealtime()
                if (!hold) {
                    deskJob =
                        viewModelScope.launch {
                            delay(VIRTUAL_AFTER_MS)
                            if (s.mode == Mode.FINDING_DESK) _effects.tryEmit(FoldUiEffect.PlaceVirtual)
                        }
                    if (s.deskCount > 0) deskFound()
                }
            }
            "S03" -> {
                placingFromStep = s.demo != null
                update { copy(mode = Mode.PLACE_DEMO) }
            }
            "S07" -> update { copy(mode = Mode.LIBRARY) }
            "S08", "S09", "S10" -> {
                if (modelArg == null && stepArg == null) {
                    // a fitting default step for the kind (design qaCapture)
                    when (name) {
                        "S08" -> update { copy(modelId = "masu", paper = Paper.SQUARE, step = 2) }
                        "S09" -> update { copy(modelId = "cup", paper = Paper.SQUARE, step = 4) }
                        else -> update { copy(modelId = "masu", paper = Paper.SQUARE, step = 6) }
                    }
                }
                needDemo()
                if (s.demo != null) enterStep(s.step) else update { copy(mode = Mode.STEP) }
            }
            "S12" -> {
                if (modelArg == null) update { copy(modelId = "cup", paper = Paper.SQUARE) }
                needDemo()
                update { copy(step = model.steps.size - 1) }
                saveProgress(Progress(s.progress.done + s.model.id, current = null))
                update { copy(mode = Mode.DONE) }
            }
            "S13" -> {
                if (s.mode == Mode.LOADING || s.mode == Mode.WELCOME) update { copy(mode = Mode.LIBRARY) }
                update { copy(settingsOpen = true) }
            }
            "S14" -> {
                val m = modelArg ?: "masu"
                val st = (stepArg ?: 5) - 1
                saveProgress(s.progress.copy(current = CurrentFold(m, st, s.paper)))
                update { copy(mode = Mode.RESUME, demo = null, modelId = m, step = st) }
            }
            "S15" -> update { copy(mode = Mode.NO_DESK) }
            "S19" -> update { copy(mode = Mode.PAPER) }
            "S17" -> update { copy(mode = Mode.NO_PERMISSION, permissionTries = if ("second" in args) 1 else 0) }
            "S18" -> {
                if (s.mode == Mode.LOADING || s.mode == Mode.WELCOME) update { copy(mode = Mode.LIBRARY) }
                update { copy(settingsOpen = true, confirmClear = true) }
            }
            else -> FoldLog.i("CMD state unknown $name")
        }
    }

    /** `DUMP {...}`: the state for QA. */
    fun dumpJson(): String {
        val st = s
        val o = JSONObject()
        o.put("state", st.stateId)
        o.put("mode", st.mode.name)
        o.put("model", st.model.id)
        o.put("paper", st.paper.id)
        o.put("settingsPaper", st.settings.paper.id)
        o.put("category", st.category.id)
        o.put("prepSteps", st.model.prepSteps)
        o.put("scale", st.model.scale.toDouble())
        o.put("step", st.step + 1)
        o.put("steps", st.model.steps.size)
        o.put("kind", st.currentStep.kind.name)
        o.put("serial", st.stepSerial)
        o.put("playedOnce", st.playedOnce)
        o.put("menu", st.menuOpen)
        o.put("settings", st.settingsOpen)
        o.put("confirm", st.confirmClear)
        o.put("controller", st.controllerActive)
        o.put("deskCount", st.deskCount)
        o.put("ghost", st.ghost ?: JSONObject.NULL)
        st.demo?.let { d ->
            o.put("demo", JSONObject().put("x", d.center.x).put("y", d.center.y).put("z", d.center.z).put("yawDeg", Math.toDegrees(d.yaw.toDouble())).put("virtual", d.virtual))
        }
        o.put("done", st.progress.done.toList())
        st.progress.current?.let { c -> o.put("current", JSONObject().put("model", c.modelId).put("step", c.step + 1).put("paper", c.paper.id)) }
        o.put("lang", st.settings.language.name)
        o.put("hand", st.settings.hand.name)
        o.put("dwell", st.settings.dwell)
        return o.toString()
    }

    private companion object {
        const val LOADING_MIN_MS = 1100L
        const val FINDING_MIN_MS = 700L
        /** Factory rule D7: about 5 s without a desk, then the virtual desk top. */
        const val VIRTUAL_AFTER_MS = 5_000L
        const val PERMISSION_RETRY_MS = 1500L
    }
}
