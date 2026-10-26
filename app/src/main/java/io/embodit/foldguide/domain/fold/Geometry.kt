package io.embodit.foldguide.domain.fold

import kotlin.math.abs
import kotlin.math.hypot

/**
 * 2D point on the sheet, millimetres. Model coordinates: origin at the sheet centre, x to the right,
 * y away from the user (design `spatialLayout.paper.coords`).
 */
data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)

    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)

    operator fun times(k: Float) = Pt(x * k, y * k)

    fun distanceTo(o: Pt): Float = hypot(x - o.x, y - o.y)

    val length: Float
        get() = hypot(x, y)

    fun normalized(): Pt {
        val l = length
        return if (l < 1e-9f) this else Pt(x / l, y / l)
    }

    companion object {
        val ZERO = Pt(0f, 0f)
    }
}

/** A fold line on the sheet with its type (design `models.format.lines`). */
data class FoldLine(val a: Pt, val b: Pt, val mountain: Boolean = false)

/** Where the moved part lands: a point (corner to point) or a segment (edge to line). */
sealed class Landing {
    data class At(val p: Pt) : Landing()

    data class Seg(val a: Pt, val b: Pt) : Landing()
}

/** Plain 2D geometry on convex polygons (every facet of a flat-folded square stays convex). */
object Geometry {
    /** Signed side of [p] relative to the line a -> b: > 0 to the left. */
    fun side(a: Pt, b: Pt, p: Pt): Float = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)

    /** Mirror image of [p] in the line a - b. */
    fun reflect(p: Pt, a: Pt, b: Pt): Pt {
        val f = foot(p, a, b)
        return Pt(2f * f.x - p.x, 2f * f.y - p.y)
    }

    /** Foot of the perpendicular from [p] onto the line a - b. */
    fun foot(p: Pt, a: Pt, b: Pt): Pt {
        var dx = b.x - a.x
        var dy = b.y - a.y
        val l = hypot(dx, dy)
        dx /= l
        dy /= l
        val t = (p.x - a.x) * dx + (p.y - a.y) * dy
        return Pt(a.x + t * dx, a.y + t * dy)
    }

    /** Twice the signed area of a polygon. */
    fun area2(pts: List<Pt>): Float {
        var s = 0f
        for (i in pts.indices) {
            val p = pts[i]
            val q = pts[(i + 1) % pts.size]
            s += p.x * q.y - q.x * p.y
        }
        return s
    }

    fun area(pts: List<Pt>): Float = area2(pts) / 2f

    /** Sutherland-Hodgman clip of a convex polygon: keeps the part where side * sgn >= 0. Null when nothing is left. */
    fun clip(pts: List<Pt>, a: Pt, b: Pt, sgn: Float): List<Pt>? {
        val out = ArrayList<Pt>(pts.size + 2)
        val n = pts.size
        for (i in 0 until n) {
            val p = pts[i]
            val q = pts[(i + 1) % n]
            val sp = side(a, b, p) * sgn
            val sq = side(a, b, q) * sgn
            if (sp >= -EPS) out += p
            if ((sp > EPS && sq < -EPS) || (sp < -EPS && sq > EPS)) {
                val t = sp / (sp - sq)
                out += Pt(p.x + t * (q.x - p.x), p.y + t * (q.y - p.y))
            }
        }
        return if (out.size >= 3 && abs(area(out)) > MIN_AREA) out else null
    }

    /** Even-odd point in polygon. */
    fun inside(p: Pt, poly: List<Pt>): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) c = !c
            j = i
        }
        return c
    }

    fun centroid(pts: List<Pt>): Pt {
        var x = 0f
        var y = 0f
        for (p in pts) {
            x += p.x
            y += p.y
        }
        return Pt(x / pts.size, y / pts.size)
    }

    /** Axis-aligned bounds: minX, minY, maxX, maxY. */
    fun bounds(pts: List<Pt>): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in pts) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    private const val EPS = 1e-4f
    private const val MIN_AREA = 0.5f
}
