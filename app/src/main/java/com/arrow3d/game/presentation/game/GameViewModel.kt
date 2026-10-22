package com.arrow3d.game.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arrow3d.domain.engine.PuzzleEngine
import com.arrow3d.domain.engine.PuzzleState
import com.arrow3d.domain.engine.PuzzleStatus
import com.arrow3d.domain.engine.TapOutcome
import com.arrow3d.domain.generator.LevelGenerator
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.domain.modes.AdaptiveDifficulty
import com.arrow3d.domain.modes.DailyChallenge
import com.arrow3d.domain.modes.LevelResult
import com.arrow3d.domain.modes.LevelSpec
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.modes.ScoreEntry
import com.arrow3d.game.audio.ISoundPlayer
import com.arrow3d.game.audio.SoundEvent
import com.arrow3d.game.data.IProgressRepository
import com.arrow3d.game.haptics.HapticEvent
import com.arrow3d.game.haptics.IHaptics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class GameViewModel(
    private val mode: PlayMode,
    private val progressRepository: IProgressRepository,
    private val haptics: IHaptics,
    private val sound: ISoundPlayer,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Monotonic clock (ms) for the combo window. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    /** Today as an epoch day, for the daily challenge and record dates. */
    private val today: () -> Long = { LocalDate.now().toEpochDay() },
) : ViewModel() {

    private val _state = MutableStateFlow(GameState(mode = mode))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<GameSideEffect>(extraBufferCapacity = 32)
    val sideEffects: SharedFlow<GameSideEffect> = _sideEffects.asSharedFlow()

    /** Day the loaded daily challenge belongs to (it may differ from [today] after midnight). */
    private var loadedDay: Long = 0
    /** The result of the current attempt was already saved (so replays and retries are not counted twice). */
    private var resultSaved = false
    private var helpUsed = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            progressRepository.observeSettings().collect { s ->
                _state.update { it.copy(vibrationEnabled = s.vibration, soundEnabled = s.sound, colorBlind = s.colorBlind) }
            }
        }
        viewModelScope.launch { loadCurrent() }
    }

    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.OnArrowTapped -> onArrowTapped(event.arrowId, event.ambiguous)
            GameEvent.OnUndo -> onUndo()
            GameEvent.OnHint -> onHint()
            GameEvent.OnRestart -> onRestart()
            GameEvent.OnNextLevel -> viewModelScope.launch {
                // Progress is saved when the level ends; read it back only once it is written.
                saveJob?.join()
                loadCurrent()
            }
            GameEvent.OnBack -> _sideEffects.tryEmit(GameSideEffect.NavigateBack)
        }
    }

    /** Loads the level the player is on in this mode. */
    private suspend fun loadCurrent() {
        when (mode) {
            is PlayMode.Classic -> {
                val number = progressRepository.currentLevel(mode.difficulty)
                loadLevel(number, LevelSpec.classic(mode.difficulty, number))
            }
            PlayMode.Adaptive -> {
                val progress = progressRepository.observeAdaptive().first()
                loadLevel(progress.level, AdaptiveDifficulty.spec(progress))
            }
            PlayMode.Daily -> {
                loadedDay = today()
                loadLevel(1, DailyChallenge.spec(loadedDay))
            }
        }
    }

    private suspend fun loadLevel(number: Int, spec: LevelSpec) {
        _state.update { it.copy(isLoading = true, levelNumber = number, gridSize = spec.gridSize, hintArrowId = null) }
        val generated = withContext(computeDispatcher) { LevelGenerator(spec.config).generate(spec.seed) }
        val puzzle = PuzzleEngine.start(generated.level, spec.lives, spec.undos, spec.hints)
        startAttempt()
        _state.update {
            it.copy(isLoading = false, puzzle = puzzle, session = it.session + 1, hintArrowId = null)
        }
    }

    private fun startAttempt() {
        resultSaved = false
        helpUsed = false
        _state.update { it.copy(recordRank = null, lastPoints = 0) }
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
                play(SoundEvent.Released(outcome.combo))
                if (arrow.kind == ArrowKind.TRAVELER) {
                    later(TRAVELER_FOLD_DELAY_MS) {
                        vibrate(HapticEvent.TravelerFold)
                        play(SoundEvent.TravelerFold)
                    }
                }
                if (outcome.newlyFree.size >= 2) {
                    later(CHAIN_DELAY_MS) {
                        vibrate(HapticEvent.Chain(outcome.newlyFree.size))
                        play(SoundEvent.Chain)
                    }
                }
                if (outcome.combo >= 2) later(COMBO_DELAY_MS) { vibrate(HapticEvent.Combo(outcome.combo)) }
                if (outcome.levelComplete) onLevelComplete(result.state)
            }
            is TapOutcome.Blocked -> {
                _state.update { it.copy(puzzle = result.state) }
                emitFx(ArrowFx.Blocked(outcome.arrowId, outcome.blockerId))
                vibrate(HapticEvent.Blocked)
                play(SoundEvent.Blocked)
                if (outcome.outOfLives) {
                    later(OUT_OF_LIVES_DELAY_MS) {
                        vibrate(HapticEvent.OutOfLives)
                        play(SoundEvent.OutOfLives)
                    }
                    onLevelLost(result.state)
                }
            }
            TapOutcome.Ignored -> Unit
        }
    }

    private fun onLevelComplete(puzzle: PuzzleState) {
        later(LEVEL_COMPLETE_DELAY_MS) {
            vibrate(HapticEvent.LevelComplete)
            play(SoundEvent.LevelComplete)
        }
        if (resultSaved) return
        resultSaved = true
        val levelNumber = _state.value.levelNumber
        val day = if (mode == PlayMode.Daily) loadedDay else today()
        saveJob = viewModelScope.launch {
            when (mode) {
                is PlayMode.Classic -> progressRepository.setCurrentLevel(mode.difficulty, levelNumber + 1)
                PlayMode.Adaptive -> saveAdaptive(levelNumber, LevelResult(won = true, livesLost = puzzle.livesLost, helpUsed = helpUsed))
                PlayMode.Daily -> {
                    val record = progressRepository.observeDaily().first()
                    val updated = DailyChallenge.complete(record, day, puzzle.score)
                    progressRepository.setDaily(updated)
                    _state.update { it.copy(dailyStreak = updated.streak) }
                }
            }
            val rank = progressRepository.addScore(mode.key, ScoreEntry(puzzle.score, puzzle.bestCombo, levelNumber, day))
            _state.update { it.copy(recordRank = rank) }
        }
    }

    private fun onLevelLost(puzzle: PuzzleState) {
        if (resultSaved || mode != PlayMode.Adaptive) return
        resultSaved = true
        val levelNumber = _state.value.levelNumber
        saveJob = viewModelScope.launch {
            saveAdaptive(levelNumber, LevelResult(won = false, livesLost = puzzle.livesLost, helpUsed = helpUsed))
        }
    }

    /** Only the level the player is actually on moves the rating; replaying an old one does not. */
    private suspend fun saveAdaptive(levelNumber: Int, result: LevelResult) {
        val progress = progressRepository.observeAdaptive().first()
        if (progress.level != levelNumber) return
        progressRepository.setAdaptive(AdaptiveDifficulty.next(progress, result))
    }

    private fun onUndo() {
        val puzzle = _state.value.puzzle ?: return
        val (newState, restored) = PuzzleEngine.undo(puzzle)
        if (restored == null) return
        helpUsed = true
        _state.update { it.copy(puzzle = newState, hintArrowId = null) }
        emitFx(ArrowFx.Restored(restored))
    }

    private fun onHint() {
        val puzzle = _state.value.puzzle ?: return
        if (_state.value.hintArrowId != null) return
        val (newState, hint) = PuzzleEngine.hint(puzzle)
        if (hint == null) return
        helpUsed = true
        _state.update { it.copy(puzzle = newState, hintArrowId = hint) }
    }

    private fun onRestart() {
        val puzzle = _state.value.puzzle ?: return
        val wasPlaying = puzzle.status == PuzzleStatus.PLAYING
        // A retry after losing, or a replay after winning, is a new attempt; restarting midway is not.
        if (!wasPlaying) startAttempt()
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

    private fun play(event: SoundEvent) {
        if (_state.value.soundEnabled) sound.play(event)
    }

    private fun later(delayMs: Long, action: () -> Unit) {
        viewModelScope.launch {
            delay(delayMs)
            action()
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
