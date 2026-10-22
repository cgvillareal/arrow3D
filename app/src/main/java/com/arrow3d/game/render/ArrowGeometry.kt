package com.arrow3d.game.render

import com.arrow3d.domain.rules.Route
import com.arrow3d.domain.surface.Vec3i

/** A screen-space polygon, ready to be filled. */
class ScreenPolygon(val points: List<Point2>)

/** Everything needed to draw one arrow in its current position. */
class ArrowShape(
    val polygons: List<ScreenPolygon>,
    /** Screen position of the arrow tip (for halos and hints), null when hidden. */
    val tip: Point2?,
    /** Projected body centre line, only over visible faces (for hit testing). */
    val spine: List<Point2>,
)

/**
 * Turns a domain [Route] into screen polygons. The arrow occupies the arc-length interval
 * [offset + TAIL_INSET, offset + bodyLength - HEAD_GAP] along the route, so the same code draws
 * an arrow at rest, sliding over its face, folding over an edge (traveler) and leaving the shape.
 */
object ArrowGeometry {
    // All lengths in doubled units (one cell = 2).
    const val TAIL_INSET = 0.35f
    const val HEAD_GAP = 0.2f
    const val HEAD_LENGTH = 0.75f
    const val SHAFT_HALF_WIDTH = 0.26f
    const val HEAD_HALF_WIDTH = 0.62f

    private class Piece(val from: Vec3f, val to: Vec3f, val dir: Vec3f, val normal: Vec3i)

    fun totalLength(route: Route): Float =
        route.points.zipWithNext().sumOf { (a, b) -> (Vec3f.of(b) - Vec3f.of(a)).length().toDouble() }.toFloat()

    /** Offset at which the whole body has left the surface. */
    fun exitTravel(route: Route): Float = totalLength(route) - route.bodyLength

    fun shape(route: Route, offset: Float, projector: CubeProjector, widthScale: Float = 1f): ArrowShape {
        val start = offset + TAIL_INSET
        val end = offset + route.bodyLength - HEAD_GAP
        val shaftEnd = end - HEAD_LENGTH
        val polygons = ArrayList<ScreenPolygon>()

        for (p in pieces(route, start, shaftEnd)) {
            if (!projector.isFacing(p.normal, p.from)) continue
            val side = (p.dir cross Vec3f.of(p.normal)).normalized() * (SHAFT_HALF_WIDTH * widthScale)
            polygons += ScreenPolygon(
                listOf(
                    projector.projectSurface(p.from - side),
                    projector.projectSurface(p.to - side),
                    projector.projectSurface(p.to + side),
                    projector.projectSurface(p.from + side),
                ),
            )
        }

        var tipScreen: Point2? = null
        val head = pieces(route, shaftEnd, end).lastOrNull()
        if (head != null && projector.isFacing(head.normal, head.to)) {
            val base = pointAt(route, shaftEnd)
            val tip = pointAt(route, end)
            val side = (head.dir cross Vec3f.of(head.normal)).normalized() * (HEAD_HALF_WIDTH * widthScale)
            tipScreen = projector.projectSurface(tip)
            polygons += ScreenPolygon(
                listOf(projector.projectSurface(base - side), tipScreen, projector.projectSurface(base + side)),
            )
        }

        val spine = pieces(route, start, end)
            .filter { projector.isFacing(it.normal, it.from) }
            .flatMap { listOf(projector.projectSurface(it.from), projector.projectSurface(it.to)) }
        return ArrowShape(polygons, tipScreen, spine)
    }

    /**
     * Splits the arc-length interval [from, to] into straight pieces, one per route segment.
     * The first segment extends backwards and the last one forwards, so offsets outside the
     * route still produce sensible geometry.
     */
    private fun pieces(route: Route, from: Float, to: Float): List<Piece> {
        if (to <= from) return emptyList()
        val out = ArrayList<Piece>()
        var acc = 0f
        val lastIndex = route.points.size - 2
        for (i in 0..lastIndex) {
            val a = Vec3f.of(route.points[i])
            val b = Vec3f.of(route.points[i + 1])
            val len = (b - a).length()
            val dir = (b - a).normalized()
            val lo = if (i == 0) from else maxOf(from, acc)
            val hi = if (i == lastIndex) to else minOf(to, acc + len)
            if (hi > lo) {
                out += Piece(a + dir * (lo - acc), a + dir * (hi - acc), dir, route.segmentNormals[i])
            }
            acc += len
        }
        return out
    }

    private fun pointAt(route: Route, s: Float): Vec3f {
        val p = pieces(route, s - 0.001f, s).lastOrNull() ?: pieces(route, s, s + 0.001f).first()
        return p.to
    }
}
