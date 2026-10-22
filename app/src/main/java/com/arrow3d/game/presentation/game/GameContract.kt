package com.arrow3d.game.presentation.game

import androidx.compose.runtime.Immutable
import com.arrow3d.domain.engine.PuzzleState
import com.arrow3d.domain.engine.PuzzleStatus
import com.arrow3d.domain.generator.Difficulty

@Immutable
data class GameState(
    val difficulty: Difficulty,
    val levelNumber: Int = 1,
    val isLoading: Boolean = true,
    val puzzle: PuzzleState? = null,
    /** Changes every time a puzzle is (re)started, so the UI resets its animations and camera. */
    val session: Int = 0,
    val hintArrowId: Int? = null,
    val vibrationEnabled: Boolean = true,
) {
    val status: PuzzleStatus get() = puzzle?.status ?: PuzzleStatus.PLAYING
    val lives: Int? get() = puzzle?.lives
    val maxLives: Int? get() = puzzle?.maxLives
    val removed: Int get() = puzzle?.removedCount ?: 0
    val total: Int get() = puzzle?.total ?: 0
    val progress: Float get() = if (total == 0) 0f else removed.toFloat() / total
    val canUndo: Boolean get() = puzzle?.canUndo == true
    val canHint: Boolean get() = puzzle?.canHint == true
    val undosLeft: Int? get() = puzzle?.undosLeft
    val hintsLeft: Int? get() = puzzle?.hintsLeft
    val stars: Int get() = puzzle?.stars ?: 0
    val livesLost: Int get() = puzzle?.livesLost ?: 0
}

sealed interface GameEvent {
    data class OnArrowTapped(val arrowId: Int, val ambiguous: Boolean) : GameEvent
    data object OnUndo : GameEvent
    data object OnHint : GameEvent
    data object OnRestart : GameEvent
    data object OnNextLevel : GameEvent
    data object OnBack : GameEvent
}

/** Visual effects the cube view animates. They are one-time events, not state. */
sealed interface ArrowFx {
    data class Released(val arrowId: Int, val newlyFree: Set<Int>, val levelComplete: Boolean) : ArrowFx
    data class Blocked(val arrowId: Int, val blockerId: Int) : ArrowFx
    data class Restored(val arrowId: Int) : ArrowFx
}

sealed interface GameSideEffect {
    data class Fx(val fx: ArrowFx) : GameSideEffect
    data object NavigateBack : GameSideEffect
}
