package io.embodit.foldguide.domain.fold

/**
 * One flat facet of the sheet: a convex polygon in model millimetres, its layer ([z], higher is nearer
 * the viewer, i.e. on top) and whether its colour side faces up ([up]; the sheet starts colour side down).
 */
data class Facet(val pts: List<Pt>, val z: Int, val up: Boolean)

/** The sheet at some moment: its facets and the creases left by earlier crease steps. */
data class Sheet(val facets: List<Facet>, val creases: List<FoldLine> = emptyList()) {
    val allPoints: List<Pt>
        get() = facets.flatMap { it.pts }
}

/** The pieces one fold line moves: the facets that turn about it (in their original place) and the rest. */
data class Split(val stay: List<Facet>, val move: List<Facet>, val a: Pt, val b: Pt, val mountain: Boolean)

/** Everything one step turns, per fold line, plus the facets that keep still (for the animation). */
data class AnimParts(val stay: List<Facet>, val moves: List<Split>)

/**
 * The 2D flat-fold engine of the design (`models.authoring`; the same rules as the mockup's
 * `folds.preview.js` and `tools/foldlib/engine.py`). Operations: fold-all, fold-top, fold a layer range,
 * flip, crease, cut and turn 45 degrees. Pure, unit-tested on the JVM.
 */
object FoldEngine {
    /** Half diagonal of the 150 mm square set as a diamond. */
    const val D = 106.1f

    /** Half of a corner-to-centre square side (the masu box after its four corners are folded). */
    const val H = 53.05f

    private const val C45 = 0.70710677f

    /** The outline of the 15 cm square in a start pose: edges towards the user, or a corner (printer paper: see FoldLibrary). */
    fun outline(pose: StartPose): List<Pt> =
        if (pose == StartPose.DIAMOND) listOf(Pt(0f, -D), Pt(D, 0f), Pt(0f, D), Pt(-D, 0f))
        else listOf(Pt(-75f, -75f), Pt(75f, -75f), Pt(75f, 75f), Pt(-75f, 75f))

    /** The flat sheet in its start pose: a square with edges towards the user, or a diamond with a corner. */
    fun start(pose: StartPose): Sheet = Sheet(listOf(Facet(outline(pose), 0, false)))

    /** The flat sheet of a model at its start (colour side down). */
    fun start(model: FoldModel): Sheet = Sheet(listOf(Facet(model.outline, 0, false)))

    /**
     * Splits the sheet by one line: the side of [pick] moves. [topOnly] moves only the topmost facet
     * under the pick point (the design's fold-top); [layers] moves only facets whose layer lies in the range
     * (a flap of several layers lying on the rest).
     */
    fun splitFor(sheet: Sheet, line: FoldLine, pick: Pt, topOnly: Boolean, layers: IntRange? = null): Split {
        val a = line.a
        val b = line.b
        val sgn = if (Geometry.side(a, b, pick) >= 0f) 1f else -1f
        var zTop = Int.MIN_VALUE
        if (topOnly) for (f in sheet.facets) if (Geometry.inside(pick, f.pts) && f.z > zTop) zTop = f.z
        val stay = ArrayList<Facet>()
        val move = ArrayList<Facet>()
        for (f in sheet.facets) {
            val mv = Geometry.clip(f.pts, a, b, sgn)
            val st = Geometry.clip(f.pts, a, b, -sgn)
            val eligible =
                when {
                    topOnly -> f.z == zTop && Geometry.inside(pick, f.pts)
                    layers != null -> f.z in layers
                    else -> true
                }
            if (mv != null && eligible) move += Facet(mv, f.z, f.up) else if (mv != null) stay += Facet(mv, f.z, f.up)
            if (st != null) stay += Facet(st, f.z, f.up)
        }
        return Split(stay, move, a, b, line.mountain)
    }

    /** The sheet after the moving facets have turned over the line (new layers, mirrored, colour side swapped). */
    fun applySplit(sp: Split): Sheet {
        val maxS = sp.stay.maxOfOrNull { it.z } ?: 0
        val minS = sp.stay.minOfOrNull { it.z } ?: 0
        val maxM = sp.move.maxOf { it.z }
        val minM = sp.move.minOf { it.z }
        val moved =
            sp.move.map { f ->
                val z = if (sp.mountain) minS - 1 - (f.z - minM) else maxS + 1 + (maxM - f.z)
                Facet(f.pts.map { Geometry.reflect(it, sp.a, sp.b) }.reversed(), z, !f.up)
            }
        return Sheet(sp.stay + moved)
    }

    /** Folds every line of a step in order (a step's lines turn regions that do not overlap). */
    fun fold(sheet: Sheet, step: Step): Sheet {
        var cur = sheet
        step.lines.forEachIndexed { i, line ->
            val sp = splitFor(cur, line, step.pickAt(i), step.topOnly, step.layersAt(i))
            if (sp.move.isEmpty()) return@forEachIndexed
            cur = applySplit(sp).copy(creases = cur.creases)
        }
        return cur
    }

    /** Turned over left to right (design: flip = mirror x, layers reversed, colour sides swapped). */
    fun flip(sheet: Sheet): Sheet =
        Sheet(
            sheet.facets.map { f -> Facet(f.pts.map { Pt(-it.x, it.y) }.reversed(), -f.z, !f.up) },
            sheet.creases.map { FoldLine(Pt(-it.a.x, it.a.y), Pt(-it.b.x, it.b.y), it.mountain) },
        )

    /** Cut along the step's line: the side of the pick goes away, and so do creases lying on that side or on the line. */
    fun cut(sheet: Sheet, step: Step): Sheet {
        val line = step.lines.first()
        val pick = step.pickAt(0)
        val sp = splitFor(sheet, line, pick, topOnly = false)
        val sgn = if (Geometry.side(line.a, line.b, pick) >= 0f) 1f else -1f
        fun gone(p: Pt) = Geometry.side(line.a, line.b, p) * sgn > -1e-4f
        return Sheet(sp.stay, sheet.creases.filterNot { gone(it.a) && gone(it.b) })
    }

    /** Turned 45 degrees counter-clockwise (seen from above) about the centre: the square becomes a diamond. */
    fun rotate45(sheet: Sheet): Sheet {
        fun r(p: Pt) = Pt(p.x * C45 - p.y * C45, p.x * C45 + p.y * C45)
        return Sheet(sheet.facets.map { f -> Facet(f.pts.map(::r), f.z, f.up) }, sheet.creases.map { FoldLine(r(it.a), r(it.b), it.mountain) })
    }

    /** The sheet after one step. */
    fun apply(sheet: Sheet, s: Step): Sheet {
        val out =
            when (s.kind) {
                StepKind.FOLD -> fold(sheet, s).copy(creases = sheet.creases + if (s.leavesCrease) s.lines else emptyList())
                StepKind.CREASE -> sheet.copy(creases = sheet.creases + s.lines)
                StepKind.FLIP -> flip(sheet)
                StepKind.CUT -> cut(sheet, s)
                StepKind.ROTATE -> rotate45(sheet)
                StepKind.DRAW, StepKind.FORM -> sheet
            }
        val reset = s.resetTo ?: return out
        return Sheet(listOf(Facet(outline(reset), 0, false)), out.creases)
    }

    /** The sheet at the start of every step of [model], plus the finished sheet at index `steps.size`. */
    fun build(model: FoldModel): List<Sheet> {
        var sheet = start(model)
        val out = ArrayList<Sheet>(model.steps.size + 1)
        for (s in model.steps) {
            out += sheet
            sheet = apply(sheet, s)
        }
        out += sheet
        return out
    }

    /** The parts of [sheet] that a fold or crease step moves, per line, with the facets that keep still. */
    fun animParts(sheet: Sheet, step: Step): AnimParts {
        var list = sheet.facets
        val moves = ArrayList<Split>()
        step.lines.forEachIndexed { i, line ->
            val sp = splitFor(Sheet(list), line, step.pickAt(i), step.topOnly, step.layersAt(i))
            if (sp.move.isNotEmpty()) moves += sp
            list = sp.stay
        }
        return AnimParts(list, moves)
    }

    /** The moving facets of a step in their original place (the amber tint). */
    fun movingParts(sheet: Sheet, step: Step): List<Facet> = animParts(sheet, step).moves.flatMap { it.move }

    /** Largest distance between a landing and the nearest vertex of the turned parts (tests: <= 0.5 mm). */
    fun landingError(sheet: Sheet, step: Step, landing: Landing): Float {
        val parts = animParts(sheet, step)
        val moved = parts.moves.flatMap { sp -> sp.move.flatMap { f -> f.pts.map { Geometry.reflect(it, sp.a, sp.b) } } }
        return when (landing) {
            is Landing.At -> moved.minOf { it.distanceTo(landing.p) }
            is Landing.Seg -> maxOf(moved.minOf { it.distanceTo(landing.a) }, moved.minOf { it.distanceTo(landing.b) })
        }
    }
}
