package com.arrow3d.game.presentation.game

import androidx.compose.runtime.Immutable
import com.arrow3d.domain.engine.PuzzleState
import com.arrow3d.domain.engine.PuzzleStatus
import com.arrow3d.domain.modes.LessonTopic
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.progress.Achievement

@Immutable
data class GameState(
    val mode: PlayMode,
    val levelNumber: Int = 1,
    val gridSize: Int = 0,
    val isLoading: Boolean = true,
    val puzzle: PuzzleState? = null,
    /** Changes every time a puzzle is (re)started, so the UI resets its animations and camera. */
    val session: Int = 0,
    val hintArrowId: Int? = null,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val colorBlind: Boolean = false,
    /** Points the last release scored, shown next to the combo. */
    val lastPoints: Int = 0,
    /** Place of this win in the mode's local ranking (1 = new record), null when it did not make it. */
    val recordRank: Int? = null,
    /** Daily challenge streak after this win. */
    val dailyStreak: Int = 0,
    val online: OnlineResult = OnlineResult.None,
    /** Tutorial only: what the current lesson teaches, and how many lessons there are. */
    val lesson: LessonTopic? = null,
    val lessonCount: Int = 0,
    /** Achievements unlocked by the attempt that just ended. */
    val achievements: List<Achievement> = emptyList(),
) {
    val isDaily: Boolean get() = mode == PlayMode.Daily
    val isTutorial: Boolean get() = mode == PlayMode.Tutorial
    val isLastLesson: Boolean get() = isTutorial && levelNumber >= lessonCount

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
    val combo: Int get() = puzzle?.combo ?: 0
    val bestCombo: Int get() = puzzle?.bestCombo ?: 0
    val score: Int get() = puzzle?.score ?: 0

    /** Clock time of the last release; the combo badge drains from there. */
    val comboStartedAt: Long? get() = puzzle?.lastReleaseAt
}

/** Where the last win stands on the online ranking. */
sealed interface OnlineResult {
    data object None : OnlineResult
    data object Sending : OnlineResult
    data class Ranked(val rank: Int) : OnlineResult

    /** Offline: it will be sent later. */
    data object Queued : OnlineResult
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
