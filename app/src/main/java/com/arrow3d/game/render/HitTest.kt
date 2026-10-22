package com.arrow3d.game.render

import com.arrow3d.domain.model.Level
import kotlin.math.max
import kotlin.math.sqrt

data class ArrowHit(val arrowId: Int, val ambiguous: Boolean)

/**
 * Finds the arrow under a touch. The touch area is generous and the arrow whose visible body is
 * closest to the finger wins. When two arrows are almost equally close the hit is flagged as
 * ambiguous, so a wrong guess never costs a life (design doc, section 7).
 */
object HitTest {

    fun find(level: Level, remaining: Set<Int>, projector: CubeProjector, touch: Point2): ArrowHit? {
        val cell = projector.cellPixels
        val radius = max(MIN_RADIUS_PX, cell * 0.55f)
        val scored = remaining.mapNotNull { id ->
            val shape = ArrowGeometry.shape(level.routeOf(id), 0f, projector)
            if (shape.spine.size < 2) return@mapNotNull null
            val d = shape.spine.chunked(2).filter { it.size == 2 }.minOf { (a, b) -> distanceToSegment(touch, a, b) }
            if (d <= radius) id to d else null
        }.sortedBy { it.second }
        val best = scored.firstOrNull() ?: return null
        val second = scored.getOrNull(1)
        val ambiguous = second != null && second.second - best.second < cell * AMBIGUITY_FRACTION
        return ArrowHit(best.first, ambiguous)
    }

    fun distanceToSegment(p: Point2, a: Point2, b: Point2): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len2 = abx * abx + aby * aby
        val t = if (len2 == 0f) 0f else (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0f, 1f)
        val cx = a.x + abx * t - p.x
        val cy = a.y + aby * t - p.y
        return sqrt(cx * cx + cy * cy)
    }

    private const val MIN_RADIUS_PX = 28f
    private const val AMBIGUITY_FRACTION = 0.12f
}
