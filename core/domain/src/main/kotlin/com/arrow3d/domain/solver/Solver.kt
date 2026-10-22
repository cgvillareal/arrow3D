package com.arrow3d.domain.solver

import com.arrow3d.domain.model.Level

data class SolveResult(
    val solvable: Boolean,
    /** A valid removal order (complete only when [solvable]). */
    val order: List<Int>,
    /**
     * Number of "waves": in each wave every currently free arrow leaves.
     * It is the length of the longest dependency chain, our main difficulty metric.
     */
    val depth: Int,
    /** Arrows that can leave right at the start. */
    val initiallyFree: Int,
    /** Arrows that end up stuck when the level is not solvable. */
    val stuck: Set<Int>,
)

/**
 * Removing an arrow never blocks another one, it only frees cells. So the set of free arrows
 * only grows, and a greedy strategy is complete: keep removing free arrows; if the shape empties
 * the level is solvable, otherwise no order would work.
 */
object Solver {

    fun solve(level: Level, remaining: Set<Int> = level.ids): SolveResult {
        val left = remaining.toMutableSet()
        val order = ArrayList<Int>(left.size)
        var depth = 0
        var initiallyFree = -1
        while (left.isNotEmpty()) {
            val wave = level.freeArrows(left).sorted()
            if (initiallyFree < 0) initiallyFree = wave.size
            if (wave.isEmpty()) break
            depth++
            left.removeAll(wave.toSet())
            order += wave
        }
        return SolveResult(
            solvable = left.isEmpty(),
            order = order,
            depth = depth,
            initiallyFree = initiallyFree.coerceAtLeast(0),
            stuck = left,
        )
    }

    fun isSolvable(level: Level, remaining: Set<Int> = level.ids): Boolean = solve(level, remaining).solvable
}
