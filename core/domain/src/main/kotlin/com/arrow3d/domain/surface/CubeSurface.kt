package com.arrow3d.domain.surface

/**
 * Surface of a cube with an [size] x [size] grid on every face.
 *
 * World axes: Y is up. The cube occupies [0, 2*size] on every axis in doubled units.
 * Arrows only ever move on this surface: inside a face, or folding over an edge onto the
 * neighbouring face. They never go through the solid.
 */
class CubeSurface(val size: Int) {

    init {
        require(size >= 1) { "Grid size must be at least 1" }
    }

    private val n2 = size * 2

    val faces: List<FaceFrame> = listOf(
        FaceFrame(FaceId.TOP, Vec3i.Y, Vec3i(0, n2, 0), Vec3i.X, Vec3i.Z),
        FaceFrame(FaceId.BOTTOM, -Vec3i.Y, Vec3i.ZERO, Vec3i.X, Vec3i.Z),
        FaceFrame(FaceId.FRONT, Vec3i.Z, Vec3i(0, 0, n2), Vec3i.X, Vec3i.Y),
        FaceFrame(FaceId.BACK, -Vec3i.Z, Vec3i.ZERO, Vec3i.X, Vec3i.Y),
        FaceFrame(FaceId.RIGHT, Vec3i.X, Vec3i(n2, 0, 0), Vec3i.Z, Vec3i.Y),
        FaceFrame(FaceId.LEFT, -Vec3i.X, Vec3i.ZERO, Vec3i.Z, Vec3i.Y),
    )

    private val byId = faces.associateBy { it.id }

    fun frame(face: FaceId): FaceFrame = byId.getValue(face)

    fun contains(cell: Cell): Boolean = cell.u in 0 until size && cell.v in 0 until size

    fun allCells(): List<CellRef> = faces.flatMap { f ->
        (0 until size).flatMap { u -> (0 until size).map { v -> CellRef(f.id, Cell(u, v)) } }
    }

    /** Centre of a cell, in doubled world units. */
    fun center(ref: CellRef): Vec3i {
        val f = frame(ref.face)
        return f.origin + f.uAxis * (2 * ref.cell.u + 1) + f.vAxis * (2 * ref.cell.v + 1)
    }

    /** 3D unit vector of an in-plane direction. */
    fun dir3(face: FaceId, dir: Dir2D): Vec3i {
        val f = frame(face)
        return f.uAxis * dir.du + f.vAxis * dir.dv
    }

    /** In-plane direction of a 3D unit vector lying on [face]. */
    fun dirOn(face: FaceId, vec: Vec3i): Dir2D {
        val f = frame(face)
        return when {
            vec == f.uAxis -> Dir2D.U_PLUS
            vec == -f.uAxis -> Dir2D.U_MINUS
            vec == f.vAxis -> Dir2D.V_PLUS
            vec == -f.vAxis -> Dir2D.V_MINUS
            else -> error("$vec is not parallel to face $face")
        }
    }

    /** Next cell on the same face, or null when [ref] is on the edge in that direction. */
    fun step(ref: CellRef, dir: Dir2D): CellRef? {
        val next = ref.cell + dir
        return if (contains(next)) CellRef(ref.face, next) else null
    }

    /**
     * Crosses the edge in front of [ref] moving along [dir] and returns the first cell on the
     * neighbouring face plus the direction of travel there. On a cube every edge is convex,
     * so the arrow folds "down" along the neighbour face.
     */
    fun fold(ref: CellRef, dir: Dir2D): Pair<CellRef, Dir2D> {
        val from = frame(ref.face)
        val travel = dir3(ref.face, dir)
        val to = faces.first { it.normal == travel }
        val newTravel = -from.normal
        val edgePoint = center(ref) + travel
        val entryCentre = edgePoint + newTravel
        val rel = entryCentre - to.origin
        val cell = Cell(((rel dot to.uAxis) - 1) / 2, ((rel dot to.vAxis) - 1) / 2)
        check(contains(cell)) { "Fold produced an invalid cell $cell" }
        return CellRef(to.id, cell) to dirOn(to.id, newTravel)
    }

    /** Distance from a cell to the nearest edge of its face (0 = touching an edge). */
    fun distanceToEdge(cell: Cell): Int =
        minOf(cell.u, size - 1 - cell.u, cell.v, size - 1 - cell.v)
}
