package com.arrow3d.game.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arrow3d.domain.engine.PuzzleEngine
import com.arrow3d.domain.engine.TapOutcome
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.generator.GeneratorConfig
import com.arrow3d.domain.generator.LevelGenerator
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.game.data.IProgressRepository
import com.arrow3d.game.haptics.HapticEvent
import com.arrow3d.game.haptics.IHaptics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameViewModel(
    private val difficulty: Difficulty,
    private val progressRepository: IProgressRepository,
    private val haptics: IHaptics,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Monotonic clock (ms) for the combo window. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {

    private val _state = MutableStateFlow(GameState(difficulty = difficulty))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<GameSideEffect>(extraBufferCapacity = 32)
    val sideEffects: SharedFlow<GameSideEffect> = _sideEffects.asSharedFlow()

    private val generator = LevelGenerator(GeneratorConfig.from(difficulty))

    init {
        viewModelScope.launch {
            progressRepository.observeVibrationEnabled().collect { enabled ->
                _state.update { it.copy(vibrationEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            loadLevel(progressRepository.currentLevel(difficulty))
        }
    }

    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.OnArrowTapped -> onArrowTapped(event.arrowId, event.ambiguous)
            GameEvent.OnUndo -> onUndo()
            GameEvent.OnHint -> onHint()
            GameEvent.OnRestart -> onRestart()
            GameEvent.OnNextLevel -> viewModelScope.launch { loadLevel(_state.value.levelNumber + 1) }
            GameEvent.OnBack -> _sideEffects.tryEmit(GameSideEffect.NavigateBack)
        }
    }

    private suspend fun loadLevel(number: Int) {
        _state.update { it.copy(isLoading = true, levelNumber = number, hintArrowId = null) }
        val generated = withContext(computeDispatcher) {
            generator.generate(LevelGenerator.seedFor(difficulty, number))
        }
        val puzzle = PuzzleEngine.start(generated.level, difficulty.lives, difficulty.undos, difficulty.hints)
        _state.update {
            it.copy(isLoading = false, puzzle = puzzle, session = it.session + 1, hintArrowId = null)
        }
    }

    private fun onArrowTapped(arrowId: Int, ambiguous: Boolean) {
        val puzzle = _state.value.puzzle ?: return
        val result = PuzzleEngine.tap(puzzle, arrowId, ambiguous, nowMillis = clock())
        when (val outcome = result.outcome) {
            is TapOutcome.Released -> {
                _state.update { it.copy(puzzle = result.state, hintArrowId = null, lastPoints = outcome.points) }
                emitFx(ArrowFx.Released(outcome.arrowId, outcome.newlyFree, outcome.levelComplete))
                val arrow = puzzle.level.arrow(arrowId)
                vibrate(HapticEvent.Released(longArrow = arrow.length >= 3))
                if (arrow.kind == ArrowKind.TRAVELER) vibrateLater(TRAVELER_FOLD_DELAY_MS, HapticEvent.TravelerFold)
                if (outcome.newlyFree.size >= 2) vibrateLater(CHAIN_DELAY_MS, HapticEvent.Chain(outcome.newlyFree.size))
                if (outcome.combo >= 2) vibrateLater(COMBO_DELAY_MS, HapticEvent.Combo(outcome.combo))
                if (outcome.levelComplete) onLevelComplete()
            }
            is TapOutcome.Blocked -> {
                _state.update { it.copy(puzzle = result.state) }
                emitFx(ArrowFx.Blocked(outcome.arrowId, outcome.blockerId))
                vibrate(HapticEvent.Blocked)
                if (outcome.outOfLives) vibrateLater(OUT_OF_LIVES_DELAY_MS, HapticEvent.OutOfLives)
            }
            TapOutcome.Ignored -> Unit
        }
    }

    private fun onLevelComplete() {
        vibrateLater(LEVEL_COMPLETE_DELAY_MS, HapticEvent.LevelComplete)
        val next = _state.value.levelNumber + 1
        viewModelScope.launch { progressRepository.setCurrentLevel(difficulty, next) }
    }

    private fun onUndo() {
        val puzzle = _state.value.puzzle ?: return
        val (newState, restored) = PuzzleEngine.undo(puzzle)
        if (restored == null) return
        _state.update { it.copy(puzzle = newState, hintArrowId = null) }
        emitFx(ArrowFx.Restored(restored))
    }

    private fun onHint() {
        val puzzle = _state.value.puzzle ?: return
        if (_state.value.hintArrowId != null) return
        val (newState, hint) = PuzzleEngine.hint(puzzle)
        if (hint == null) return
        _state.update { it.copy(puzzle = newState, hintArrowId = hint) }
    }

    private fun onRestart() {
        val puzzle = _state.value.puzzle ?: return
        _state.update {
            it.copy(puzzle = PuzzleEngine.restart(puzzle), session = it.session + 1, hintArrowId = null)
        }
    }

    private fun emitFx(fx: ArrowFx) {
        _sideEffects.tryEmit(GameSideEffect.Fx(fx))
    }

    private fun vibrate(event: HapticEvent) {
        if (_state.value.vibrationEnabled) haptics.play(event)
    }

    private fun vibrateLater(delayMs: Long, event: HapticEvent) {
        viewModelScope.launch {
            delay(delayMs)
            vibrate(event)
        }
    }

    private companion object {
        const val TRAVELER_FOLD_DELAY_MS = 140L
        const val CHAIN_DELAY_MS = 180L
        const val COMBO_DELAY_MS = 90L
        const val LEVEL_COMPLETE_DELAY_MS = 350L
        const val OUT_OF_LIVES_DELAY_MS = 250L
    }
}
