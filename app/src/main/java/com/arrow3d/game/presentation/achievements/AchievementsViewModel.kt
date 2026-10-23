package com.arrow3d.game.presentation.achievements

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arrow3d.domain.progress.Achievement
import com.arrow3d.domain.progress.PlayerStats
import com.arrow3d.game.data.IProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class AchievementsState(
    val stats: PlayerStats = PlayerStats(),
    val unlocked: Set<Achievement> = emptySet(),
) {
    val all: List<Achievement> get() = Achievement.entries
}

class AchievementsViewModel(progressRepository: IProgressRepository) : ViewModel() {

    val state: StateFlow<AchievementsState> = progressRepository.observeStats()
        .map { stats -> AchievementsState(stats, Achievement.unlocked(stats)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsState())
}
