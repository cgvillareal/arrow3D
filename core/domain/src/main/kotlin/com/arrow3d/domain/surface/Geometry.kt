package com.arrow3d.domain.surface

/**
 * Integer 3D vector. Surface coordinates are stored "doubled" (one cell = 2 units)
 * so cell centres, cell edges and face edges are all exact integers.
 */
data class Vec3i(val x: Int, val y: Int, val z: Int) {
    operator fun plus(o: Vec3i) = Vec3i(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3i) = Vec3i(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Int) = Vec3i(x * k, y * k, z * k)
    operator fun unaryMinus() = Vec3i(-x, -y, -z)
    infix fun dot(o: Vec3i) = x * o.x + y * o.y + z * o.z

    companion object {
        val X = Vec3i(1, 0, 0)
        val Y = Vec3i(0, 1, 0)
        val Z = Vec3i(0, 0, 1)
        val ZERO = Vec3i(0, 0, 0)
    }
}

/** Direction inside a face plane, expressed in the face's local (u, v) axes. */
enum class Dir2D(val du: Int, val dv: Int) {
    U_PLUS(1, 0), U_MINUS(-1, 0), V_PLUS(0, 1), V_MINUS(0, -1);

    val opposite: Dir2D
        get() = when (this) {
            U_PLUS -> U_MINUS
            U_MINUS -> U_PLUS
            V_PLUS -> V_MINUS
            V_MINUS -> V_PLUS
        }
}

data class Cell(val u: Int, val v: Int) {
    operator fun plus(d: Dir2D) = Cell(u + d.du, v + d.dv)
    fun moved(d: Dir2D, steps: Int) = Cell(u + d.du * steps, v + d.dv * steps)
}

enum class FaceId { TOP, BOTTOM, FRONT, BACK, RIGHT, LEFT }

data class CellRef(val face: FaceId, val cell: Cell)

/** A face is a grid lying on a plane: corner [origin], local axes [uAxis]/[vAxis] and outward [normal]. */
data class FaceFrame(
    val id: FaceId,
    val normal: Vec3i,
    val origin: Vec3i,
    val uAxis: Vec3i,
    val vAxis: Vec3i,
)
