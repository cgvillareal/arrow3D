package com.arrow3d.domain.model

import com.arrow3d.domain.surface.Cell
import com.arrow3d.domain.surface.CellRef
import com.arrow3d.domain.surface.Dir2D
import com.arrow3d.domain.surface.FaceId

enum class ArrowKind {
    /** Slides over its own face and leaves through the edge. */
    COMMON,

    /** Different colour: folds over one edge and leaves through the far edge of the neighbour face. */
    TRAVELER,
}

/**
 * A piece lying on one face. [cells] go from tail to head and every cell touches the next one,
 * so a long arrow can bend around other arrows like a snake. It points along its last step:
 * [dir] is the direction of the head. When it moves, the head goes forward and the body follows
 * the head's trail, so only the cells in front of the head can block it.
 */
data class Arrow(
    val id: Int,
    val face: FaceId,
    val cells: List<Cell>,
    val dir: Dir2D,
    val kind: ArrowKind = ArrowKind.COMMON,
) {
    init {
        require(cells.isNotEmpty()) { "Arrow $id has no cells" }
        require(cells.toSet().size == cells.size) { "Arrow $id crosses itself" }
        cells.zipWithNext().forEach { (a, b) ->
            require(stepBetween(a, b) != null) { "Arrow $id cells must touch each other (tail to head)" }
        }
        if (cells.size >= 2) {
            require(stepBetween(cells[cells.size - 2], head) == dir) { "Arrow $id must point along its last step" }
        }
    }

    val length: Int get() = cells.size
    val tail: Cell get() = cells.first()
    val head: Cell get() = cells.last()
    val headRef: CellRef get() = CellRef(face, head)
    val refs: List<CellRef> get() = cells.map { CellRef(face, it) }

    /** Direction of each step from tail to head; a single-cell arrow has one step, its [dir]. */
    val steps: List<Dir2D>
        get() = if (cells.size == 1) listOf(dir) else cells.zipWithNext().map { (a, b) -> stepBetween(a, b)!! }

    /** Direction the tail moves in first. */
    val tailDir: Dir2D get() = steps.first()

    /** Number of turns in the body. */
    val bends: Int get() = steps.zipWithNext().count { (a, b) -> a != b }

    val isBent: Boolean get() = bends > 0

    companion object {
        /** Builds a straight arrow from its tail cell, direction and length. */
        fun fromTail(id: Int, face: FaceId, tail: Cell, dir: Dir2D, length: Int, kind: ArrowKind = ArrowKind.COMMON) =
            Arrow(id, face, (0 until length).map { tail.moved(dir, it) }, dir, kind)

        /** Builds an arrow from its cells (tail to head, at least two); it points along its last step. */
        fun fromPath(id: Int, face: FaceId, cells: List<Cell>, kind: ArrowKind = ArrowKind.COMMON): Arrow {
            require(cells.size >= 2) { "A path needs two cells to know where it points" }
            val dir = requireNotNull(stepBetween(cells[cells.size - 2], cells.last())) { "Cells must touch" }
            return Arrow(id, face, cells, dir, kind)
        }

        fun stepBetween(a: Cell, b: Cell): Dir2D? = Dir2D.entries.firstOrNull { a + it == b }
    }
}
