package io.embodit.foldguide.ui.fold.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import io.embodit.foldguide.domain.fold.Facet
import io.embodit.foldguide.domain.fold.FoldEngine
import io.embodit.foldguide.domain.fold.Geometry
import io.embodit.foldguide.domain.fold.Pt
import io.embodit.foldguide.domain.fold.Sheet
import io.embodit.foldguide.domain.fold.Split
import io.embodit.foldguide.domain.fold.Step
import io.embodit.foldguide.domain.fold.StepKind
import io.embodit.foldguide.domain.model.Quat4
import io.embodit.foldguide.domain.model.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.yield

/** What the demo sheet is doing (v0.1.1: CUT slides the strip off, ROTATE turns the square into a diamond). */
internal enum class PaperPlay { STATIC, FOLD, CREASE, FLIP, GHOST, CUT, ROTATE }

/**
 * The demo sheet (design `entities3d.demoPaper`): the facets of the current sheet as meshes under a
 * paper-local frame (x right, y up, z towards the user; model millimetres -> metres). The facets a
 * step turns hang under hinge entities placed on the fold line; the animation only rotates the hinges
 * (no mesh is rebuilt per frame). Two meshes per group: the rose faces and the paper-white faces.
 */
internal class PaperView(private val parent: Entity, private val materials: SceneMaterials) {

    private class Hinge(val entity: Entity, val axis: Vec3, val sign: Float, val pieces: List<Piece>, val base: Vec3, val away: Vec3)

    private var group: Entity? = null
    private var stayPieces: List<Piece> = emptyList()
    private var hinges: List<Hinge> = emptyList()
    private var play = PaperPlay.STATIC
    private var mountain = false
    private var startMs = 0L
    private var ghostShare = 0f
    private var halfWidthM = 0.08f

    /** True once the first loop of the current fold / crease has completed. */
    var playedOnce = false
        private set

    /** True when the demo has stopped (3 loops done): the guides fade. */
    var finished = false
        private set

    /** Height the whole sheet is lifted to (mountain folds: 40 mm; flips: the turning half width). */
    var lift = 0f
        private set

    /** Rotation about the sheet's front-back axis (flips). */
    var roll = 0f
        private set

    /** Turn about the vertical axis (rotate steps: 0 -> 45 degrees); the guides follow it. */
    var turn = 0f
        private set

    val built: Boolean
        get() = group != null

    fun clear() {
        stayPieces.forEach { it.destroy() }
        hinges.forEach { h -> h.pieces.forEach { it.destroy() } }
        runCatching { group?.destroy(true) }
        stayPieces = emptyList()
        hinges = emptyList()
        group = null
        playedOnce = false
        finished = false
        lift = 0f
        roll = 0f
        turn = 0f
    }

    fun setVisible(visible: Boolean) {
        group?.enabled = visible
    }

    /**
     * Builds the sheet for a step. [sheet] is the sheet at the step's start; for fold / crease steps
     * the moving facets go under hinges. Yields after every mesh (a mesh costs about a frame).
     */
    suspend fun build(sheet: Sheet, step: Step?, play: PaperPlay, nowMs: Long, ghost: Float = 0f) {
        clear()
        this.play = play
        this.ghostShare = ghost
        this.startMs = nowMs
        this.mountain = step?.mountain == true
        val g = Entity().named("paper")
        parent.addChild(g)
        group = g
        val b = Geometry.bounds(sheet.allPoints)
        halfWidthM = maxOf(abs(b[0]), abs(b[2])) / 1000f
        val minZ = sheet.facets.minOfOrNull { it.z } ?: 0
        fun heightOf(f: Facet) = BASE_M + (f.z - minZ) * LAYER_M
        val turning = step != null && step.marks && (play == PaperPlay.FOLD || play == PaperPlay.CREASE || play == PaperPlay.GHOST || play == PaperPlay.CUT)
        val parts = if (turning) FoldEngine.animParts(sheet, step!!) else null
        val stay = parts?.stay ?: sheet.facets
        stayPieces = facets(g, stay, ::heightOf, Pt.ZERO, 0f, "stay")
        yield()
        val hs = ArrayList<Hinge>()
        if (parts != null) {
            for ((i, sp) in parts.moves.withIndex()) {
                val h = hinge(g, sp, i, stay, ::heightOf)
                hs += h
                yield()
            }
        }
        hinges = hs
        if (play == PaperPlay.STATIC) {
            playedOnce = true
            finished = true
        }
        if (play == PaperPlay.GHOST) applyAngles(PI.toFloat() * ghostShare.coerceIn(0f, 1f), 1f)
    }

    /** Two meshes (rose faces, paper faces) for [facets], vertices relative to [pivot] at pivot height [pivotY]. */
    private fun facets(parent: Entity, facets: List<Facet>, heightOf: (Facet) -> Float, pivot: Pt, pivotY: Float, tag: String): List<Piece> {
        val rose = MeshBuilder()
        val paper = MeshBuilder()
        for (f in facets) {
            val y = heightOf(f) - pivotY
            val ccw = if (Geometry.area2(f.pts) >= 0f) f.pts else f.pts.reversed()
            val top = ccw.map { (it - pivot).local(y) }
            val bottom = ccw.map { (it - pivot).local(y - THICKNESS_M) }
            // the colour side faces up when f.up: top = rose, bottom = paper; else the other way round
            val topRose = f.up
            (if (topRose) rose else paper).polygon(top, MeshBuilder.UP)
            (if (topRose) paper else rose).polygon(bottom, MeshBuilder.DOWN, reverse = true)
        }
        val out = ArrayList<Piece>(2)
        if (!rose.isEmpty) out += Piece(parent, rose.build("paper_${tag}_rose"), materials.rose, "paper:$tag:rose")
        if (!paper.isEmpty) out += Piece(parent, paper.build("paper_${tag}_back"), materials.paper, "paper:$tag:back")
        return out
    }

    /**
     * A hinge on the fold line of [sp]: the entity sits on the line at a pivot height chosen so that the
     * turned flap lands just above (valley) or below (mountain) the facets that stay; its children are
     * the moving facets with vertices relative to the pivot.
     */
    private fun hinge(parent: Entity, sp: Split, index: Int, stay: List<Facet>, heightOf: (Facet) -> Float): Hinge {
        val a = sp.a
        val b = sp.b
        val dir = (b - a).normalized()
        val maxStay = stay.maxOfOrNull(heightOf) ?: BASE_M
        val minStay = stay.minOfOrNull(heightOf) ?: BASE_M
        val maxMove = sp.move.maxOf(heightOf)
        val minMove = sp.move.minOf(heightOf)
        val pivotY = if (sp.mountain) (minStay + minMove) / 2f - LAYER_M / 2f - THICKNESS_M else (maxStay + maxMove) / 2f + LAYER_M / 2f
        val e = Entity().named("hinge:$index")
        parent.addChild(e)
        e.setLocal(position = a.localVec(pivotY))
        val pieces = facets(e, sp.move, heightOf, a, pivotY, "move$index")
        // axis along the line; its sign chosen so that a positive angle lifts the moving side
        var axis = Vec3(dir.x, 0f, -dir.y)
        val pick = sp.move.first().pts.let { Geometry.centroid(it) }
        val foot = Geometry.foot(pick, a, b)
        val side = Vec3((pick.x - foot.x), 0f, -(pick.y - foot.y)).normalized()
        val up = axis.cross(side)
        if (up.y < 0f) axis = -axis
        return Hinge(e, axis, if (sp.mountain) -1f else 1f, pieces, a.localVec(pivotY), side)
    }

    /** Hinge angles and the sheet's lift for the moment [nowMs]. Returns true while something moves. */
    fun animate(nowMs: Long): Boolean {
        if (group == null) return false
        when (play) {
            PaperPlay.STATIC -> return false
            PaperPlay.GHOST -> {
                lift = if (mountain) MOUNTAIN_LIFT_M else 0f
                return false
            }
            PaperPlay.FLIP -> return animateFlip(nowMs)
            PaperPlay.CUT -> return animateCut(nowMs)
            PaperPlay.ROTATE -> return animateRotate(nowMs)
            PaperPlay.FOLD, PaperPlay.CREASE -> Unit
        }
        val t = nowMs - startMs
        val loop = (t / CYCLE_MS).toInt()
        val u = (t % CYCLE_MS).toFloat()
        if (loop >= 1) playedOnce = true
        val fold = play == PaperPlay.FOLD
        if (loop >= LOOPS) {
            finished = true
            applyAngles(if (fold) PI.toFloat() else 0f, 1f)
            lift = if (mountain) lerpLift(nowMs - (startMs + LOOPS * CYCLE_MS), down = true) else 0f
            return lift > 0f
        }
        val theta =
            when {
                u < PAUSE_MS -> 0f
                u < PAUSE_MS + FOLD_MS -> PI.toFloat() * Ease.inOutQuad((u - PAUSE_MS) / FOLD_MS)
                fold -> PI.toFloat()
                u < PAUSE_MS + FOLD_MS + HOLD_MS -> PI.toFloat()
                u < PAUSE_MS + FOLD_MS + HOLD_MS + UNFOLD_MS -> PI.toFloat() * (1f - (u - PAUSE_MS - FOLD_MS - HOLD_MS) / UNFOLD_MS)
                else -> 0f
            }
        applyAngles(theta, 1f)
        lift = if (mountain) (if (loop == 0) lerpLift(t, down = false) else MOUNTAIN_LIFT_M) else 0f
        return true
    }

    private fun lerpLift(sinceMs: Long, down: Boolean): Float {
        val q = (sinceMs / LIFT_MS).coerceIn(0f, 1f)
        val e = Ease.inOutCubic(q)
        return if (down) MOUNTAIN_LIFT_M * (1f - e) else MOUNTAIN_LIFT_M * e
    }

    private fun applyAngles(theta: Float, k: Float) {
        for (h in hinges) h.entity.setLocal(rotation = Quat4.axisAngle(h.axis, h.sign * theta * k))
    }

    /** Flip: turn about the front-back axis in 0.9 s, pause, twice; then stay turned over. */
    private fun animateFlip(nowMs: Long): Boolean {
        val t = nowMs - startMs
        val loop = (t / FLIP_CYCLE_MS).toInt()
        val u = (t % FLIP_CYCLE_MS).toFloat()
        if (loop >= 1) playedOnce = true
        val angle =
            if (loop >= FLIP_LOOPS) {
                finished = true
                PI.toFloat()
            } else if (u < PAUSE_MS) 0f
            else if (u < PAUSE_MS + FOLD_MS) PI.toFloat() * Ease.inOutQuad((u - PAUSE_MS) / FOLD_MS)
            else PI.toFloat()
        roll = angle
        lift = (halfWidthM + 0.004f) * abs(sin(angle))
        return !finished
    }

    /**
     * Cut: the strip slides 40 mm away from the cut line while it fades (pause 0.6 s, slide 0.9 s, hold
     * 0.4 s), three times; then it is gone.
     */
    private fun animateCut(nowMs: Long): Boolean {
        val t = nowMs - startMs
        val loop = (t / CYCLE_MS).toInt()
        val u = (t % CYCLE_MS).toFloat()
        if (loop >= 1) playedOnce = true
        val k =
            if (loop >= LOOPS) {
                finished = true
                1f
            } else if (u < PAUSE_MS) 0f
            else Ease.inOutQuad(((u - PAUSE_MS) / FOLD_MS).coerceIn(0f, 1f))
        for (h in hinges) {
            h.entity.setLocal(position = h.base + h.away * (CUT_SLIDE_M * k))
            h.entity.setOpacity(1f - k)
            h.entity.enabled = k < 0.99f
        }
        return !finished
    }

    /** Rotate: the whole sheet turns 45 degrees about its centre in 0.9 s, pause, twice; then it stays turned. */
    private fun animateRotate(nowMs: Long): Boolean {
        val t = nowMs - startMs
        val loop = (t / FLIP_CYCLE_MS).toInt()
        val u = (t % FLIP_CYCLE_MS).toFloat()
        if (loop >= 1) playedOnce = true
        val share =
            if (loop >= FLIP_LOOPS) {
                finished = true
                1f
            } else if (u < PAUSE_MS) 0f
            else Ease.inOutQuad(((u - PAUSE_MS) / FOLD_MS).coerceIn(0f, 1f))
        turn = QUARTER_PI * share
        group?.setLocal(rotation = Quat4.axisAngle(Vec3.UP, turn))
        return !finished
    }

    /** Current angle of the first hinge (debug `dump`). */
    fun angleShare(): Float {
        return if (play == PaperPlay.GHOST) ghostShare else -1f
    }

    companion object {
        /** Height of the lowest layer over the desk and the step per layer (paper 0.3 mm thick). */
        const val BASE_M = 0.0006f
        const val LAYER_M = 0.00035f
        const val THICKNESS_M = 0.0003f
        const val MOUNTAIN_LIFT_M = 0.04f

        /** Demo motion (design `spatialLayout.demo.motion`): pause 0.6 s, fold 0.9 s, hold 0.9 s; 3 loops. */
        const val PAUSE_MS = 600f
        const val FOLD_MS = 900f
        const val HOLD_MS = 400f
        const val UNFOLD_MS = 400f
        const val CYCLE_MS = 2700L
        const val LOOPS = 3
        const val LIFT_MS = 600f
        const val FLIP_CYCLE_MS = 2100L
        const val FLIP_LOOPS = 2
        const val CUT_SLIDE_M = 0.04f
        const val QUARTER_PI = (PI / 4).toFloat()
    }
}
