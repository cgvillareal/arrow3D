package com.arrow3d.domain.engine

import com.arrow3d.domain.model.Level

enum class PuzzleStatus { PLAYING, WON, LOST }

/**
 * Immutable state of a level being played. Null limits mean "unlimited" (Zen mode).
 */
data class PuzzleState(
    val level: Level,
    val remaining: Set<Int>,
    val lives: Int?,
    val maxLives: Int?,
    val undosLeft: Int?,
    val hintsLeft: Int?,
    /** Removed arrows, most recent last (used by undo). */
    val history: List<Int> = emptyList(),
    val livesLost: Int = 0,
) {
    val status: PuzzleStatus
        get() = when {
            remaining.isEmpty() -> PuzzleStatus.WON
            lives == 0 -> PuzzleStatus.LOST
            else -> PuzzleStatus.PLAYING
        }

    val total: Int get() = level.arrows.size
    val removedCount: Int get() = total - remaining.size
    val canUndo: Boolean get() = status == PuzzleStatus.PLAYING && history.isNotEmpty() && (undosLeft == null || undosLeft > 0)
    val canHint: Boolean get() = status == PuzzleStatus.PLAYING && (hintsLeft == null || hintsLeft > 0)

    /** 3 stars without losing lives, 2 with one life lost, 1 otherwise. */
    val stars: Int get() = when (livesLost) {
        0 -> 3
        1 -> 2
        else -> 1
    }
}

sealed interface TapOutcome {
    /** The arrow leaves. [newlyFree] are arrows that were blocked and can now leave (chain glow). */
    data class Released(val arrowId: Int, val newlyFree: Set<Int>, val levelComplete: Boolean) : TapOutcome

    /** Something is in the way. [lifeLost] is false in Zen or when the tap was ambiguous. */
    data class Blocked(val arrowId: Int, val blockerId: Int, val lifeLost: Boolean, val outOfLives: Boolean) : TapOutcome

    data object Ignored : TapOutcome
}

data class TapResult(val state: PuzzleState, val outcome: TapOutcome)

/** Pure rules of a level: every function takes a state and returns a new one. */
object PuzzleEngine {

    fun start(level: Level, lives: Int?, undos: Int?, hints: Int?): PuzzleState = PuzzleState(
        level = level,
        remaining = level.ids,
        lives = lives,
        maxLives = lives,
        undosLeft = undos,
        hintsLeft = hints,
    )

    /**
     * @param ambiguous the touch could have meant another arrow; a blocked ambiguous tap
     * never costs a life (design doc, section 7).
     */
    fun tap(state: PuzzleState, arrowId: Int, ambiguous: Boolean = false): TapResult {
        if (state.status != PuzzleStatus.PLAYING || arrowId !in state.remaining) {
            return TapResult(state, TapOutcome.Ignored)
        }
        val level = state.level
        val blocker = level.blockerOf(arrowId, state.remaining)
        if (blocker != null) {
            val costsLife = state.lives != null && !ambiguous
            val newState = if (costsLife) {
                state.copy(lives = state.lives!! - 1, livesLost = state.livesLost + 1)
            } else {
                state
            }
            return TapResult(
                newState,
                TapOutcome.Blocked(arrowId, blocker, lifeLost = costsLife, outOfLives = newState.lives == 0),
            )
        }
        val freeBefore = level.freeArrows(state.remaining)
        val remaining = state.remaining - arrowId
        val freeAfter = level.freeArrows(remaining)
        val newState = state.copy(remaining = remaining, history = state.history + arrowId)
        return TapResult(
            newState,
            TapOutcome.Released(arrowId, newlyFree = freeAfter - freeBefore, levelComplete = remaining.isEmpty()),
        )
    }

    /** Puts the last removed arrow back. Lost lives are not restored. */
    fun undo(state: PuzzleState): Pair<PuzzleState, Int?> {
        if (!state.canUndo) return state to null
        val last = state.history.last()
        return state.copy(
            remaining = state.remaining + last,
            history = state.history.dropLast(1),
            undosLeft = state.undosLeft?.minus(1),
        ) to last
    }

    /**
     * Suggests a free arrow. Any free arrow is safe (removing never blocks), so we pick the one
     * that frees the most others, which also teaches the chain logic.
     */
    fun hint(state: PuzzleState): Pair<PuzzleState, Int?> {
        if (!state.canHint) return state to null
        val level = state.level
        val free = level.freeArrows(state.remaining)
        if (free.isEmpty()) return state to null
        val best = free.maxWith(
            compareBy<Int> { id -> (level.freeArrows(state.remaining - id) - free).size }
                .thenByDescending { it },
        )
        return state.copy(hintsLeft = state.hintsLeft?.minus(1)) to best
    }

    fun restart(state: PuzzleState): PuzzleState =
        start(state.level, state.maxLives, undos = null, hints = null).copy(
            undosLeft = state.undosLeft,
            hintsLeft = state.hintsLeft,
        )
}
