package io.embodit.foldguide.ui.fold.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import io.embodit.foldguide.domain.fold.FoldEngine
import io.embodit.foldguide.domain.fold.FormKind
import io.embodit.foldguide.domain.model.Quat4
import io.embodit.foldguide.domain.model.Vec3
import kotlin.math.PI
import kotlinx.coroutines.yield

/**
 * The form steps (design `models.formAnimation`): parametric hinge models. Cup: a front and a back
 * trapezoid hinged on the base crease, opening apart (3 keyframes). Masu box: the base square, four
 * walls hinged on its edges, the two end points hinged on their walls and tucking into the box
 * (3 - 4 keyframes per step). A keyframe is a set of hinge angles in degrees; the view tweens from one
 * keyframe to the next in 0.25 s and holds each 0.9 s, looping.
 */
internal class FormView(private val parent: Entity, private val materials: SceneMaterials) {

    /** A hinge: its entity, the fixed rotation of its frame and the axis the keyframe angle turns about. */
    private class Joint(val entity: Entity, val base: Quat4, val axis: Vec3)

    private var group: Entity? = null
    private val pieces = ArrayList<Piece>()
    private var joints: List<Joint> = emptyList()
    private var keyframes: List<FloatArray> = emptyList()
    private var startMs = 0L
    private var finalOnly = false

    val built: Boolean
        get() = group != null

    fun clear() {
        pieces.forEach { it.destroy() }
        pieces.clear()
        runCatching { group?.destroy(true) }
        group = null
        joints = emptyList()
        keyframes = emptyList()
    }

    fun setVisible(visible: Boolean) {
        group?.enabled = visible
    }

    /** Builds the hinge model of [kind]; [finalOnly] shows the last keyframe without moving (S12). */
    suspend fun build(kind: FormKind, nowMs: Long, finalOnly: Boolean) {
        clear()
        this.finalOnly = finalOnly
        startMs = nowMs
        val g = Entity().named("form")
        parent.addChild(g)
        group = g
        when (kind) {
            FormKind.CUP_OPEN -> buildCup(g)
            FormKind.MASU_WALLS, FormKind.MASU_END1, FormKind.MASU_END2 -> buildMasu(g, kind)
        }
        apply(if (finalOnly) keyframes.last() else keyframes.first())
    }

    /** A two-sided plate under [e]: rose on the side [outward] points to, paper white on the other. */
    private fun plate(e: Entity, pts: List<Vector3>, outward: Vector3, tag: String) {
        val rose = MeshBuilder()
        val paper = MeshBuilder()
        // polygon() takes counter-clockwise points for the given normal; the caller lists the points so
        // that they are counter-clockwise seen from the outward side
        rose.polygon(pts, outward)
        paper.polygon(pts, outward, reverse = true)
        pieces += Piece(e, rose.build("form_${tag}_rose"), materials.rose, "form:$tag:rose")
        pieces += Piece(e, paper.build("form_${tag}_back"), materials.paper, "form:$tag:back")
    }

    // ---- cup: two trapezoids on the base crease (mouth 88 mm wide, 62 mm high; design `models.list[2]`)

    private suspend fun buildCup(g: Entity) {
        val half = FoldEngine.D / 1000f
        val mouthHalf = 0.044f
        val height = 0.0621f
        val front = Entity().named("cup:front")
        val back = Entity().named("cup:back")
        g.addChild(front)
        g.addChild(back)
        // front plate: outward = +z (towards the user); points counter-clockwise seen from +z
        plate(front, listOf(Vector3(-half, 0f, 0f), Vector3(half, 0f, 0f), Vector3(mouthHalf, height, 0f), Vector3(-mouthHalf, height, 0f)), Vector3(0f, 0f, 1f), "cupFront")
        yield()
        // back plate: outward = -z; counter-clockwise seen from -z is the reverse order
        plate(back, listOf(Vector3(-mouthHalf, height, 0f), Vector3(mouthHalf, height, 0f), Vector3(half, 0f, 0f), Vector3(-half, 0f, 0f)), Vector3(0f, 0f, -1f), "cupBack")
        yield()
        joints = listOf(Joint(front, Quat4.IDENTITY, Vec3.RIGHT), Joint(back, Quat4.IDENTITY, Vec3.RIGHT))
        // the front leans towards the user (+x turn), the back away: closed -> ajar -> open
        keyframes = listOf(floatArrayOf(2f, -2f), floatArrayOf(13f, -13f), floatArrayOf(27f, -27f))
    }

    // ---- masu: base + 4 walls + 2 end points (design `models.list[3]`, box 53 x 53 x 26.5 mm)

    private suspend fun buildMasu(g: Entity, kind: FormKind) {
        val s = FoldEngine.H / 1000f // half side of the base
        val w = 0.0265f // wall height
        val tip = FoldEngine.D / 1000f - s // the end point beyond its wall
        // base: colour side down (rose underneath, paper white on top), counter-clockwise seen from -y
        plate(g, listOf(Vector3(-s, 0f, -s), Vector3(s, 0f, -s), Vector3(s, 0f, s), Vector3(-s, 0f, s)), MeshBuilder.DOWN, "masuBase")
        yield()
        val js = ArrayList<Joint>()
        // a wall hinged on a base edge. Joint frame: x along the edge, +z outward (the wall lies flat outward,
        // rose side down); turning about x by -90 degrees stands it up
        fun wall(name: String, pos: Vec3, yaw: Float, withTip: Boolean): Joint? {
            val j = Entity().named("masu:$name")
            g.addChild(j)
            val base = Quat4.axisAngle(Vec3.UP, yaw)
            j.setLocal(position = pos, rotation = base)
            plate(j, listOf(Vector3(-s, 0f, 0f), Vector3(s, 0f, 0f), Vector3(s, 0f, w), Vector3(-s, 0f, w)), MeshBuilder.DOWN, "masu_$name")
            val joint = Joint(j, base, Vec3.RIGHT)
            js += joint
            if (!withTip) return null
            // the end point: a triangle hinged on the wall's outer edge, folding back over the wall into the box
            val t = Entity().named("masu:${name}Tip")
            j.addChild(t)
            t.setLocal(position = Vec3(0f, 0f, w))
            plate(t, listOf(Vector3(-s, 0f, 0f), Vector3(s, 0f, 0f), Vector3(0f, 0f, tip)), MeshBuilder.DOWN, "masu_${name}Tip")
            val tj = Joint(t, Quat4.IDENTITY, Vec3.RIGHT)
            js += tj
            return tj
        }
        wall("near", Vec3(0f, 0f, s), 0f, withTip = false)
        yield()
        wall("far", Vec3(0f, 0f, -s), PI.toFloat(), withTip = false)
        yield()
        wall("left", Vec3(-s, 0f, 0f), -PI.toFloat() / 2f, withTip = true)
        yield()
        wall("right", Vec3(s, 0f, 0f), PI.toFloat() / 2f, withTip = true)
        yield()
        joints = js // near, far, left, leftTip, right, rightTip
        // degrees: walls 0 = flat, -90 = standing; tips 0 = in line with the wall, -170 = folded over into the box
        keyframes =
            when (kind) {
                FormKind.MASU_WALLS ->
                    listOf(
                        floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f),
                        floatArrayOf(-45f, -45f, 0f, 0f, 0f, 0f),
                        floatArrayOf(-90f, -90f, 0f, 0f, 0f, 0f),
                    )
                FormKind.MASU_END1 ->
                    listOf(
                        floatArrayOf(-90f, -90f, 0f, 0f, 0f, 0f),
                        floatArrayOf(-90f, -90f, -40f, 0f, 0f, 0f),
                        floatArrayOf(-90f, -90f, -90f, 0f, 0f, 0f),
                        floatArrayOf(-90f, -90f, -90f, -170f, 0f, 0f),
                    )
                else ->
                    listOf(
                        floatArrayOf(-90f, -90f, -90f, -170f, 0f, 0f),
                        floatArrayOf(-90f, -90f, -90f, -170f, -40f, 0f),
                        floatArrayOf(-90f, -90f, -90f, -170f, -90f, 0f),
                        floatArrayOf(-90f, -90f, -90f, -170f, -90f, -170f),
                    )
            }
    }

    private fun apply(angles: FloatArray) {
        for ((i, j) in joints.withIndex()) {
            val deg = angles.getOrElse(i) { 0f }
            j.entity.setLocal(rotation = j.base * Quat4.axisAngle(j.axis, Math.toRadians(deg.toDouble()).toFloat()))
        }
    }

    /** Tweens between keyframes; returns true while playing. */
    fun animate(nowMs: Long): Boolean {
        if (group == null || keyframes.isEmpty()) return false
        if (finalOnly || keyframes.size == 1) return false
        val n = keyframes.size
        val slotMs = HOLD_MS + TWEEN_MS
        val period = slotMs * n
        val t = ((nowMs - startMs) % period.toLong()).toFloat()
        val slot = (t / slotMs).toInt().coerceIn(0, n - 1)
        val inSlot = t - slot * slotMs
        val from = keyframes[slot]
        val to = keyframes[(slot + 1) % n]
        val q = if (inSlot < HOLD_MS) 0f else Ease.inOutCubic((inSlot - HOLD_MS) / TWEEN_MS)
        val a = FloatArray(from.size) { i -> from[i] + (to[i] - from[i]) * q }
        apply(a)
        return true
    }

    companion object {
        const val HOLD_MS = 900f
        const val TWEEN_MS = 250f
    }
}
