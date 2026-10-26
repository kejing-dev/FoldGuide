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
 * `folds.preview.js`). Four operations: fold-all, fold-top, flip and crease. Pure, unit-tested on the JVM.
 */
object FoldEngine {
    /** Half diagonal of the 150 mm square set as a diamond. */
    const val D = 106.1f

    /** Half of a corner-to-centre square side (the masu box after its four corners are folded). */
    const val H = 53.05f

    /** The flat sheet in its start pose: a square with edges towards the user, or a diamond with a corner. */
    fun start(pose: StartPose): Sheet {
        val pts =
            if (pose == StartPose.DIAMOND) listOf(Pt(0f, -D), Pt(D, 0f), Pt(0f, D), Pt(-D, 0f))
            else listOf(Pt(-75f, -75f), Pt(75f, -75f), Pt(75f, 75f), Pt(-75f, 75f))
        return Sheet(listOf(Facet(pts, 0, false)))
    }

    /**
     * Splits the sheet by one line: the side of [pick] moves. [topOnly] moves only the topmost facet
     * under the pick point (the design's fold-top).
     */
    fun splitFor(sheet: Sheet, line: FoldLine, pick: Pt, topOnly: Boolean): Split {
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
            val eligible = !topOnly || (f.z == zTop && Geometry.inside(pick, f.pts))
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
            val sp = splitFor(cur, line, step.pickAt(i), step.topOnly)
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

    /** The sheet at the start of every step of [model], plus the finished sheet at index `steps.size`. */
    fun build(model: FoldModel): List<Sheet> {
        var sheet = start(model.pose)
        val out = ArrayList<Sheet>(model.steps.size + 1)
        for (s in model.steps) {
            out += sheet
            sheet =
                when (s.kind) {
                    StepKind.FOLD -> fold(sheet, s).copy(creases = sheet.creases)
                    StepKind.CREASE -> sheet.copy(creases = sheet.creases + s.lines)
                    StepKind.FLIP -> flip(sheet)
                    StepKind.DRAW, StepKind.FORM -> sheet
                }
        }
        out += sheet
        return out
    }

    /** The parts of [sheet] that a fold or crease step moves, per line, with the facets that keep still. */
    fun animParts(sheet: Sheet, step: Step): AnimParts {
        var list = sheet.facets
        val moves = ArrayList<Split>()
        step.lines.forEachIndexed { i, line ->
            val sp = splitFor(Sheet(list), line, step.pickAt(i), step.topOnly)
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
