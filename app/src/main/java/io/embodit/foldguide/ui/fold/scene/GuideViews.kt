package io.embodit.foldguide.ui.fold.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import io.embodit.foldguide.domain.fold.FoldEngine
import io.embodit.foldguide.domain.fold.FoldLine
import io.embodit.foldguide.domain.fold.Geometry
import io.embodit.foldguide.domain.fold.Landing
import io.embodit.foldguide.domain.fold.Pt
import io.embodit.foldguide.domain.fold.Sheet
import io.embodit.foldguide.domain.fold.Step
import io.embodit.foldguide.domain.fold.StepKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.yield

/**
 * The guides drawn on the demo sheet for one step (design `spatialLayout.demo.guides`): fold lines
 * (white core 1.2 mm over a graphite halo, dashed for valleys, dash-dot for mountains), the moving
 * flap (amber 22 % + 0.6 mm edge), arched arrows (amber), landings (jade ring / segment), earlier
 * creases (white 30 %) and the draw dots. One mesh per appearance, built with a yield in between.
 */
internal class GuideViews(private val parent: Entity, private val materials: SceneMaterials) {
    private var group: Entity? = null
    private val pieces = ArrayList<Piece>()
    private var opacity = 1f

    fun clear() {
        pieces.forEach { it.destroy() }
        pieces.clear()
        runCatching { group?.destroy(true) }
        group = null
        opacity = 1f
    }

    fun setVisible(visible: Boolean) {
        group?.enabled = visible
    }

    /** Fades the whole set (1 while the demo runs, 0.35 once it has stopped). */
    fun setOpacity(value: Float) {
        if (value == opacity) return
        opacity = value
        group?.setOpacity(value)
    }

    suspend fun build(sheet: Sheet, step: Step, topY: Float) {
        clear()
        val g = Entity().named("guides")
        parent.addChild(g)
        group = g
        val y = topY + 0.0004f
        if (step.turns) {
            // the moving flap: tint + edge
            val tint = MeshBuilder()
            val edge = MeshBuilder()
            for (f in FoldEngine.movingParts(sheet, step)) {
                val ccw = if (Geometry.area2(f.pts) >= 0f) f.pts else f.pts.reversed()
                tint.polygon(ccw.map { it.local(y) }, MeshBuilder.UP)
                for (i in ccw.indices) edge.band(ccw[i].local(y + 0.0001f), ccw[(i + 1) % ccw.size].local(y + 0.0001f), FLAP_EDGE_M, MeshBuilder.UP)
            }
            add(g, tint, materials.amberTint, "flapTint")
            add(g, edge, materials.amber, "flapEdge")
            yield()
            // arrows: from the pick to its mirror image, arched 28 % of the chord up into the air
            val arrows = MeshBuilder()
            step.lines.forEachIndexed { i, line ->
                val from = step.pickAt(i)
                val to = Geometry.reflect(from, line.a, line.b)
                arrow(arrows, from, to, y + 0.0006f)
            }
            add(g, arrows, materials.amber, "arrows")
            yield()
            // fold lines: halo under the core
            val halo = MeshBuilder()
            val core = MeshBuilder()
            for (line in step.lines) dashed(line, y + 0.0008f, halo, core)
            add(g, halo, materials.graphite, "foldHalo")
            add(g, core, materials.foldWhite, "foldCore")
            yield()
            // landings
            val landing = MeshBuilder()
            for (l in step.landings) {
                when (l) {
                    is Landing.At -> landing.ring(l.p.x / 1000f, y + 0.0006f, -l.p.y / 1000f, LANDING_R_M - LANDING_W_M, LANDING_R_M, segments = 28)
                    is Landing.Seg -> landing.band(l.a.local(y + 0.0006f), l.b.local(y + 0.0006f), LANDING_SEG_M, MeshBuilder.UP)
                }
            }
            add(g, landing, materials.jade, "landing")
            yield()
        }
        // earlier creases (only ones not drawn as this step's line)
        val creases = MeshBuilder()
        for (c in sheet.creases) if (step.lines.none { same(it, c) }) creases.band(c.a.local(y + 0.0002f), c.b.local(y + 0.0002f), CREASE_M, MeshBuilder.UP)
        add(g, creases, materials.crease, "creases")
        if (step.kind == StepKind.DRAW) {
            yield()
            val dots = MeshBuilder()
            val rims = MeshBuilder()
            for (d in step.dots) {
                dots.disc(d.x / 1000f, y + 0.0006f, -d.y / 1000f, DOT_R_M, segments = 20)
                rims.ring(d.x / 1000f, y + 0.0007f, -d.y / 1000f, DOT_R_M, DOT_R_M + 0.0008f, segments = 20)
            }
            add(g, dots, materials.foldWhite, "dots")
            add(g, rims, materials.graphite, "dotRims")
        }
        g.setOpacity(opacity)
    }

    private fun add(parent: Entity, b: MeshBuilder, material: com.pico.spatial.core.ecs.resource.Material, name: String) {
        if (b.isEmpty) return
        pieces += Piece(parent, b.build("guide_$name"), material, "guide:$name")
    }

    private fun same(a: FoldLine, b: FoldLine): Boolean =
        (a.a.distanceTo(b.a) < 0.5f && a.b.distanceTo(b.b) < 0.5f) || (a.a.distanceTo(b.b) < 0.5f && a.b.distanceTo(b.a) < 0.5f)

    /** Valley: 6 / 4 mm dashes; mountain: 6 / 1.6 / 1.4 / 1.6 dash-dot. */
    private fun dashed(line: FoldLine, y: Float, halo: MeshBuilder, core: MeshBuilder) {
        val pattern = if (line.mountain) floatArrayOf(6f, 1.6f, 1.4f, 1.6f) else floatArrayOf(6f, 4f)
        val total = line.a.distanceTo(line.b)
        val dir = (line.b - line.a).normalized()
        var s = 0f
        var k = 0
        while (s < total) {
            val len = minOf(pattern[k % pattern.size], total - s)
            if (k % 2 == 0) {
                val p = line.a + dir * s
                val q = line.a + dir * (s + len)
                halo.band(p.local(y), q.local(y), LINE_HALO_M, MeshBuilder.UP)
                core.band(p.local(y + 0.0001f), q.local(y + 0.0001f), LINE_CORE_M, MeshBuilder.UP)
            }
            s += len
            k++
        }
    }

    /** An arched arrow from [from] to [to] (quadratic arc lifted by 28 % of the chord) with a two-stroke head. */
    private fun arrow(b: MeshBuilder, from: Pt, to: Pt, y: Float) {
        val chord = from.distanceTo(to)
        if (chord < 1f) return
        val rise = chord * 0.28f / 1000f
        val n = 14
        var prev: Vector3? = null
        var prevPrev: Vector3? = null
        for (i in 0..n) {
            val t = i.toFloat() / n
            val p = from + (to - from) * t
            val h = y + 4f * rise * t * (1f - t)
            val cur = p.local(h)
            if (prev != null) b.band(prev, cur, ARROW_M, arrowNormal(from, to))
            prevPrev = prev
            prev = cur
        }
        // head: two short strokes back from the tip, in the plane of the sheet
        val tip = to.local(y)
        val back = (from - to).normalized()
        val ang = atan2(back.y, back.x)
        for (sgn in floatArrayOf(-1f, 1f)) {
            val a = ang + sgn * 0.5f
            val q = Pt(to.x + cos(a) * ARROW_HEAD_MM, to.y + sin(a) * ARROW_HEAD_MM)
            b.band(tip, q.local(y + 0.0001f), ARROW_M, MeshBuilder.UP)
        }
        @Suppress("UNUSED_VARIABLE") val unused = prevPrev
    }

    /** Normal of the vertical plane that carries the arc (perpendicular to the chord, horizontal). */
    private fun arrowNormal(from: Pt, to: Pt): Vector3 {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val l = hypot(dx, dy)
        // chord in local space: (dx, 0, -dy); a horizontal perpendicular: (dy, 0, dx)
        return Vector3(dy / l, 0f, dx / l)
    }

    companion object {
        const val LINE_CORE_M = 0.0012f
        const val LINE_HALO_M = 0.0022f
        const val FLAP_EDGE_M = 0.0006f
        const val ARROW_M = 0.0014f
        const val ARROW_HEAD_MM = 5f
        const val LANDING_R_M = 0.004f
        const val LANDING_W_M = 0.0013f
        const val LANDING_SEG_M = 0.0011f
        const val CREASE_M = 0.0004f
        const val DOT_R_M = 0.0026f
    }
}
