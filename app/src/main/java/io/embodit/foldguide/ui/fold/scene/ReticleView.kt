package io.embodit.foldguide.ui.fold.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import io.embodit.foldguide.domain.fold.FoldEngine
import io.embodit.foldguide.domain.fold.Pt
import io.embodit.foldguide.domain.fold.StartPose
import io.embodit.foldguide.domain.model.Quat4
import io.embodit.foldguide.domain.model.Vec3

/**
 * The placement reticle (design `entities3d.reticle`): a 15 cm dashed outline in the model's start
 * pose (square or diamond), rose 25 % inside, amber dashes, gliding over the desk under the gaze;
 * a coral cross where the gaze is not on a desk. Built once; shown and moved per frame.
 */
internal class ReticleView(private val parent: Entity, private val materials: SceneMaterials) {
    private var square: Entity? = null
    private var diamond: Entity? = null
    private var cross: Entity? = null
    private val pieces = ArrayList<Piece>()
    private var shownPose: StartPose? = null
    private var crossShown = false

    /** True while a frame or the cross is on screen. */
    val shown: Boolean
        get() = shownPose != null || crossShown

    fun prepare() {
        square = frame(StartPose.SQUARE)
        diamond = frame(StartPose.DIAMOND)
        cross = buildCross()
        square?.enabled = false
        diamond?.enabled = false
        cross?.enabled = false
    }

    private fun frame(pose: StartPose): Entity {
        val e = Entity().named("reticle:${pose.name.lowercase()}")
        parent.addChild(e)
        val sheet = FoldEngine.start(pose).facets.first().pts
        val fill = MeshBuilder()
        fill.polygon(sheet.map { it.local(0.0008f) }, MeshBuilder.UP)
        val dash = MeshBuilder()
        for (i in sheet.indices) dashedEdge(dash, sheet[i], sheet[(i + 1) % sheet.size])
        pieces += Piece(e, fill.build("reticle_${pose.name}_fill"), materials.roseGhost, "reticleFill")
        pieces += Piece(e, dash.build("reticle_${pose.name}_dash"), materials.amberDash, "reticleDash")
        return e
    }

    private fun dashedEdge(b: MeshBuilder, a: Pt, c: Pt) {
        val total = a.distanceTo(c)
        val dir = (c - a).normalized()
        var s = 0f
        while (s < total) {
            val len = minOf(6f, total - s)
            b.band((a + dir * s).local(0.001f), (a + dir * (s + len)).local(0.001f), 0.0013f, MeshBuilder.UP)
            s += 10f
        }
    }

    private fun buildCross(): Entity {
        val e = Entity().named("reticle:cross")
        parent.addChild(e)
        val b = MeshBuilder()
        val r = 0.012f
        val up = Vector3(0f, 0f, 1f)
        b.band(Vector3(-r, -r, 0f), Vector3(r, r, 0f), 0.003f, up)
        b.band(Vector3(-r, r, 0f), Vector3(r, -r, 0f), 0.003f, up)
        pieces += Piece(e, b.build("reticle_cross"), materials.coral, "reticleCross")
        return e
    }

    /** Shows the frame of [pose] at [center] with [yaw] (desk plane). */
    fun showFrame(pose: StartPose, center: Vec3, yaw: Float) {
        val e = if (pose == StartPose.SQUARE) square else diamond
        val other = if (pose == StartPose.SQUARE) diamond else square
        if (shownPose != pose) {
            other?.enabled = false
            e?.enabled = true
            shownPose = pose
        }
        if (crossShown) {
            cross?.enabled = false
            crossShown = false
        }
        e?.setLocal(position = center, rotation = Quat4.axisAngle(Vec3.UP, yaw))
    }

    /** Shows the cross at [at], facing the viewer. */
    fun showCross(at: Vec3, facing: Quat4) {
        if (shownPose != null) {
            square?.enabled = false
            diamond?.enabled = false
            shownPose = null
        }
        if (!crossShown) {
            cross?.enabled = true
            crossShown = true
        }
        cross?.setLocal(position = at, rotation = facing)
    }

    fun hide() {
        if (shownPose != null) {
            square?.enabled = false
            diamond?.enabled = false
            shownPose = null
        }
        if (crossShown) {
            cross?.enabled = false
            crossShown = false
        }
    }

    fun destroy() {
        pieces.forEach { it.destroy() }
        pieces.clear()
        runCatching { square?.destroy(true) }
        runCatching { diamond?.destroy(true) }
        runCatching { cross?.destroy(true) }
        square = null
        diamond = null
        cross = null
    }
}
