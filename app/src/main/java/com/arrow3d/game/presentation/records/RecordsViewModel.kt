package com.arrow3d.game.presentation.records

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arrow3d.domain.modes.Leaderboard
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.game.data.IOnlineRanking
import com.arrow3d.game.data.IProgressRepository
import com.arrow3d.game.data.OnlineAccount
import com.arrow3d.online.ApiErrors
import com.arrow3d.online.ApiResult
import com.arrow3d.online.LeaderboardResponse
import com.arrow3d.online.NameRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class RecordsTab { LOCAL, WORLD }

enum class JoinError { INVALID_NAME, CONNECTION }

@Immutable
data class WorldState(
    val configured: Boolean = false,
    /** False until the stored account has been read, so the join form does not flash. */
    val accountLoaded: Boolean = false,
    val account: OnlineAccount? = null,
    val nameInput: String = "",
    val joining: Boolean = false,
    val joinError: JoinError? = null,
    val mode: PlayMode = PlayMode.Daily,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val board: LeaderboardResponse? = null,
)

@Immutable
data class RecordsState(
    /** Every mode in display order, with its board (possibly empty). */
    val boards: List<Pair<PlayMode, Leaderboard>> = emptyList(),
    val tab: RecordsTab = RecordsTab.LOCAL,
    val world: WorldState = WorldState(),
)

sealed interface RecordsEvent {
    data class OnTabSelected(val tab: RecordsTab) : RecordsEvent
    data class OnNameChanged(val name: String) : RecordsEvent
    data object OnJoin : RecordsEvent
    data class OnWorldModeSelected(val mode: PlayMode) : RecordsEvent
    data object OnRetry : RecordsEvent
}

/** Local records and the online ranking. */
class RecordsViewModel(
    progressRepository: IProgressRepository,
    private val online: IOnlineRanking,
    private val today: () -> Long = { LocalDate.now().toEpochDay() },
) : ViewModel() {

    private val _state = MutableStateFlow(RecordsState(world = WorldState(configured = online.isConfigured)))
    val state: StateFlow<RecordsState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            progressRepository.observeLeaderboards().collect { boards ->
                _state.update { s -> s.copy(boards = DISPLAY_ORDER.map { it to (boards[it.key] ?: Leaderboard()) }) }
            }
        }
        if (online.isConfigured) {
            viewModelScope.launch {
                online.observeAccount().collect { account ->
                    val joinedNow = account != null && _state.value.world.account == null
                    updateWorld { it.copy(accountLoaded = true, account = account) }
                    if (joinedNow) {
                        online.flushPending()
                        if (_state.value.tab == RecordsTab.WORLD) loadBoard()
                    }
                }
            }
        }
    }

    fun onEvent(event: RecordsEvent) {
        when (event) {
            is RecordsEvent.OnTabSelected -> {
                _state.update { it.copy(tab = event.tab) }
                if (event.tab == RecordsTab.WORLD && _state.value.world.board == null) loadBoard()
            }
            is RecordsEvent.OnNameChanged -> updateWorld { it.copy(nameInput = event.name.take(NameRules.MAX + 4), joinError = null) }
            RecordsEvent.OnJoin -> join()
            is RecordsEvent.OnWorldModeSelected -> {
                updateWorld { it.copy(mode = event.mode, board = null) }
                loadBoard()
            }
            RecordsEvent.OnRetry -> loadBoard()
        }
    }

    private fun join() {
        val world = _state.value.world
        if (world.joining) return
        if (!NameRules.isValid(world.nameInput)) {
            updateWorld { it.copy(joinError = JoinError.INVALID_NAME) }
            return
        }
        updateWorld { it.copy(joining = true, joinError = null) }
        viewModelScope.launch {
            val error = when (val result = online.join(NameRules.normalize(world.nameInput))) {
                is ApiResult.Ok -> null
                is ApiResult.Failed -> if (result.error == ApiErrors.INVALID_NAME) JoinError.INVALID_NAME else JoinError.CONNECTION
                is ApiResult.Unreachable -> JoinError.CONNECTION
            }
            // On success the account flow fills in the account and loads the board.
            updateWorld { it.copy(joining = false, joinError = error) }
        }
    }

    private fun loadBoard() {
        if (!online.isConfigured) return
        val mode = _state.value.world.mode
        loadJob?.cancel()
        updateWorld { it.copy(loading = true, failed = false) }
        loadJob = viewModelScope.launch {
            when (val result = online.leaderboard(mode.key, today())) {
                is ApiResult.Ok -> updateWorld { it.copy(loading = false, board = result.value) }
                else -> updateWorld { it.copy(loading = false, failed = true) }
            }
        }
    }

    private fun updateWorld(change: (WorldState) -> WorldState) = _state.update { it.copy(world = change(it.world)) }

    companion object {
        val DISPLAY_ORDER: List<PlayMode> = listOf(PlayMode.Daily, PlayMode.Adaptive) + PlayMode.all.filterIsInstance<PlayMode.Classic>()
    }
}
