package com.arrow3d.game.presentation.select

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.modes.AdaptiveProgress
import com.arrow3d.domain.modes.DailyRecord
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.modes.Tutorial
import com.arrow3d.game.data.IProgressRepository
import com.arrow3d.game.data.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@Immutable
data class ModeSelectState(
    val levels: Map<Difficulty, Int> = Difficulty.entries.associateWith { 1 },
    val settings: Settings = Settings(),
    val adaptive: AdaptiveProgress = AdaptiveProgress(),
    val daily: DailyRecord = DailyRecord(),
    val today: Long = 0,
    /** Best score per [PlayMode.key]; missing when the mode has no record yet. */
    val bestScores: Map<String, Int> = emptyMap(),
    /** Tutorial lessons completed. */
    val tutorialCompleted: Int = 0,
) {
    val tutorialDone: Boolean get() = tutorialCompleted >= Tutorial.size

    val dailyDoneToday: Boolean get() = daily.completedOn(today)
    val dailyStreak: Int get() = daily.currentStreak(today)
}

sealed interface ModeSelectEvent {
    data class OnModeSelected(val mode: PlayMode) : ModeSelectEvent
    data object OnRecordsSelected : ModeSelectEvent
    data object OnAchievementsSelected : ModeSelectEvent
    data class OnMusicToggled(val enabled: Boolean) : ModeSelectEvent
    data class OnVibrationToggled(val enabled: Boolean) : ModeSelectEvent
    data class OnSoundToggled(val enabled: Boolean) : ModeSelectEvent
    data class OnColorBlindToggled(val enabled: Boolean) : ModeSelectEvent
}

sealed interface ModeSelectSideEffect {
    data class OpenGame(val mode: PlayMode) : ModeSelectSideEffect
    data object OpenRecords : ModeSelectSideEffect
    data object OpenAchievements : ModeSelectSideEffect
}

class ModeSelectViewModel(
    private val progressRepository: IProgressRepository,
    private val today: () -> Long = { LocalDate.now().toEpochDay() },
) : ViewModel() {

    private val _state = MutableStateFlow(ModeSelectState(today = today()))
    val state: StateFlow<ModeSelectState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<ModeSelectSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<ModeSelectSideEffect> = _sideEffects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                progressRepository.observeLevels(),
                progressRepository.observeSettings(),
                progressRepository.observeAdaptive(),
                progressRepository.observeDaily(),
                progressRepository.observeLeaderboards(),
            ) { levels, settings, adaptive, daily, boards ->
                ModeSelectState(
                    levels = levels,
                    settings = settings,
                    adaptive = adaptive,
                    daily = daily,
                    // Re-read the day on every update, so the screen is right after midnight.
                    today = today(),
                    bestScores = boards.mapNotNull { (key, board) -> board.best?.let { key to it.score } }.toMap(),
                )
            }.collect { s -> _state.update { s.copy(tutorialCompleted = it.tutorialCompleted) } }
        }
        viewModelScope.launch {
            progressRepository.observeTutorialProgress().collect { n -> _state.update { it.copy(tutorialCompleted = n) } }
        }
    }

    fun onEvent(event: ModeSelectEvent) {
        when (event) {
            is ModeSelectEvent.OnModeSelected -> _sideEffects.tryEmit(ModeSelectSideEffect.OpenGame(event.mode))
            ModeSelectEvent.OnRecordsSelected -> _sideEffects.tryEmit(ModeSelectSideEffect.OpenRecords)
            ModeSelectEvent.OnAchievementsSelected -> _sideEffects.tryEmit(ModeSelectSideEffect.OpenAchievements)
            is ModeSelectEvent.OnMusicToggled -> viewModelScope.launch { progressRepository.setMusicEnabled(event.enabled) }
            is ModeSelectEvent.OnVibrationToggled -> viewModelScope.launch { progressRepository.setVibrationEnabled(event.enabled) }
            is ModeSelectEvent.OnSoundToggled -> viewModelScope.launch { progressRepository.setSoundEnabled(event.enabled) }
            is ModeSelectEvent.OnColorBlindToggled -> viewModelScope.launch { progressRepository.setColorBlindEnabled(event.enabled) }
        }
    }
}
