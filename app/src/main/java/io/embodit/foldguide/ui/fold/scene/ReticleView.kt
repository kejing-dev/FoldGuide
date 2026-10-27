package io.embodit.foldguide.ui.fold.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import io.embodit.foldguide.domain.fold.Pt
import io.embodit.foldguide.domain.model.Quat4
import io.embodit.foldguide.domain.model.Vec3

/**
 * The placement reticle (design `entities3d.reticle`): a life-size dashed outline of the sheet in the
 * model's start pose (square, diamond, A4 / Letter upright), mist pink inside, blue dashes, gliding over
 * the desk under the gaze; a coral cross where the gaze is not on a desk. One frame per outline, built
 * the first time it is needed; shown and moved per frame.
 */
internal class ReticleView(private val parent: Entity, private val materials: SceneMaterials) {
    private val frames = HashMap<String, Entity>()
    private var cross: Entity? = null
    private val pieces = ArrayList<Piece>()
    private var shownKey: String? = null
    private var crossShown = false

    /** True while a frame or the cross is on screen. */
    val shown: Boolean
        get() = shownKey != null || crossShown

    fun prepare() {
        cross = buildCross()
        cross?.enabled = false
    }

    /** A frame for [outline] (model units), scaled to the real sheet by [scale]. */
    private fun frame(key: String, outline: List<Pt>, scale: Float): Entity {
        val e = Entity().named("reticle:$key")
        parent.addChild(e)
        val fill = MeshBuilder()
        fill.polygon(outline.map { it.local(0.0008f) }, MeshBuilder.UP)
        val dash = MeshBuilder()
        for (i in outline.indices) dashedEdge(dash, outline[i], outline[(i + 1) % outline.size])
        pieces += Piece(e, fill.build("reticle_${key}_fill"), materials.roseGhost, "reticleFill")
        pieces += Piece(e, dash.build("reticle_${key}_dash"), materials.amberDash, "reticleDash")
        e.setUniformScale(scale)
        e.enabled = false
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

    /** Shows the frame [key] ([outline] in model units times [scale]) at [center] with [yaw] (desk plane). */
    fun showFrame(key: String, outline: List<Pt>, scale: Float, center: Vec3, yaw: Float) {
        val e = frames.getOrPut(key) { frame(key, outline, scale) }
        if (shownKey != key) {
            shownKey?.let { frames[it]?.enabled = false }
            e.enabled = true
            shownKey = key
        }
        if (crossShown) {
            cross?.enabled = false
            crossShown = false
        }
        e.setLocal(position = center, rotation = Quat4.axisAngle(Vec3.UP, yaw))
    }

    /** Shows the cross at [at], facing the viewer. */
    fun showCross(at: Vec3, facing: Quat4) {
        hideFrame()
        if (!crossShown) {
            cross?.enabled = true
            crossShown = true
        }
        cross?.setLocal(position = at, rotation = facing)
    }

    private fun hideFrame() {
        shownKey?.let { frames[it]?.enabled = false }
        shownKey = null
    }

    fun hide() {
        hideFrame()
        if (crossShown) {
            cross?.enabled = false
            crossShown = false
        }
    }

    fun destroy() {
        pieces.forEach { it.destroy() }
        pieces.clear()
        frames.values.forEach { runCatching { it.destroy(true) } }
        frames.clear()
        runCatching { cross?.destroy(true) }
        cross = null
        shownKey = null
    }
}
