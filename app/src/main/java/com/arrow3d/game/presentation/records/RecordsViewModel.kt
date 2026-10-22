package com.arrow3d.game.presentation.records

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arrow3d.domain.modes.Leaderboard
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.game.data.IProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class RecordsState(
    /** Every mode in display order, with its board (possibly empty). */
    val boards: List<Pair<PlayMode, Leaderboard>> = emptyList(),
)

/** Local ranking. An online ranking needs a backend and is left for a later phase. */
class RecordsViewModel(progressRepository: IProgressRepository) : ViewModel() {

    val state: StateFlow<RecordsState> = progressRepository.observeLeaderboards()
        .map { boards -> RecordsState(DISPLAY_ORDER.map { it to (boards[it.key] ?: Leaderboard()) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsState())

    private companion object {
        val DISPLAY_ORDER: List<PlayMode> = listOf(PlayMode.Daily, PlayMode.Adaptive) + PlayMode.all.filterIsInstance<PlayMode.Classic>()
    }
}
