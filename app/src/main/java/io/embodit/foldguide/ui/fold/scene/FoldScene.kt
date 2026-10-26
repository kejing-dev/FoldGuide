package io.embodit.foldguide.ui.fold.scene

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.gesture.SpatialDragValue
import com.pico.spatial.ui.foundation.gesture.SpatialTapValue
import com.pico.spatial.ui.geometry.Offset3D
import io.embodit.foldguide.domain.fold.FoldEngine
import io.embodit.foldguide.domain.fold.FoldModel
import io.embodit.foldguide.domain.fold.Sheet
import io.embodit.foldguide.domain.fold.StepKind
import io.embodit.foldguide.domain.model.BlockReason
import io.embodit.foldguide.domain.model.Hand
import io.embodit.foldguide.domain.model.PlaneSurface
import io.embodit.foldguide.domain.model.Quat4
import io.embodit.foldguide.domain.model.SurfaceKind
import io.embodit.foldguide.domain.model.Vec3
import io.embodit.foldguide.domain.usecase.DeskUseCase
import io.embodit.foldguide.domain.usecase.FacePanelUseCase
import io.embodit.foldguide.domain.usecase.HitSurfaceUseCase
import io.embodit.foldguide.domain.usecase.PanelFacing
import io.embodit.foldguide.platform.FoldGraph
import io.embodit.foldguide.platform.FoldLog
import io.embodit.foldguide.platform.sense.PlaneTracker
import io.embodit.foldguide.platform.tracking.HeadPose
import io.embodit.foldguide.ui.fold.DemoPose
import io.embodit.foldguide.ui.fold.FoldEvent
import io.embodit.foldguide.ui.fold.FoldUiState
import io.embodit.foldguide.ui.fold.Mode
import io.embodit.foldguide.ui.fold.components.DwellTarget
import io.embodit.foldguide.ui.fold.debug.FoldProbe
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject

/** The framework instantiates the system itself, so the running scene is reached through this holder. */
internal object ActiveScene {
    @Volatile var scene: FoldScene? = null
}

/** The one per-frame ECS system of the app. */
class FoldFrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        ActiveScene.scene?.onFrame(context)
    }
}

/**
 * The 3D side of the app: the demo sheet with its guides, the placement reticle, the desk tap
 * targets, and the placement of the pre-built panels. Compose state is rendered through [render]
 * (idempotent diff); per-frame work (demo motion, reticle, lazy follow, controller stick) happens in
 * [onFrame] only and never goes through Compose. Everything started in [attach] is stopped in
 * [detach]. Main thread only.
 */
class FoldScene(private val sink: (FoldEvent) -> Unit) : FoldProbe.SceneProbe {

    /** Panel id -> the panel is in view: a pooled panel composes its content only then. */
    val panelShown = mutableStateMapOf<String, Boolean>()

    /** Bumped to make `SpatialView.update` run again while panel entities are still missing. */
    val bindTick = mutableIntStateOf(0)

    private val main = Handler(Looper.getMainLooper())
    private val planeTracker = FoldGraph.planes
    private val headTracker = FoldGraph.head
    private val controller = FoldGraph.controller
    private val deskRule = DeskUseCase()
    private val hits = HitSurfaceUseCase()
    private val facingRule = FacePanelUseCase()

    private var content: SpatialViewContent? = null
    private var stageRoot: Entity? = null
    private var contentProbe: Entity? = null
    private var materials: SceneMaterials? = null
    private var shared: SharedResources? = null
    private var colliders: PlaneColliders? = null
    private var rig: PanelRig? = null
    private var reticle: ReticleView? = null
    private var paperRoot: Entity? = null
    private var liftNode: Entity? = null
    private var paperTap: Entity? = null
    private var paper: PaperView? = null
    private var guides: GuideViews? = null
    private var form: FormView? = null
    private var attached = false

    private val follower = Follower("guide", GUIDE_OFFSET, GUIDE_HALF_EXTENTS, GUIDE_LEAN)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var buildJob: Job? = null
    private var stepJob: Job? = null

    private var ui = FoldUiState()
    private var head = HeadPose(Vec3.ZERO, Quat4.IDENTITY)
    private var hasHead = false
    private var lastPlaneCount = -1
    private var lastDeskCount = -1
    private var lastPlanesEmitMs = 0L
    private var blockSinceMs = NEVER
    private var sentBlock: BlockReason? = null
    private var panelPressed = false
    private var lastPanelPressMs = NEVER
    private var controllerActive = false
    private var controllerChangedMs = NEVER
    private var lastFrameMs = 0L
    private var fpsOn = false
    private var frames = 0
    private var fpsStartMs = 0L

    // the demo sheet
    private val sheetsCache = HashMap<String, List<Sheet>>()
    private var builtKey: String = ""
    private var playedSent = -1
    private var spin = 0f
    private var spinBackFrom = 0f
    private var spinBackStartMs = NEVER
    private var lastSpinInputMs = NEVER
    private var dragging = false
    private var doneSpin = 0f
    private var formLift = 0f

    // panel poses
    private var deskPanelPose: Pair<Vec3, Quat4>? = null
    private var deskPanelKey: String = ""
    private var plantedPose: Pair<Vec3, Quat4>? = null
    private var guidePose: Pair<Vec3, Quat4>? = null
    private var noPlanes = false

    /** Plane updates waiting for a collider (id -> surface, null = removed); drained [COLLIDERS_PER_FRAME] per frame. */
    private val planeQueue = LinkedHashMap<String, PlaneSurface?>()

    // look-to-advance (design `input.dwell`): counted here from the head's forward ray
    /** Debug: a Stage point the gaze is taken to rest on instead of the head's forward (emulator tests). */
    @Volatile var gazeOverride: Vec3? = null
    private var dwellMs = 0f
    private var dwellStartMs = NEVER
    private var dwellSerial = -1
    private var dwellLeftSinceStep = false
    private var dwellProgressWritten = 0f

    /** The guide panel's pointer: a press holds the lazy follow still. */
    fun onPanelPointer(pressed: Boolean) {
        panelPressed = pressed
        lastPanelPressMs = SystemClock.elapsedRealtime()
    }

    // =========================================================================================
    // lifecycle
    // =========================================================================================

    fun attach(content: SpatialViewContent, attachments: SpatialViewAttachments) {
        if (attached || stageRoot != null) detach()
        val t0 = SystemClock.elapsedRealtime()
        this.content = content
        val root = Entity().named("stageRoot")
        content.addEntity(root)
        val probe = Entity().named("contentProbe")
        content.addEntity(probe)
        contentProbe = probe
        // An entity added to the SpatialView is not at the Stage origin (0.256 m off in z for a
        // full-size view). Move the root once so that its local space equals Stage space.
        val offset = root.convertPositionFrom(Vector3.ZERO, null)
        val rotation = root.convertRotationFrom(Quat.identity(), null)
        root.components[TransformComponent::class.java]?.apply {
            setPosition(offset)
            setQuaternion(rotation)
        }
        stageRoot = root
        FoldLog.i("STAGE root offset=${FoldLog.pos(offset.domain())}")

        val rig = PanelRig(root) { id, shown -> panelShown[id] = shown }
        this.rig = rig
        for (id in PanelSpec.pool) {
            val (w, h) = PanelSpec.size(id)
            rig.world(id, w, h).autoFace = false
        }
        headTracker.start()
        controller.start()
        planeTracker.start(
            PlaneTracker.Listener { id, surface, _ ->
                // the emulator reports dozens of planes in one burst: colliders are made a few per frame
                planeQueue[id] = surface
            }
        )
        ActiveScene.scene = this
        bindPanels(attachments)
        follower.reset()
        buildJob =
            scope.launch {
                try {
                    build(root, t0)
                } catch (t: Throwable) {
                    if (t !is CancellationException) FoldLog.e("SCENE build failed: $t", t)
                    throw t
                }
            }
    }

    /** Materials, colliders, the reticle and the paper frame; the main thread is given back after every mesh. */
    private suspend fun build(root: Entity, t0: Long) {
        val sh = SharedResources()
        shared = sh
        val m = SceneMaterials(sh)
        materials = m
        yield()
        colliders = PlaneColliders(root).also { it.buildBackstop() }
        for (p in planeTracker.all) planeQueue.putIfAbsent(p.id, p)
        yield()
        reticle = ReticleView(root, m).also { it.prepare() }
        yield()
        val pr = Entity().named("paperRoot")
        root.addChild(pr)
        pr.enabled = false
        paperRoot = pr
        val lift = Entity().named("paperLift")
        pr.addChild(lift)
        liftNode = lift
        val tap = Entity().named("paperTap")
        pr.addChild(tap)
        tap.tapBox(Vec3(PAPER_TAP_M, 0.02f, PAPER_TAP_M), Vec3(0f, 0.01f, 0f))
        paperTap = tap
        paper = PaperView(lift, m)
        guides = GuideViews(lift, m)
        form = FormView(lift, m)
        attached = true
        FoldLog.i("SCENE attached build_ms=${SystemClock.elapsedRealtime() - t0}")
        render(ui, force = true)
        sink(FoldEvent.SceneReady)
    }

    /** Called from `SpatialView(update = ...)`: parents freshly composed panel entities under their holders. */
    fun bindPanels(attachments: SpatialViewAttachments) {
        val rig = rig ?: return
        val complete = rig.bind(attachments, PanelSpec.pool, SystemClock.elapsedRealtime())
        if (!complete) {
            main.removeCallbacks(rebind)
            main.postDelayed(rebind, REBIND_DELAY_MS)
        }
    }

    private val rebind = Runnable { if (stageRoot != null) bindTick.intValue++ }

    fun detach() {
        if (!attached && stageRoot == null) return
        attached = false
        buildJob?.cancel()
        buildJob = null
        stepJob?.cancel()
        stepJob = null
        if (ActiveScene.scene === this) ActiveScene.scene = null
        main.removeCallbacksAndMessages(null)
        planeTracker.stop()
        headTracker.stop()
        controller.stop()
        panelShown.clear()
        runCatching { rig?.release() }
        paper?.clear()
        guides?.clear()
        form?.clear()
        reticle?.destroy()
        colliders?.clear()
        runCatching { paperRoot?.destroy(true) }
        runCatching { stageRoot?.destroy(true) }
        runCatching { contentProbe?.destroy(true) }
        shared?.close()
        shared = null
        stageRoot = null
        contentProbe = null
        content = null
        materials = null
        colliders = null
        rig = null
        reticle = null
        paperRoot = null
        liftNode = null
        paperTap = null
        paper = null
        guides = null
        form = null
        hasHead = false
        lastPlaneCount = -1
        lastDeskCount = -1
        builtKey = ""
        deskPanelPose = null
        plantedPose = null
        planeQueue.clear()
        dwellMs = 0f
        dwellStartMs = NEVER
        dwellSerial = -1
        DwellTarget.progress.floatValue = 0f
        FoldLog.i("SCENE detached")
    }

    /** Restarts the head tracker (S17 "allow"). */
    fun restartSensing() {
        headTracker.stop()
        headTracker.start()
        blockSinceMs = NEVER
        sentBlock = null
    }

    // =========================================================================================
    // model -> entities (called when the UI state changes)
    // =========================================================================================

    fun render(state: FoldUiState, force: Boolean = false) {
        val previous = ui
        ui = state
        if (!attached) return
        val now = SystemClock.elapsedRealtime()
        val demo = state.demo
        // where the guide panel stands once the demo sheet is down (recomputed when the sheet or the hand changes)
        if (demo != null) {
            val key = "${demo.center}|${demo.yaw}|${state.hand}"
            if (key != deskPanelKey || force) {
                deskPanelKey = key
                deskPanelPose = deskPanelPoseFor(demo, state.hand, head.position)
                FoldLog.i("PANEL desk pose=${FoldLog.pos(deskPanelPose?.first)}")
            }
        } else {
            deskPanelKey = ""
            deskPanelPose = null
        }
        if (state.mode != Mode.PLACE_DEMO) plantedPose = null
        if (state.mode != Mode.DONE) doneSpin = 0f
        // the demo sheet
        val showPaper = demo != null && (state.mode == Mode.STEP || state.mode == Mode.DONE)
        paperRoot?.enabled = showPaper
        paperTap?.enabled = showPaper
        if (demo != null) paperRoot?.setLocal(position = demo.center, rotation = Quat4.axisAngle(Vec3.UP, demo.yaw + spin + doneSpin))
        val key =
            if (!showPaper) "" else "${state.model.id}|${state.step}|${state.stepSerial}|${state.mode}|${state.ghost ?: "-"}"
        if (key != builtKey || force) {
            builtKey = key
            if (key.isEmpty()) {
                stepJob?.cancel()
                paper?.clear()
                guides?.clear()
                form?.clear()
            } else {
                rebuildStep(state, now)
            }
        }
        if (previous.mode != state.mode && state.mode != Mode.PLACE_DEMO) reticle?.hide()
        // panels
        val rig = rig ?: return
        if (!state.menuOpen) rig[PanelSpec.MENU]?.hide()
        if (!state.settingsOpen) rig[PanelSpec.SETTINGS]?.hide()
    }

    private fun sheetsOf(model: FoldModel): List<Sheet> = sheetsCache.getOrPut(model.id) { FoldEngine.build(model) }

    /** Builds the demo sheet and the guides of the current step (a mesh per frame). */
    private fun rebuildStep(state: FoldUiState, now: Long) {
        val paper = paper ?: return
        val guides = guides ?: return
        val form = form ?: return
        stepJob?.cancel()
        playedSent = -1
        spin = 0f
        spinBackStartMs = NEVER
        val model = state.model
        val sheets = sheetsOf(model)
        stepJob =
            scope.launch {
                try {
                    val t0 = SystemClock.elapsedRealtime()
                    if (state.mode == Mode.DONE) {
                        val last = model.steps.last()
                        if (last.kind == StepKind.FORM && last.form != null) {
                            paper.clear()
                            guides.clear()
                            formLift = FORM_LIFT_M
                            form.build(last.form, now, finalOnly = true)
                        } else {
                            form.clear()
                            formLift = 0f
                            val fin = sheets[model.steps.size]
                            paper.build(fin, null, PaperPlay.STATIC, now)
                            if (last.kind == StepKind.DRAW) guides.build(fin, last, topOf(fin)) else guides.clear()
                        }
                    } else {
                        val step = state.currentStep
                        val sheet = sheets[state.step]
                        when (step.kind) {
                            StepKind.FORM -> {
                                paper.clear()
                                guides.clear()
                                formLift = FORM_LIFT_M
                                form.build(step.form!!, now, finalOnly = false)
                            }
                            StepKind.FOLD, StepKind.CREASE -> {
                                form.clear()
                                formLift = 0f
                                val play = if (state.ghost != null) PaperPlay.GHOST else if (step.kind == StepKind.FOLD) PaperPlay.FOLD else PaperPlay.CREASE
                                paper.build(sheet, step, play, now, state.ghost ?: 0f)
                                guides.build(sheet, step, topOf(sheet))
                            }
                            StepKind.FLIP -> {
                                form.clear()
                                formLift = 0f
                                paper.build(sheet, step, PaperPlay.FLIP, now)
                                guides.build(sheet, step, topOf(sheet))
                            }
                            StepKind.DRAW -> {
                                form.clear()
                                formLift = 0f
                                paper.build(sheet, null, PaperPlay.STATIC, now)
                                guides.build(sheet, step, topOf(sheet))
                            }
                        }
                    }
                    guides.setOpacity(1f)
                    FoldLog.i("SCENE step built model=${model.id} step=${state.step + 1} mode=${state.mode} ms=${SystemClock.elapsedRealtime() - t0}")
                } catch (t: Throwable) {
                    if (t !is CancellationException) FoldLog.e("SCENE step build failed: $t", t)
                }
            }
    }

    /** Height of the top layer of [sheet] over the desk. */
    private fun topOf(sheet: Sheet): Float {
        val minZ = sheet.facets.minOfOrNull { it.z } ?: 0
        val maxZ = sheet.facets.maxOfOrNull { it.z } ?: 0
        return PaperView.BASE_M + (maxZ - minZ) * PaperView.LAYER_M
    }

    /**
     * The guide panel once the demo sheet is down (design `spatialLayout.panel`): bottom centre at
     * (+110, +200) mm of the sheet (left hand: -110), 30 mm over the desk, leaning back 20 degrees,
     * turned towards the head of that moment.
     */
    private fun deskPanelPoseFor(demo: DemoPose, hand: Hand, headPos: Vec3): Pair<Vec3, Quat4> {
        val turn = Quat4.axisAngle(Vec3.UP, demo.yaw)
        val xAxis = turn.rotate(Vec3.RIGHT)
        val away = turn.rotate(Vec3.AHEAD)
        val side = if (hand == Hand.LEFT) -1f else 1f
        val bottom = demo.center + xAxis * (PANEL_SIDE_M * side) + away * PANEL_BACK_M + Vec3.UP * PANEL_RAISE_M
        val f = facingRule.facing(bottom, headPos, null)
        val facing = PanelFacing(f.yaw, PANEL_LEAN_RAD)
        val (_, hh) = PanelSpec.halfM(PanelSpec.GUIDE)
        return (bottom + facing.up * hh) to facing.rotation
    }

    // =========================================================================================
    // per frame
    // =========================================================================================

    fun onFrame(context: SceneUpdateContext) {
        val root = stageRoot ?: return
        if (root.scene == null || context.scene != root.scene) return
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs).coerceIn(0L, 100L)) / 1000f
        lastFrameMs = now
        headTracker.pose()?.let {
            head = it
            hasHead = true
        }
        val rig = rig ?: return
        rig.onFrame(now)
        rig.stowBehind(head.view)
        val state = ui
        val hold = panelPressed || (lastPanelPressMs != NEVER && now - lastPanelPressMs < PANEL_HOLD_MS) || state.menuOpen || state.settingsOpen
        if (hasHead && head.position.length > 1e-3f) follower.follow(head, planeTracker.all, planeTracker.count, now, hold)
        placePanels(state, rig)
        countFrame(now)
        if (!attached) return
        drainPlaneQueue()
        pollPlanes(now)
        pollBlocked(now)
        reticleFrame(state)
        demoFrame(state, now, dt)
        sampleController(state, now, dt)
        dwellFrame(state, now)
    }

    private fun drainPlaneQueue() {
        val c = colliders ?: return
        var n = 0
        val it = planeQueue.entries.iterator()
        while (it.hasNext() && n < COLLIDERS_PER_FRAME) {
            val (id, surface) = it.next()
            it.remove()
            if (surface != null) c.upsert(surface) else c.remove(id)
            n++
        }
    }

    /**
     * Look-to-advance: the head's forward ray against the guide panel, mapped to the panel's root pixels
     * and tested against the "Next" button's bounds ([DwellTarget]). Counting starts once the gaze has
     * been off the button since the step began and the demo has played (`dwellArmed`); 1 s on it fires.
     */
    private fun dwellFrame(state: FoldUiState, now: Long) {
        if (state.stepSerial != dwellSerial) {
            dwellSerial = state.stepSerial
            dwellLeftSinceStep = false
            dwellMs = 0f
            dwellStartMs = NEVER
        }
        val target = DwellTarget.bounds
        val onPanel = gazeOnGuidePanel()
        val inside =
            target != null && onPanel != null && state.mode == Mode.STEP &&
                abs(onPanel.first * target.rootW - target.cx) <= target.w / 2f && abs(onPanel.second * target.rootH - target.cy) <= target.h / 2f
        if (!inside) {
            dwellLeftSinceStep = true
            if (dwellMs > 0f) FoldLog.i("DWELL cleared at ${dwellMs.toInt()} ms")
            dwellMs = 0f
            dwellStartMs = NEVER
        } else if (state.dwellArmed && dwellLeftSinceStep) {
            // wall clock, not frame time: the idle emulator renders 1 - 4 frames a second
            if (dwellStartMs == NEVER) {
                dwellStartMs = now
                FoldLog.i("DWELL start serial=${state.stepSerial}")
            }
            dwellMs = (now - dwellStartMs).toFloat()
            if (dwellMs >= DWELL_MS) {
                FoldLog.i("DWELL fire serial=${state.stepSerial} after ${dwellMs.toInt()} ms")
                dwellMs = 0f
                dwellStartMs = NEVER
                dwellLeftSinceStep = false
                sink(FoldEvent.DwellFired(state.stepSerial))
            }
        }
        val q = ((dwellMs / DWELL_MS) * 20f).toInt() / 20f
        if (q != dwellProgressWritten) {
            dwellProgressWritten = q
            DwellTarget.progress.floatValue = q
        }
    }

    /** Where the gaze meets the guide panel, as shares of its width and height (0..1, top-left origin), or null. */
    private fun gazeOnGuidePanel(): Pair<Float, Float>? {
        if (!hasHead) return null
        val (pos, rot) = guidePose ?: return null
        val origin = head.position
        val dir = gazeOverride?.let { (it - origin).normalized() } ?: head.forward
        val n = rot.rotate(Vec3(0f, 0f, 1f))
        val denom = dir.dot(n)
        if (abs(denom) < 1e-5f) return null
        val t = (pos - origin).dot(n) / denom
        if (t <= 0f) return null
        val local = rot.conjugate().rotate(origin + dir * t - pos)
        val (hw, hh) = PanelSpec.halfM(PanelSpec.GUIDE)
        if (abs(local.x) > hw || abs(local.y) > hh) return null
        return (local.x / (2f * hw) + 0.5f) to (0.5f - local.y / (2f * hh))
    }

    /** Debug `aim <control>`: the turn (right, up; degrees) from the current gaze to a guide-panel control's centre. */
    fun aimLine(name: String): String {
        val (pos, rot) = guidePose ?: return "AIM $name: no panel pose"
        val b = FoldProbe.controlsOf(PanelSpec.GUIDE)[name] ?: return "AIM $name: no such control (debug probe off or not composed)"
        val size = FoldProbe.sizeOf(PanelSpec.GUIDE) ?: return "AIM $name: panel size unknown"
        val (hw, hh) = PanelSpec.halfM(PanelSpec.GUIDE)
        val x = (b.cx / size.first - 0.5f) * 2f * hw
        val y = (0.5f - b.cy / size.second) * 2f * hh
        val point = pos + rot.rotate(Vec3(x, y, 0f))
        val to = point - head.position
        val dist = to.length
        val f = head.forward
        val yawNow = Math.toDegrees(kotlin.math.atan2(-f.x.toDouble(), -f.z.toDouble()))
        val yawTo = Math.toDegrees(kotlin.math.atan2(-to.x.toDouble(), -to.z.toDouble()))
        var dYaw = yawTo - yawNow
        while (dYaw > 180) dYaw -= 360
        while (dYaw < -180) dYaw += 360
        val pitchNow = Math.toDegrees(kotlin.math.asin(f.y.coerceIn(-1f, 1f).toDouble()))
        val pitchTo = Math.toDegrees(kotlin.math.asin((to.y / dist).coerceIn(-1f, 1f).toDouble()))
        // atan2(-x, -z) grows when turning left, so a right turn is its negative
        return String.format(Locale.US, "AIM %s turnRight=%.1f turnUp=%.1f dist=%.2f point=%s", name, -dYaw, pitchTo - pitchNow, dist, FoldLog.pos(point))
    }

    /** The guide panel (lazy follow / planted on the desk / beside the demo) and the popups above it. */
    private fun placePanels(state: FoldUiState, rig: PanelRig) {
        if (!hasHead) return
        val onDesk = state.demo != null && state.mode != Mode.FINDING_DESK && state.mode != Mode.NO_DESK && state.mode != Mode.NO_PERMISSION && state.mode != Mode.LOADING && state.mode != Mode.WELCOME
        val pose =
            when {
                onDesk && deskPanelPose != null -> deskPanelPose!!
                state.mode == Mode.PLACE_DEMO && plantedPose != null -> plantedPose!!
                else -> follower.pose.position to follower.rotation
            }
        guidePose = pose
        val k = PanelSpec.SCALE
        rig[PanelSpec.GUIDE]?.place(pose.first, pose.second, k)
        val (hw, hh) = PanelSpec.halfM(PanelSpec.GUIDE, k)
        if (state.settingsOpen) {
            val (_, sh) = PanelSpec.halfM(PanelSpec.SETTINGS, k)
            rig[PanelSpec.SETTINGS]?.place(pose.first + pose.second.rotate(Vec3(0f, hh + POPUP_GAP_M + sh, 0.012f)), pose.second, k)
        }
        if (state.menuOpen) {
            val (mw, mh) = PanelSpec.halfM(PanelSpec.MENU, k)
            rig[PanelSpec.MENU]?.place(pose.first + pose.second.rotate(Vec3(hw - mw, hh + POPUP_GAP_M + mh, 0.012f)), pose.second, k)
        }
    }

    /** The reticle under the gaze while placing (frozen while the gaze is on the guide panel). */
    private fun reticleFrame(state: FoldUiState) {
        val reticle = reticle ?: return
        if (state.mode != Mode.PLACE_DEMO || !hasHead) {
            reticle.hide()
            return
        }
        val origin = head.position
        val dir = head.forward
        val planes = if (noPlanes) emptyList() else planeTracker.all
        val desks = deskRule.desks(planes)
        val hit = hits.nearest(desks, origin, dir, RETICLE_RANGE_M)
        // the gaze rests on the guide panel: the reticle keeps its last place (once it has one)
        if (reticle.shown && rayHitsGuidePanel(origin, dir)) return
        if (hit != null) {
            val yaw = deskRule.yawFacing(origin, hit.point)
            reticle.showFrame(state.model.pose, hit.point + Vec3.UP * 0.0005f, yaw)
            if (plantedPose == null && state.demo == null) {
                plantedPose = deskPanelPoseFor(DemoPose(hit.point, yaw), state.hand, origin)
                FoldLog.i("PANEL planted at S03 pos=${FoldLog.pos(plantedPose?.first)}")
            }
        } else {
            val any = hits.nearest(planes, origin, dir, RETICLE_RANGE_M)
            val at = (any?.point ?: (origin + dir * 0.8f)) + (origin - (any?.point ?: origin)).normalized() * 0.01f
            reticle.showCross(at, facingRule.facing(at, origin, null).rotation)
        }
    }

    private fun rayHitsGuidePanel(origin: Vec3, dir: Vec3): Boolean {
        val (pos, rot) = guidePose ?: return false
        val n = rot.rotate(Vec3(0f, 0f, 1f))
        val denom = dir.dot(n)
        if (abs(denom) < 1e-5f) return false
        val t = (pos - origin).dot(n) / denom
        if (t <= 0f) return false
        val p = origin + dir * t
        val local = rot.conjugate().rotate(p - pos)
        val (hw, hh) = PanelSpec.halfM(PanelSpec.GUIDE)
        return abs(local.x) <= hw && abs(local.y) <= hh
    }

    /** Demo motion: hinge angles, lift, flip roll, done spin, spin-back after a turn. */
    private fun demoFrame(state: FoldUiState, now: Long, dt: Float) {
        val demo = state.demo ?: return
        val paper = paper ?: return
        val lift = liftNode ?: return
        if (!(state.mode == Mode.STEP || state.mode == Mode.DONE)) return
        paper.animate(now)
        form?.animate(now)
        if (paper.finished) guides?.setOpacity(GUIDE_FADE) else guides?.setOpacity(1f)
        if (paper.playedOnce && playedSent != state.stepSerial) {
            playedSent = state.stepSerial
            sink(FoldEvent.DemoPlayed(state.stepSerial))
        }
        val y = paper.lift + formLift
        lift.setLocal(position = Vec3(0f, y, 0f), rotation = Quat4.axisAngle(Vec3(0f, 0f, 1f), paper.roll))
        if (state.mode == Mode.DONE) doneSpin += dt * (2f * PI.toFloat() / DONE_TURN_S)
        // spin back to the designed yaw two seconds after the last turn
        if (!dragging && spin != 0f && lastSpinInputMs != NEVER && now - lastSpinInputMs >= SPIN_BACK_AFTER_MS) {
            if (spinBackStartMs == NEVER) {
                spinBackStartMs = now
                spinBackFrom = spin
            }
            val q = ((now - spinBackStartMs) / SPIN_BACK_MS).coerceIn(0f, 1f)
            spin = spinBackFrom * (1f - Ease.inOutCubic(q))
            if (q >= 1f) {
                spin = 0f
                spinBackStartMs = NEVER
                lastSpinInputMs = NEVER
            }
        }
        paperRoot?.setLocal(rotation = Quat4.axisAngle(Vec3.UP, demo.yaw + spin + doneSpin))
    }

    /** Controller: presence (panel copy) and the thumbstick turning the demo sheet about its vertical axis. */
    private fun sampleController(state: FoldUiState, now: Long, dt: Float) {
        val sample = controller.sample()
        val active = sample != null
        if (active != controllerActive) {
            if (controllerChangedMs == NEVER) controllerChangedMs = now
            if (now - controllerChangedMs >= CONTROLLER_DEBOUNCE_MS) {
                controllerActive = active
                controllerChangedMs = NEVER
                FoldLog.i("TRACK controller active=$active")
                sink(FoldEvent.ControllerChanged(active))
            }
        } else {
            controllerChangedMs = NEVER
        }
        if (sample == null || state.demo == null || (state.mode != Mode.STEP && state.mode != Mode.DONE)) return
        val x = sample.stickX
        if (abs(x) > STICK_DEAD_ZONE) {
            val v = (abs(x) - STICK_DEAD_ZONE) / (1f - STICK_DEAD_ZONE)
            spin -= x * v * STICK_RATE_RAD_S * dt
            lastSpinInputMs = now
            spinBackStartMs = NEVER
        }
    }

    private fun pollPlanes(now: Long) {
        val count = planeTracker.count
        val desks = if (noPlanes) 0 else deskRule.desks(planeTracker.all).size
        if (count == lastPlaneCount && desks == lastDeskCount) return
        if (now - lastPlanesEmitMs < PLANES_EMIT_MS) return
        lastPlanesEmitMs = now
        lastPlaneCount = count
        lastDeskCount = desks
        FoldLog.i("PLANES n=$count desks=$desks")
        sink(FoldEvent.PlanesChanged(desks))
    }

    /** Spatial sensing blocked (no permission): reported once the reason has stood for two seconds. */
    private fun pollBlocked(now: Long) {
        val reason = headTracker.blockReason()?.takeIf { it != BlockReason.NOT_IN_FULL_SPACE }
        if (reason == null) {
            blockSinceMs = NEVER
            if (sentBlock != null) {
                sentBlock = null
                sink(FoldEvent.SensingBlocked(null))
            }
            return
        }
        if (blockSinceMs == NEVER) blockSinceMs = now
        if (now - blockSinceMs >= BLOCK_GRACE_MS && sentBlock != reason) {
            sentBlock = reason
            FoldLog.i("TRACK blocked reason=$reason")
            sink(FoldEvent.SensingBlocked(reason))
        }
    }

    private fun countFrame(now: Long) {
        if (!fpsOn) return
        frames++
        if (fpsStartMs == 0L) fpsStartMs = now
        if (now - fpsStartMs >= 1000L) {
            FoldLog.i("FPS app=${frames * 1000f / (now - fpsStartMs)} frames=$frames ms=${now - fpsStartMs}")
            frames = 0
            fpsStartMs = now
        }
    }

    // =========================================================================================
    // system pointer (gaze + pinch, emulator mouse)
    // =========================================================================================

    /** A system spatial tap on one of our entities. */
    fun onSpatialTap(value: SpatialTapValue) {
        val target = value.targetEntity
        val name = runCatching { target?.getName() }.getOrNull() ?: "none"
        val stage = toStage(value.position.x, value.position.y, value.position.z)
        val kind = value.interactionKind
        if (target == null) {
            FoldLog.i("TAP other target=none kind=$kind")
            return
        }
        val planeId = colliders?.planeIdOf(target)
        when {
            paperTap != null && target.id == paperTap!!.id -> FoldLog.i("TAP paper kind=$kind")
            planeId != null && stage != null -> {
                val plane = planeTracker.get(planeId)
                val desk = plane != null && !noPlanes && deskRule.isDesk(plane, deskRule.floorY(planeTracker.all))
                FoldLog.i("TAP surface plane=${name.removePrefix("plane:")} desk=$desk kind=$kind stage=${FoldLog.pos(stage)}")
                if (ui.mode == Mode.PLACE_DEMO && desk && plane != null) {
                    val point = hits.project(plane, stage)
                    sink(FoldEvent.DemoPlaceRequested(point, deskRule.yawFacing(head.position, point)))
                }
            }
            colliders?.isBackstop(target) == true -> FoldLog.i("TAP backstop kind=$kind stage=${FoldLog.pos(stage)}")
            else -> FoldLog.i("TAP other target=$name kind=$kind stage=${FoldLog.pos(stage)}")
        }
    }

    /** A system drag on the demo sheet turns it about its vertical axis (design T6); it turns back 2 s after release. */
    fun onSpatialDrag(phase: String, start: Offset3D?, value: SpatialDragValue) {
        val tap = paperTap ?: return
        when (phase) {
            "start" -> {
                val target = value.targetEntity ?: return
                if (target.id != tap.id) return
                if (ui.demo == null || (ui.mode != Mode.STEP && ui.mode != Mode.DONE)) return
                dragging = true
                spinBackStartMs = NEVER
                FoldLog.i("DRAG paper start")
            }
            "move" -> {
                if (!dragging) return
                spin += value.dragAmount.x * DRAG_RAD_PER_PX
            }
            "end", "cancel" -> {
                if (!dragging) return
                dragging = false
                lastSpinInputMs = SystemClock.elapsedRealtime()
                FoldLog.i("DRAG paper end spin=${FoldLog.f(Math.toDegrees(spin.toDouble()).toFloat(), 1)}")
            }
        }
        @Suppress("UNUSED_VARIABLE") val unused = start
    }

    /** View-local pixels of a spatial tap -> Stage metres, through the SpatialView content conversion. */
    private fun toStage(x: Float, y: Float, z: Float): Vec3? {
        val c = content ?: return null
        val probe = contentProbe ?: return null
        return runCatching {
                val local = c.convertPosition(Vector3(x, y, z), ViewCoordinateSpace.Local, c.localSpatialCoordinateSpace)
                probe.convertPositionTo(local, null).domain()
            }
            .onFailure { FoldLog.w("TAP convert failed: $it") }
            .getOrNull()
    }

    // =========================================================================================
    // debug (FoldProbe.SceneProbe)
    // =========================================================================================

    /**
     * Debug seed: the demo sheet on a desk in front of the viewer. Rays from the head, 10 - 60 degrees below
     * the view direction, are cast against the desk planes; the nearest hit (the steepest ray that still
     * lands on a desk) is taken, so the sheet sits on the near edge of whatever table or platform is ahead.
     * Without a hit: the nearest desk's point under the head's forward, else 0.6 m ahead, 0.45 m below the head.
     */
    fun seedDemo() {
        val desks = deskRule.desks(planeTracker.all)
        val flat = Vec3(head.forward.x, 0f, head.forward.z).let { if (it.length < 0.1f) Vec3.AHEAD else it.normalized() }
        var point: Vec3? = null
        var planeLabel = "none"
        var best = Float.MAX_VALUE
        var deg = 10
        while (deg <= 60) {
            val a = Math.toRadians(deg.toDouble())
            val dir = (flat * kotlin.math.cos(a).toFloat() + Vec3(0f, -kotlin.math.sin(a).toFloat(), 0f)).normalized()
            val hit = hits.nearest(desks, head.position, dir, 6f)
            if (hit != null && hit.distance < best) {
                best = hit.distance
                point = hit.point
                planeLabel = hit.plane.label
            }
            deg += 5
        }
        if (point == null) {
            val plane = desks.minByOrNull { Vec3(it.centroid.x - head.position.x, 0f, it.centroid.z - head.position.z).length }
            point =
                if (plane != null) {
                    planeLabel = plane.label
                    val projected = hits.project(plane, head.position + flat * 0.5f)
                    if (hits.contains(plane, projected, margin = -0.1f)) projected else plane.centroid
                } else {
                    head.position + flat * 0.6f + Vec3(0f, -0.45f, 0f)
                }
        }
        val yaw = deskRule.yawFacing(head.position, point)
        FoldLog.i("DEMO seed plane=$planeLabel point=${FoldLog.pos(point)} dist=${FoldLog.f(head.position.distanceTo(point))}")
        sink(FoldEvent.DemoPlaceRequested(point, yaw, force = true))
    }

    fun setNoPlanes(on: Boolean) {
        noPlanes = on
        lastDeskCount = -1
    }

    fun setSpin(degrees: Float) {
        spin = Math.toRadians(degrees.toDouble()).toFloat()
        lastSpinInputMs = NEVER
        spinBackStartMs = NEVER
    }

    override fun poseLine(): String {
        val h = head
        return "POSE hmd pos=${FoldLog.pos5(h.position)} ${FoldLog.quat5(h.rotation)} fwd=${FoldLog.pos5(h.forward)} valid=$hasHead"
    }

    override fun panelLines(): List<String> =
        rig?.all?.map { s ->
            "PANEL id=${s.id} shown=${s.shown} pos=${FoldLog.pos5(s.enginePosition())} ${FoldLog.quat5(s.engineRotation())} scale=${FoldLog.f(s.scale, 3)} size_dp=${s.widthDp.toInt()}x${s.heightDp.toInt()}"
        } ?: emptyList()

    override fun planesSummary(): List<String> {
        val floor = deskRule.floorY(planeTracker.all)
        return planeTracker.all.map { p ->
            "PLANE ${p.label} kind=${p.kind} desk=${deskRule.isDesk(p, floor)} center=${FoldLog.pos(p.centroid)} normal=${FoldLog.pos(p.normal)} verts=${p.vertices.size}"
        }
    }

    override fun parkPanel(): String {
        follower.park(head, planeTracker.all, SystemClock.elapsedRealtime())
        return "FOLLOW guide park"
    }

    override fun setFpsLine(on: Boolean): String {
        fpsOn = on
        frames = 0
        fpsStartMs = 0L
        return "FPS ${if (on) "on" else "off"}"
    }

    /** The demo sheet in Stage coordinates: pose, facets of the current sheet and this step's fold lines. */
    override fun sceneJson(): JSONObject {
        val o = JSONObject()
        val demo = ui.demo
        o.put("demo", demo?.let { JSONObject().put("x", it.center.x).put("y", it.center.y).put("z", it.center.z).put("yawDeg", Math.toDegrees(it.yaw.toDouble())) } ?: JSONObject.NULL)
        o.put("spinDeg", Math.toDegrees(spin.toDouble()))
        o.put("lift", (paper?.lift ?: 0f) + formLift)
        o.put("played", paper?.playedOnce ?: false)
        o.put("finished", paper?.finished ?: false)
        if (demo != null && (ui.mode == Mode.STEP || ui.mode == Mode.DONE)) {
            val turn = Quat4.axisAngle(Vec3.UP, demo.yaw + spin + doneSpin)
            fun stage(p: io.embodit.foldguide.domain.fold.Pt): JSONArray {
                val v = demo.center + turn.rotate(p.localVec(0f))
                return JSONArray().put(v.x).put(v.y).put(v.z)
            }
            val sheets = sheetsOf(ui.model)
            val sheet = if (ui.mode == Mode.DONE) sheets.last() else sheets[ui.step]
            val facets = JSONArray()
            for (f in sheet.facets) facets.put(JSONObject().put("z", f.z).put("up", f.up).put("pts", JSONArray().also { arr -> f.pts.forEach { arr.put(stage(it)) } }))
            o.put("facets", facets)
            val lines = JSONArray()
            if (ui.mode == Mode.STEP) for (l in ui.currentStep.lines) lines.put(JSONObject().put("a", stage(l.a)).put("b", stage(l.b)).put("mountain", l.mountain))
            o.put("lines", lines)
        }
        guidePose?.let { (p, q) -> o.put("guidePanel", JSONObject().put("x", p.x).put("y", p.y).put("z", p.z).put("qx", q.x).put("qy", q.y).put("qz", q.z).put("qw", q.w)) }
        return o
    }

    /** Head pose (for the debug receiver). */
    fun headPose(): HeadPose = head

    @Suppress("unused")
    private fun planesOf(): Collection<PlaneSurface> = planeTracker.all

    companion object {
        private const val NEVER = Long.MIN_VALUE
        private const val REBIND_DELAY_MS = 250L
        private const val PANEL_HOLD_MS = 600L
        private const val PLANES_EMIT_MS = 300L
        private const val BLOCK_GRACE_MS = 2000L
        private const val CONTROLLER_DEBOUNCE_MS = 800L
        private const val RETICLE_RANGE_M = 3f
        private const val PAPER_TAP_M = 0.24f
        private const val FORM_LIFT_M = 0.15f
        private const val GUIDE_FADE = 0.35f
        private const val DONE_TURN_S = 6f
        private const val SPIN_BACK_AFTER_MS = 2000L
        private const val SPIN_BACK_MS = 500f
        private const val DRAG_RAD_PER_PX = 0.0087f
        private const val STICK_DEAD_ZONE = 0.15f
        private const val STICK_RATE_RAD_S = 1.6f
        private const val POPUP_GAP_M = 0.012f
        private const val COLLIDERS_PER_FRAME = 3
        private const val DWELL_MS = 1000f

        /** Panel beside the sheet: 110 mm to the side, 200 mm away, 30 mm up, leaning back 20 degrees. */
        private const val PANEL_SIDE_M = 0.11f
        private const val PANEL_BACK_M = 0.20f
        private const val PANEL_RAISE_M = 0.03f
        private const val PANEL_LEAN_RAD = 0.349066f

        /** Lazy follow before the sheet is down: straight ahead, 15 degrees down, 0.6 m (design `panels[0]`). */
        private val GUIDE_OFFSET = Vec3(0f, -0.1553f, -0.5796f)
        private val GUIDE_HALF_EXTENTS = listOf(0.132f, 0.09f, 0.01f)
        private val GUIDE_LEAN: Quat4 = Quat4.IDENTITY
    }
}
