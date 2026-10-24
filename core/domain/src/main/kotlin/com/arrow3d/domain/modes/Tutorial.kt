package com.arrow3d.domain.modes

import com.arrow3d.domain.model.Arrow
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.domain.model.Level
import com.arrow3d.domain.surface.Cell
import com.arrow3d.domain.surface.CubeSurface
import com.arrow3d.domain.surface.Dir2D
import com.arrow3d.domain.surface.FaceId

/** What a lesson teaches; the app maps each one to its text. */
enum class LessonTopic { TAP, BLOCKING, CHAIN, LONG, BENT, TRAVELER, COMBO }

data class Lesson(val topic: LessonTopic, val level: Level)

/**
 * The tutorial: small hand-made levels, one idea each. They only use the three faces the default
 * camera shows (top, front, right), so a new player never has to rotate to find an arrow, and
 * they do not cover the whole cube, so the idea of each lesson stands out.
 *
 * Face axes on the 3x3 cube: TOP u = x (towards the right face), v = z (towards the front);
 * FRONT u = x, v = y (up); RIGHT u = z (towards the front), v = y (up).
 */
object Tutorial {
    private val surface = CubeSurface(3)

    private fun straight(id: Int, face: FaceId, u: Int, v: Int, dir: Dir2D, length: Int = 1, kind: ArrowKind = ArrowKind.COMMON) =
        Arrow.fromTail(id, face, Cell(u, v), dir, length, kind)

    private fun path(id: Int, face: FaceId, vararg cells: Pair<Int, Int>) =
        Arrow.fromPath(id, face, cells.map { (u, v) -> Cell(u, v) })

    val lessons: List<Lesson> = listOf(
        // Every arrow can leave: tap them.
        Lesson(
            LessonTopic.TAP,
            Level(
                surface,
                listOf(
                    straight(0, FaceId.TOP, 0, 1, Dir2D.U_MINUS),
                    straight(1, FaceId.TOP, 2, 1, Dir2D.U_PLUS),
                    straight(2, FaceId.TOP, 1, 2, Dir2D.V_PLUS),
                ),
            ),
        ),
        // The left arrow is blocked by the one standing in front of it.
        Lesson(
            LessonTopic.BLOCKING,
            Level(
                surface,
                listOf(
                    straight(0, FaceId.FRONT, 0, 1, Dir2D.U_PLUS),
                    straight(1, FaceId.FRONT, 1, 1, Dir2D.V_PLUS),
                    straight(2, FaceId.FRONT, 2, 0, Dir2D.U_PLUS),
                ),
            ),
        ),
        // A row: each one frees the next.
        Lesson(
            LessonTopic.CHAIN,
            Level(
                surface,
                listOf(
                    straight(0, FaceId.TOP, 0, 0, Dir2D.U_PLUS),
                    straight(1, FaceId.TOP, 1, 0, Dir2D.U_PLUS),
                    straight(2, FaceId.TOP, 2, 0, Dir2D.U_PLUS),
                    straight(3, FaceId.TOP, 1, 2, Dir2D.V_PLUS),
                ),
            ),
        ),
        // Long arrows block a whole line until they go.
        Lesson(
            LessonTopic.LONG,
            Level(
                surface,
                listOf(
                    straight(0, FaceId.RIGHT, 0, 0, Dir2D.V_PLUS, length = 3),
                    straight(1, FaceId.RIGHT, 2, 1, Dir2D.U_MINUS, length = 2),
                    straight(2, FaceId.RIGHT, 2, 0, Dir2D.V_MINUS),
                ),
            ),
        ),
        // A bent arrow leaves like a snake: only what is in front of its head matters, and its
        // tail frees the cell the arrow at (0,1) needs.
        Lesson(
            LessonTopic.BENT,
            Level(
                surface,
                listOf(
                    path(0, FaceId.TOP, 0 to 0, 1 to 0, 1 to 1, 1 to 2),
                    straight(1, FaceId.TOP, 0, 1, Dir2D.V_MINUS),
                    straight(2, FaceId.TOP, 2, 1, Dir2D.U_PLUS),
                ),
            ),
        ),
        // The traveler folds over the top-right edge and runs down the right face, where an
        // arrow is in its way.
        Lesson(
            LessonTopic.TRAVELER,
            Level(
                surface,
                listOf(
                    straight(0, FaceId.TOP, 1, 1, Dir2D.U_PLUS, kind = ArrowKind.TRAVELER),
                    straight(1, FaceId.RIGHT, 1, 0, Dir2D.U_PLUS),
                    straight(2, FaceId.TOP, 0, 2, Dir2D.V_PLUS),
                ),
            ),
        ),
        // Lots of free arrows: take them out quickly to build a combo.
        Lesson(
            LessonTopic.COMBO,
            Level(
                surface,
                listOf(
                    straight(0, FaceId.FRONT, 0, 0, Dir2D.U_MINUS),
                    straight(1, FaceId.FRONT, 1, 0, Dir2D.V_MINUS),
                    straight(2, FaceId.FRONT, 2, 0, Dir2D.U_PLUS),
                    straight(3, FaceId.FRONT, 0, 2, Dir2D.U_MINUS),
                    straight(4, FaceId.FRONT, 1, 2, Dir2D.V_PLUS),
                    straight(5, FaceId.FRONT, 2, 2, Dir2D.U_PLUS),
                    straight(6, FaceId.FRONT, 1, 1, Dir2D.V_PLUS),
                ),
            ),
        ),
    )

    val size: Int get() = lessons.size

    /** Lesson to start from: the first one not done yet, or the beginning again once all are done. */
    fun startIndex(completed: Int): Int = if (completed in 0 until size) completed else 0
}
