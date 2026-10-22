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
 * A straight piece lying on one face. [cells] go from tail to head; the arrow points along its
 * own axis, so a long arrow can only point forward along its body.
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
        cells.zipWithNext().forEach { (a, b) ->
            require(a + dir == b) { "Arrow $id cells must be contiguous along $dir (tail to head)" }
        }
    }

    val length: Int get() = cells.size
    val tail: Cell get() = cells.first()
    val head: Cell get() = cells.last()
    val headRef: CellRef get() = CellRef(face, head)
    val refs: List<CellRef> get() = cells.map { CellRef(face, it) }

    companion object {
        /** Builds an arrow from its tail cell, direction and length. */
        fun fromTail(id: Int, face: FaceId, tail: Cell, dir: Dir2D, length: Int, kind: ArrowKind = ArrowKind.COMMON) =
            Arrow(id, face, (0 until length).map { tail.moved(dir, it) }, dir, kind)
    }
}
