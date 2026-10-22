package com.arrow3d.game.presentation.select

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.game.data.IProgressRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ModeSelectState(
    val levels: Map<Difficulty, Int> = Difficulty.entries.associateWith { 1 },
    val vibrationEnabled: Boolean = true,
)

sealed interface ModeSelectEvent {
    data class OnModeSelected(val difficulty: Difficulty) : ModeSelectEvent
    data class OnVibrationToggled(val enabled: Boolean) : ModeSelectEvent
}

sealed interface ModeSelectSideEffect {
    data class OpenGame(val difficulty: Difficulty) : ModeSelectSideEffect
}

class ModeSelectViewModel(
    private val progressRepository: IProgressRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ModeSelectState())
    val state: StateFlow<ModeSelectState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<ModeSelectSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<ModeSelectSideEffect> = _sideEffects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                progressRepository.observeLevels(),
                progressRepository.observeVibrationEnabled(),
            ) { levels, vibration -> levels to vibration }
                .collect { (levels, vibration) ->
                    _state.update { it.copy(levels = levels, vibrationEnabled = vibration) }
                }
        }
    }

    fun onEvent(event: ModeSelectEvent) {
        when (event) {
            is ModeSelectEvent.OnModeSelected -> _sideEffects.tryEmit(ModeSelectSideEffect.OpenGame(event.difficulty))
            is ModeSelectEvent.OnVibrationToggled -> viewModelScope.launch {
                progressRepository.setVibrationEnabled(event.enabled)
            }
        }
    }
}
