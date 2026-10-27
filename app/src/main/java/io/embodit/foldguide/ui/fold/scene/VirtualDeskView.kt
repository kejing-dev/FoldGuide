package io.embodit.foldguide.ui.fold.scene

import com.pico.spatial.core.ecs.Entity
import io.embodit.foldguide.domain.fold.Geometry
import io.embodit.foldguide.domain.fold.Pt
import kotlin.math.cos
import kotlin.math.sin

/**
 * The virtual desk top (v0.1.1, factory rule D7): when no real desk is found within about 5 s, the demo
 * sheet lies on this soft cream plate in front of the user instead of waiting for the scan. A rounded
 * rectangle around the sheet's start outline (model units, under the scaled paper root), with a faint rim.
 */
internal class VirtualDeskView(private val parent: Entity, private val materials: SceneMaterials) {
    private var entity: Entity? = null
    private val pieces = ArrayList<Piece>()
    private var builtKey = ""

    /** Shows the plate for [outline] (rebuilt only when the outline changes), or hides it. */
    fun show(key: String, outline: List<Pt>, visible: Boolean) {
        if (!visible) {
            entity?.enabled = false
            return
        }
        if (key != builtKey) {
            clear()
            builtKey = key
            val e = Entity().named("virtualDesk")
            parent.addChild(e)
            entity = e
            val b = Geometry.bounds(outline)
            val plate = roundedRect(b[0] - MARGIN, b[1] - MARGIN, b[2] + MARGIN, b[3] + MARGIN, RADIUS)
            val fill = MeshBuilder()
            fill.polygon(plate.map { it.local(PLATE_Y_M) }, MeshBuilder.UP)
            val rim = MeshBuilder()
            for (i in plate.indices) rim.band(plate[i].local(PLATE_Y_M + 0.0001f), plate[(i + 1) % plate.size].local(PLATE_Y_M + 0.0001f), RIM_M, MeshBuilder.UP)
            pieces += Piece(e, fill.build("virtual_desk"), materials.virtualDesk, "virtualDesk")
            pieces += Piece(e, rim.build("virtual_desk_rim"), materials.virtualDeskEdge, "virtualDeskRim")
        }
        entity?.enabled = true
    }

    fun clear() {
        pieces.forEach { it.destroy() }
        pieces.clear()
        runCatching { entity?.destroy(true) }
        entity = null
        builtKey = ""
    }

    private fun roundedRect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float): List<Pt> {
        val out = ArrayList<Pt>()
        val corners = listOf(Pt(x1 - r, y0 + r) to -90f, Pt(x1 - r, y1 - r) to 0f, Pt(x0 + r, y1 - r) to 90f, Pt(x0 + r, y0 + r) to 180f)
        for ((c, start) in corners) {
            for (k in 0..4) {
                val a = Math.toRadians((start + k * 22.5f).toDouble())
                out += Pt(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            }
        }
        return out
    }

    private companion object {
        /** Model units around the sheet. */
        const val MARGIN = 45f
        const val RADIUS = 24f
        const val PLATE_Y_M = 0.0001f
        const val RIM_M = 0.0015f
    }
}
