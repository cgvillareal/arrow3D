package com.arrow3d.game.presentation.records

import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.modes.AdaptiveProgress
import com.arrow3d.domain.modes.DailyRecord
import com.arrow3d.domain.modes.Leaderboard
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.modes.ScoreEntry
import com.arrow3d.game.data.IOnlineRanking
import com.arrow3d.game.data.IProgressRepository
import com.arrow3d.game.data.OnlineAccount
import com.arrow3d.game.data.Settings
import com.arrow3d.game.data.SubmitOutcome
import com.arrow3d.online.ApiErrors
import com.arrow3d.online.ApiResult
import com.arrow3d.online.LeaderboardEntry
import com.arrow3d.online.LeaderboardResponse
import com.arrow3d.online.RunSubmission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class LocalOnly(private val boards: Map<String, Leaderboard>) : IProgressRepository {
    override fun observeLevels(): Flow<Map<Difficulty, Int>> = flowOf(emptyMap())
    override suspend fun currentLevel(difficulty: Difficulty) = 1
    override suspend fun setCurrentLevel(difficulty: Difficulty, level: Int) = Unit
    override fun observeSettings(): Flow<Settings> = flowOf(Settings())
    override suspend fun setVibrationEnabled(enabled: Boolean) = Unit
    override suspend fun setSoundEnabled(enabled: Boolean) = Unit
    override suspend fun setColorBlindEnabled(enabled: Boolean) = Unit
    override fun observeAdaptive(): Flow<AdaptiveProgress> = flowOf(AdaptiveProgress())
    override suspend fun setAdaptive(progress: AdaptiveProgress) = Unit
    override fun observeDaily(): Flow<DailyRecord> = flowOf(DailyRecord())
    override suspend fun setDaily(record: DailyRecord) = Unit
    override fun observeLeaderboards(): Flow<Map<String, Leaderboard>> = flowOf(boards)
    override suspend fun addScore(modeKey: String, entry: ScoreEntry): Int? = null
}

private class FakeServer(override val isConfigured: Boolean = true) : IOnlineRanking {
    val account = MutableStateFlow<OnlineAccount?>(null)
    var reachable = true
    val requested = mutableListOf<Pair<String, Long>>()
    override fun observeAccount(): Flow<OnlineAccount?> = account
    override suspend fun join(name: String): ApiResult<OnlineAccount> {
        if (!reachable) return ApiResult.Unreachable(java.io.IOException("offline"))
        if (name == "taken") return ApiResult.Failed(400, ApiErrors.INVALID_NAME, null)
        return OnlineAccount("me", name, "t").also { account.value = it }.let { ApiResult.Ok(it) }
    }
    override suspend fun submit(run: RunSubmission) = SubmitOutcome.Skipped
    override suspend fun leaderboard(modeKey: String, day: Long): ApiResult<LeaderboardResponse> {
        requested += modeKey to day
        if (!reachable) return ApiResult.Unreachable(java.io.IOException("offline"))
        val entry = LeaderboardEntry(1, "other", "Beto", 900, 4, 2, day)
        val me = LeaderboardEntry(30, "me", "Ana", 100, 1, 1, day)
        return ApiResult.Ok(LeaderboardResponse(modeKey, day, listOf(entry), me))
    }
    override suspend fun flushPending() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val local = mapOf("easy" to Leaderboard(listOf(ScoreEntry(500, 3, 2, 10))))

    @Test
    fun `local tab lists every mode with its board`() = runTest(dispatcher) {
        val vm = RecordsViewModel(LocalOnly(local), FakeServer(), today = { 99 })
        advanceUntilIdle()
        val boards = vm.state.value.boards
        assertEquals(PlayMode.Daily, boards.first().first)
        assertEquals(500, boards.first { it.first.key == "easy" }.second.best?.score)
    }

    @Test
    fun `joining validates the name and then loads today's daily board`() = runTest(dispatcher) {
        val server = FakeServer()
        val vm = RecordsViewModel(LocalOnly(local), server, today = { 99 })
        advanceUntilIdle()
        vm.onEvent(RecordsEvent.OnTabSelected(RecordsTab.WORLD))
        advanceUntilIdle()
        assertTrue(vm.state.value.world.accountLoaded)
        assertNull(vm.state.value.world.account)

        vm.onEvent(RecordsEvent.OnNameChanged("ab"))
        vm.onEvent(RecordsEvent.OnJoin)
        assertEquals(JoinError.INVALID_NAME, vm.state.value.world.joinError)

        vm.onEvent(RecordsEvent.OnNameChanged("taken"))
        vm.onEvent(RecordsEvent.OnJoin)
        advanceUntilIdle()
        assertEquals(JoinError.INVALID_NAME, vm.state.value.world.joinError)

        vm.onEvent(RecordsEvent.OnNameChanged("  Ana  "))
        vm.onEvent(RecordsEvent.OnJoin)
        advanceUntilIdle()
        val world = vm.state.value.world
        assertEquals("Ana", world.account?.name)
        assertEquals("daily" to 99L, server.requested.last())
        assertEquals(listOf("Beto"), world.board?.entries?.map { it.name })
        assertEquals(30, world.board?.me?.rank)
    }

    @Test
    fun `switching modes reloads, failures can be retried`() = runTest(dispatcher) {
        val server = FakeServer().apply { account.value = OnlineAccount("me", "Ana", "t") }
        val vm = RecordsViewModel(LocalOnly(local), server, today = { 99 })
        advanceUntilIdle()
        server.reachable = false
        vm.onEvent(RecordsEvent.OnTabSelected(RecordsTab.WORLD))
        advanceUntilIdle()
        assertTrue(vm.state.value.world.failed)

        server.reachable = true
        vm.onEvent(RecordsEvent.OnRetry)
        advanceUntilIdle()
        assertFalse(vm.state.value.world.failed)

        vm.onEvent(RecordsEvent.OnWorldModeSelected(PlayMode.Classic(Difficulty.HARD)))
        advanceUntilIdle()
        assertEquals("hard", server.requested.last().first)
        assertEquals("hard", vm.state.value.world.board?.mode)
    }

    @Test
    fun `without a server url the world tab only explains it`() = runTest(dispatcher) {
        val server = FakeServer(isConfigured = false)
        val vm = RecordsViewModel(LocalOnly(local), server, today = { 99 })
        vm.onEvent(RecordsEvent.OnTabSelected(RecordsTab.WORLD))
        advanceUntilIdle()
        assertFalse(vm.state.value.world.configured)
        assertTrue(server.requested.isEmpty())
    }
}
