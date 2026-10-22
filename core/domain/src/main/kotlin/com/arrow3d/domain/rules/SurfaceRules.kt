package com.arrow3d.domain.rules

import com.arrow3d.domain.model.Arrow
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.domain.surface.CellRef
import com.arrow3d.domain.surface.CubeSurface
import com.arrow3d.domain.surface.Vec3i

/** How many edges each arrow kind is allowed to fold over. */
fun ArrowKind.folds(): Int = when (this) {
    ArrowKind.COMMON -> 0
    ArrowKind.TRAVELER -> 1
}

/**
 * Cells the arrow's head has to cross to leave the surface, in order.
 * Only these cells matter: the body follows the head along the same route.
 */
fun CubeSurface.exitPath(arrow: Arrow): List<CellRef> {
    val out = ArrayList<CellRef>()
    var ref = arrow.headRef
    var dir = arrow.dir
    var foldsLeft = arrow.kind.folds()
    while (true) {
        val next = step(ref, dir)
        if (next != null) {
            out += next
            ref = next
        } else {
            if (foldsLeft == 0) break
            foldsLeft--
            val (entry, newDir) = fold(ref, dir)
            out += entry
            ref = entry
            dir = newDir
        }
    }
    return out
}

/**
 * Geometry of the whole movement, for rendering: a polyline over the surface from the back of
 * the tail to the edge where the arrow leaves, plus a final stretch off the surface.
 * Positions are in doubled world units.
 */
data class Route(
    /** points[0] = back of the tail; then one point per edge crossed; last = point off the surface. */
    val points: List<Vec3i>,
    /** Outward normal of the face each segment lies on (last segment keeps the last face's normal). */
    val segmentNormals: List<Vec3i>,
    /** Arc length (doubled units) from points[0] to the edge where the arrow leaves the surface. */
    val surfaceLength: Int,
    /** Body length in doubled units (2 per cell). */
    val bodyLength: Int,
)

fun CubeSurface.route(arrow: Arrow): Route {
    var dir = arrow.dir
    var face = arrow.face
    var travel = dir3(face, dir)
    val points = mutableListOf(center(CellRef(face, arrow.tail)) - travel)
    val normals = mutableListOf<Vec3i>()
    var ref = arrow.headRef
    var foldsLeft = arrow.kind.folds()
    var length = 2 * arrow.length - 1 // back of tail -> head centre
    while (true) {
        val next = step(ref, dir)
        if (next != null) {
            ref = next
            length += 2
            continue
        }
        // reached the edge in front of `ref`
        val edge = center(ref) + travel
        length += 1
        points += edge
        normals += frame(face).normal
        if (foldsLeft == 0) break
        foldsLeft--
        val (entry, newDir) = fold(ref, dir)
        ref = entry
        dir = newDir
        face = entry.face
        travel = dir3(face, dir)
        length += 1 // edge -> centre of entry cell
    }
    // Leave tangentially, far enough for the whole body to clear the surface.
    points += points.last() + travel * (2 * arrow.length + 2)
    normals += frame(face).normal
    return Route(points, normals, surfaceLength = length, bodyLength = 2 * arrow.length)
}
