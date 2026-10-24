package com.arrow3d.domain.model

import com.arrow3d.domain.rules.Route
import com.arrow3d.domain.rules.exitPath
import com.arrow3d.domain.rules.route
import com.arrow3d.domain.surface.CellRef
import com.arrow3d.domain.surface.CubeSurface

/**
 * Static description of a puzzle: the surface and every arrow on it.
 * Which arrows are still on the shape lives in [com.arrow3d.domain.engine.PuzzleState].
 */
class Level(
    val surface: CubeSurface,
    val arrows: List<Arrow>,
) {
    val byId: Map<Int, Arrow> = arrows.associateBy { it.id }

    /** Which arrow owns each cell. */
    val owner: Map<CellRef, Int> = buildMap {
        arrows.forEach { a ->
            a.refs.forEach { ref ->
                require(surface.contains(ref.cell)) { "Arrow ${a.id} leaves the grid at $ref" }
                val previous = put(ref, a.id)
                require(previous == null) { "Cell $ref is shared by arrows $previous and ${a.id}" }
            }
        }
    }

    private val paths: Map<Int, List<CellRef>> = arrows.associate { a ->
        val path = surface.exitPath(a)
        require(path.none { it in a.refs }) { "Arrow ${a.id} points into its own body" }
        a.id to path
    }
    private val routes: Map<Int, Route> = arrows.associate { it.id to surface.route(it) }

    val ids: Set<Int> get() = byId.keys

    fun arrow(id: Int): Arrow = byId.getValue(id)

    fun pathOf(id: Int): List<CellRef> = paths.getValue(id)

    fun routeOf(id: Int): Route = routes.getValue(id)

    /** True when every cell of every face belongs to an arrow. */
    val coversWholeSurface: Boolean get() = owner.size == surface.allCells().size

    /** First arrow still on the shape that stands in the way of [id], or null if it can leave. */
    fun blockerOf(id: Int, remaining: Set<Int>): Int? =
        pathOf(id).firstNotNullOfOrNull { ref -> owner[ref]?.takeIf { it in remaining && it != id } }

    fun isFree(id: Int, remaining: Set<Int>): Boolean = blockerOf(id, remaining) == null

    fun freeArrows(remaining: Set<Int>): Set<Int> = remaining.filterTo(HashSet()) { isFree(it, remaining) }
}
