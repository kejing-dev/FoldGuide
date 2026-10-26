package io.embodit.foldguide.domain.usecase

import io.embodit.foldguide.domain.model.PlaneSurface
import io.embodit.foldguide.domain.model.SurfaceKind
import io.embodit.foldguide.domain.model.Vec3
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Which detected planes count as a desk (design `container.notes`): a horizontal plane (normal within
 * 18 degrees of the vertical, |n.y| > 0.95) between 0.45 and 1.15 m above the floor. TABLE planes are
 * taken whatever their height (the emulator's room reports its table as TABLE); CABINET and unknown
 * horizontal planes only inside the height window.
 */
class DeskUseCase {

    /** Height of the floor: the lowest FLOOR plane, else 0 (the Stage origin is on the floor). */
    fun floorY(planes: Collection<PlaneSurface>): Float =
        planes.filter { it.kind == SurfaceKind.FLOOR }.minOfOrNull { it.minY } ?: 0f

    fun isDesk(plane: PlaneSurface, floorY: Float): Boolean {
        if (abs(plane.normal.y) <= MIN_NORMAL_Y) return false
        if (plane.kind == SurfaceKind.FLOOR || plane.kind == SurfaceKind.CEILING || plane.kind == SurfaceKind.WALL || plane.kind == SurfaceKind.DOOR || plane.kind == SurfaceKind.WINDOW) return false
        if (plane.kind == SurfaceKind.TABLE) return true
        val h = plane.centroid.y - floorY
        return h in MIN_HEIGHT_M..MAX_HEIGHT_M
    }

    /** The desks among [planes], tables first. */
    fun desks(planes: Collection<PlaneSurface>): List<PlaneSurface> {
        val floor = floorY(planes)
        return planes.filter { isDesk(it, floor) }.sortedBy { if (it.kind == SurfaceKind.TABLE) 0 else 1 }
    }

    /**
     * Yaw of the demo sheet placed at [point] by a viewer at [head]: the sheet's "away" axis is the
     * horizontal direction from the viewer to the point (design `spatialLayout.panel.deskFrame`).
     */
    fun yawFacing(head: Vec3, point: Vec3): Float {
        val fx = point.x - head.x
        val fz = point.z - head.z
        return if (fx * fx + fz * fz < 1e-6f) 0f else atan2(-fx, -fz)
    }

    companion object {
        const val MIN_NORMAL_Y = 0.95f
        const val MIN_HEIGHT_M = 0.45f
        const val MAX_HEIGHT_M = 1.15f
    }
}
