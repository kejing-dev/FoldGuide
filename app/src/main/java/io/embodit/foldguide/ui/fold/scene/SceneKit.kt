package io.embodit.foldguide.ui.fold.scene

import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import io.embodit.foldguide.domain.fold.Pt
import io.embodit.foldguide.domain.model.Quat4
import io.embodit.foldguide.domain.model.Vec3
import io.embodit.foldguide.ui.theme.FoldColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// domain <-> SDK types. The scene works in Stage metres below one calibrated root (Spatial Ruler).
// ---------------------------------------------------------------------------------------------

internal fun Vec3.sdk(): Vector3 = Vector3(x, y, z)

internal fun Vector3.domain(): Vec3 = Vec3(x, y, z)

internal fun Quat4.sdk(): Quat = Quat(x, y, z, w)

internal fun Quat.domain(): Quat4 = Quat4(x, y, z, w)

internal fun Color.color4(): Color4 = Color4(red, green, blue, alpha)

internal val Entity.transformOrNull: TransformComponent?
    get() = components[TransformComponent::class.java]

internal fun Entity.setLocal(position: Vec3? = null, rotation: Quat4? = null, scale: Vec3? = null) {
    val t = transformOrNull ?: return
    if (position != null) t.setPosition(position.sdk())
    if (rotation != null) t.setQuaternion(rotation.sdk())
    if (scale != null) t.setScaleVector(scale.sdk())
}

internal fun Entity.setUniformScale(k: Float) {
    transformOrNull?.setScaleVector(Vector3(k, k, k))
}

internal fun Entity.setVisible(visible: Boolean) {
    components[ModelComponent::class.java]?.isVisible = visible
}

internal fun Entity.setMaterial(material: Material) {
    components[ModelComponent::class.java]?.materials?.set(0, material)
}

/** Overall opacity of an entity and its children (the guide fade). */
internal fun Entity.setOpacity(opacity: Float) {
    val existing = components[OpacityControllerComponent::class.java]
    if (existing != null) existing.opacity = opacity.coerceIn(0f, 1f)
    else components.set(OpacityControllerComponent(opacity.coerceIn(0f, 1f)))
}

internal fun Entity.named(name: String): Entity {
    runCatching { setName(name) }
    return this
}

/** A box collider of [size] (local metres) that the system pointer can tap or drag. */
internal fun Entity.tapBox(size: Vec3, offset: Vec3 = Vec3.ZERO) {
    val shape = ShapeResource.createBox(size.sdk()).let { if (offset == Vec3.ZERO) it else it.offsetByTranslation(offset.sdk()) }
    components.set(CollisionComponent(collisionShape = listOf(shape), physicsMaterial = PhysicsMaterialResource()))
    components.set(InteractableComponent())
}

/** Model millimetres (x right, y away) -> paper-local metres (x right, y up, z towards the user). */
internal fun Pt.local(h: Float = 0f): Vector3 = Vector3(x / 1000f, h, -y / 1000f)

internal fun Pt.localVec(h: Float = 0f): Vec3 = Vec3(x / 1000f, h, -y / 1000f)

/**
 * Resources shared by entities that come and go. The SDK releases a resource when its last user
 * entity is destroyed; shared ones are made global and closed when the scene goes away.
 */
internal class SharedResources {
    private val owned = ArrayList<Resource>()

    fun <T : Resource> keep(resource: T): T {
        resource.toGlobal()
        owned += resource
        return resource
    }

    fun close() {
        owned.forEach { runCatching { it.close() } }
        owned.clear()
    }
}

/** Scene-lifetime materials, one per appearance (design `palette`). Unlit, tone mapping off: exact colours. */
internal class SceneMaterials(private val shared: SharedResources) {
    /** The demo sheet's two faces: one-sided each, so a facet's top and bottom carry different colours. */
    val rose: Material = unlit(FoldColors.KamiRose, culling = MaterialCullingMode.BACK)
    val paper: Material = unlit(FoldColors.PaperBack, culling = MaterialCullingMode.BACK)

    /** Fold line: white core over a graphite halo. */
    val foldWhite: Material = unlit(FoldColors.FoldWhite)
    val graphite: Material = unlit(FoldColors.GraphiteHalo)

    /** The moving flap (22 %), its edge and the arrows (amber). */
    val amberTint: Material = sheer(FoldColors.FlapAmber, 0.22f)
    val amber: Material = unlit(FoldColors.FlapAmber)

    /** Landings. */
    val jade: Material = unlit(FoldColors.LandingJade)

    /** Earlier creases: white 30 %. */
    val crease: Material = sheer(FoldColors.FoldWhite, 0.30f)

    /** Reticle: rose 25 % fill, amber dashes; "cannot place": coral. */
    val roseGhost: Material = sheer(FoldColors.KamiRose, 0.25f, depthTest = false)
    val amberDash: Material = sheer(FoldColors.FlapAmber, 0.9f, depthTest = false)
    val coral: Material = sheer(FoldColors.Coral, 0.95f, depthTest = false)

    private fun unlit(color: Color, culling: MaterialCullingMode = MaterialCullingMode.NONE): UnlitMaterial =
        shared.keep(
            UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                setBaseColor(color.color4())
                runCatching { setApplyToneMapping(false) }
                setCullingMode(culling)
            }
        )

    /** A see-through unlit material: the colour is opaque, the material's opacity carries the share. */
    private fun sheer(color: Color, opacity: Float, depthTest: Boolean = true): UnlitMaterial =
        shared.keep(
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(color.copy(alpha = 1f).color4())
                setOpacity(opacity)
                runCatching { setApplyToneMapping(false) }
                setCullingMode(MaterialCullingMode.NONE)
                setDepthWrite(false)
                if (!depthTest) setDepthTest(false)
            }
        )
}

/**
 * Accumulates triangles and builds one `MeshResource` (one mesh costs about a frame: everything of
 * one appearance goes into one mesh). Polygons are convex; faces are fans. Winding: counter-clockwise
 * seen along the normal is the front face.
 */
internal class MeshBuilder {
    private val positions = ArrayList<Vector3>()
    private val normals = ArrayList<Vector3>()
    private val indices = ArrayList<Int>()
    private var minX = Float.MAX_VALUE
    private var minY = Float.MAX_VALUE
    private var minZ = Float.MAX_VALUE
    private var maxX = -Float.MAX_VALUE
    private var maxY = -Float.MAX_VALUE
    private var maxZ = -Float.MAX_VALUE

    val isEmpty: Boolean
        get() = indices.isEmpty()

    private fun add(p: Vector3, n: Vector3): Int {
        positions += p
        normals += n
        if (p.x < minX) minX = p.x
        if (p.y < minY) minY = p.y
        if (p.z < minZ) minZ = p.z
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
        if (p.z > maxZ) maxZ = p.z
        return positions.size - 1
    }

    /** A convex polygon with the given [normal]; [reverse] turns it to face the other way. */
    fun polygon(points: List<Vector3>, normal: Vector3, reverse: Boolean = false) {
        if (points.size < 3) return
        val pts = if (reverse) points.reversed() else points
        val n = if (reverse) Vector3(-normal.x, -normal.y, -normal.z) else normal
        val base = positions.size
        for (p in pts) add(p, n)
        for (i in 1 until pts.size - 1) {
            indices += base
            indices += base + i
            indices += base + i + 1
        }
    }

    /** A quad strip segment from [a] to [b] of [width], lying in the plane with normal [normal]. */
    fun band(a: Vector3, b: Vector3, width: Float, normal: Vector3) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-6f) return
        // side = normal x dir
        var sx = normal.y * dz - normal.z * dy
        var sy = normal.z * dx - normal.x * dz
        var sz = normal.x * dy - normal.y * dx
        val sl = kotlin.math.sqrt(sx * sx + sy * sy + sz * sz)
        if (sl < 1e-9f) return
        val hw = width / 2f / sl
        sx *= hw
        sy *= hw
        sz *= hw
        polygon(
            listOf(
                Vector3(a.x - sx, a.y - sy, a.z - sz),
                Vector3(b.x - sx, b.y - sy, b.z - sz),
                Vector3(b.x + sx, b.y + sy, b.z + sz),
                Vector3(a.x + sx, a.y + sy, a.z + sz),
            ),
            normal,
        )
    }

    /** A flat ring in the XZ plane at height [y], facing +Y. */
    fun ring(cx: Float, y: Float, cz: Float, inner: Float, outer: Float, segments: Int = 32, dashes: Int = 0, dashFill: Float = 0.6f) {
        fun arc(a0: Double, a1: Double, steps: Int) {
            for (i in 0 until steps) {
                val s = a0 + (a1 - a0) * i / steps
                val e = a0 + (a1 - a0) * (i + 1) / steps
                polygon(
                    listOf(
                        Vector3(cx + (cos(s) * inner).toFloat(), y, cz + (sin(s) * inner).toFloat()),
                        Vector3(cx + (cos(e) * inner).toFloat(), y, cz + (sin(e) * inner).toFloat()),
                        Vector3(cx + (cos(e) * outer).toFloat(), y, cz + (sin(e) * outer).toFloat()),
                        Vector3(cx + (cos(s) * outer).toFloat(), y, cz + (sin(s) * outer).toFloat()),
                    ),
                    UP,
                    reverse = true,
                )
            }
        }
        if (dashes <= 0) arc(0.0, 2 * PI, segments)
        else {
            val slot = 2 * PI / dashes
            for (d in 0 until dashes) arc(d * slot, d * slot + slot * dashFill, 3)
        }
    }

    /** A flat disc in the XZ plane at height [y], facing +Y. */
    fun disc(cx: Float, y: Float, cz: Float, r: Float, segments: Int = 24) {
        val pts = ArrayList<Vector3>(segments)
        for (i in 0 until segments) {
            val a = 2 * PI * i / segments
            pts += Vector3(cx + (cos(a) * r).toFloat(), y, cz + (sin(a) * r).toFloat())
        }
        polygon(pts, UP, reverse = true)
    }

    fun build(name: String): MeshResource {
        val center = Vector3((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f)
        val half = Vector3(((maxX - minX) / 2f).coerceAtLeast(0.001f), ((maxY - minY) / 2f).coerceAtLeast(0.001f), ((maxZ - minZ) / 2f).coerceAtLeast(0.001f))
        return MeshResource.createWithMeshModel(
            model = MeshModel(positions = ArrayList(positions), triangleIndices = ArrayList(indices), normals = ArrayList(normals)),
            bounds = BoundingBox(center = center, halfExtent = half),
            name = name,
        )
    }

    companion object {
        val UP = Vector3(0f, 1f, 0f)
        val DOWN = Vector3(0f, -1f, 0f)
    }
}

/** A built mesh entity under a parent; destroyed with [destroy]. */
internal class Piece(parent: Entity, mesh: MeshResource, material: Material, name: String) {
    val entity: Entity = ModelEntity(mesh, material).named(name)

    init {
        parent.addChild(entity)
    }

    fun destroy() {
        runCatching { entity.destroy(true) }
    }
}

/** Time-based easing helpers. */
internal object Ease {
    fun inOutCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

    fun inOutQuad(t: Float): Float = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f

    fun outCubic(t: Float): Float = 1f - (1f - t).let { it * it * it }
}
