package com.arrow3d.game.presentation.game

import app.cash.turbine.test
import com.arrow3d.domain.engine.ComboRules
import com.arrow3d.domain.engine.PuzzleStatus
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.modes.AdaptiveDifficulty
import com.arrow3d.domain.modes.AdaptiveProgress
import com.arrow3d.domain.modes.DailyChallenge
import com.arrow3d.domain.modes.DailyRecord
import com.arrow3d.domain.modes.Leaderboard
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.modes.ScoreEntry
import com.arrow3d.domain.solver.Solver
import com.arrow3d.game.audio.ISoundPlayer
import com.arrow3d.game.audio.SoundEvent
import com.arrow3d.game.data.IProgressRepository
import com.arrow3d.game.data.Settings
import com.arrow3d.game.haptics.HapticEvent
import com.arrow3d.game.haptics.IHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeProgressRepository(startLevel: Int = 1) : IProgressRepository {
    val levels = MutableStateFlow(Difficulty.entries.associateWith { startLevel })
    val settings = MutableStateFlow(Settings())
    val adaptive = MutableStateFlow(AdaptiveProgress())
    val daily = MutableStateFlow(DailyRecord())
    val boards = MutableStateFlow<Map<String, Leaderboard>>(emptyMap())
    override fun observeLevels(): Flow<Map<Difficulty, Int>> = levels
    override suspend fun currentLevel(difficulty: Difficulty) = levels.value.getValue(difficulty)
    override suspend fun setCurrentLevel(difficulty: Difficulty, level: Int) {
        levels.value = levels.value + (difficulty to level)
    }
    override fun observeSettings(): Flow<Settings> = settings
    override suspend fun setVibrationEnabled(enabled: Boolean) = settings.update { it.copy(vibration = enabled) }
    override suspend fun setSoundEnabled(enabled: Boolean) = settings.update { it.copy(sound = enabled) }
    override suspend fun setColorBlindEnabled(enabled: Boolean) = settings.update { it.copy(colorBlind = enabled) }
    override fun observeAdaptive(): Flow<AdaptiveProgress> = adaptive
    override suspend fun setAdaptive(progress: AdaptiveProgress) {
        adaptive.value = progress
    }
    override fun observeDaily(): Flow<DailyRecord> = daily
    override suspend fun setDaily(record: DailyRecord) {
        daily.value = record
    }
    override fun observeLeaderboards(): Flow<Map<String, Leaderboard>> = boards
    override suspend fun addScore(modeKey: String, entry: ScoreEntry): Int? {
        val (board, rank) = (boards.value[modeKey] ?: Leaderboard()).add(entry)
        boards.value = boards.value + (modeKey to board)
        return rank
    }
}

private class FakeSound : ISoundPlayer {
    val played = mutableListOf<SoundEvent>()
    override fun play(event: SoundEvent) {
        played += event
    }
}

private class FakeHaptics : IHaptics {
    val played = mutableListOf<HapticEvent>()
    override fun play(event: HapticEvent) {
        played += event
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private lateinit var dispatcher: TestDispatcher
    private lateinit var repository: FakeProgressRepository
    private lateinit var haptics: FakeHaptics
    private lateinit var sound: FakeSound

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        repository = FakeProgressRepository()
        haptics = FakeHaptics()
        sound = FakeSound()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(difficulty: Difficulty = Difficulty.EASY) = viewModel(PlayMode.Classic(difficulty))

    private fun viewModel(mode: PlayMode, now: () -> Long = { 0L }) =
        GameViewModel(mode, repository, haptics, sound, computeDispatcher = dispatcher, clock = now, today = { TODAY })

    private fun solve(vm: GameViewModel) {
        Solver.solve(vm.state.value.puzzle!!.level).order.forEach { vm.onEvent(GameEvent.OnArrowTapped(it, ambiguous = false)) }
    }

    @Test
    fun `loads the saved level and starts playing`() = runTest(dispatcher) {
        repository.levels.value = repository.levels.value + (Difficulty.EASY to 7)
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(7, state.levelNumber)
        assertNotNull(state.puzzle)
        assertEquals(PuzzleStatus.PLAYING, state.status)
        assertEquals(Difficulty.EASY.lives, state.lives)
    }

    @Test
    fun `tapping a free arrow releases it, emits the effect and vibrates`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val puzzle = vm.state.value.puzzle!!
        val free = puzzle.level.freeArrows(puzzle.remaining).first()

        vm.sideEffects.test {
            vm.onEvent(GameEvent.OnArrowTapped(free, ambiguous = false))
            val effect = awaitItem() as GameSideEffect.Fx
            assertEquals(free, (effect.fx as ArrowFx.Released).arrowId)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, vm.state.value.removed)
        assertTrue(haptics.played.first() is HapticEvent.Released)
    }

    @Test
    fun `blocked tap costs a life and highlights the blocker`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val puzzle = vm.state.value.puzzle!!
        val blocked = puzzle.remaining.first { !puzzle.level.isFree(it, puzzle.remaining) }

        vm.sideEffects.test {
            vm.onEvent(GameEvent.OnArrowTapped(blocked, ambiguous = false))
            val fx = (awaitItem() as GameSideEffect.Fx).fx as ArrowFx.Blocked
            assertEquals(puzzle.level.blockerOf(blocked, puzzle.remaining), fx.blockerId)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(Difficulty.EASY.lives!! - 1, vm.state.value.lives)
        assertEquals(HapticEvent.Blocked, haptics.played.first())
    }

    @Test
    fun `solving the level saves progress to the next level`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val order = Solver.solve(vm.state.value.puzzle!!.level).order
        order.forEach { vm.onEvent(GameEvent.OnArrowTapped(it, ambiguous = false)) }
        advanceUntilIdle()
        assertEquals(PuzzleStatus.WON, vm.state.value.status)
        assertEquals(3, vm.state.value.stars)
        assertEquals(2, repository.levels.value.getValue(Difficulty.EASY))
        assertTrue(haptics.played.contains(HapticEvent.LevelComplete))

        vm.onEvent(GameEvent.OnNextLevel)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.levelNumber)
        assertEquals(PuzzleStatus.PLAYING, vm.state.value.status)
    }

    @Test
    fun `no vibration when the setting is off`() = runTest(dispatcher) {
        repository.settings.value = Settings(vibration = false)
        val vm = viewModel()
        advanceUntilIdle()
        val puzzle = vm.state.value.puzzle!!
        vm.onEvent(GameEvent.OnArrowTapped(puzzle.level.freeArrows(puzzle.remaining).first(), ambiguous = false))
        advanceUntilIdle()
        assertTrue(haptics.played.isEmpty())
    }

    @Test
    fun `undo and hint respect their limits`() = runTest(dispatcher) {
        val vm = viewModel(Difficulty.EXPERT) // 1 undo, 1 hint
        advanceUntilIdle()
        vm.onEvent(GameEvent.OnHint)
        val hint = vm.state.value.hintArrowId
        assertNotNull(hint)
        assertEquals(0, vm.state.value.hintsLeft)

        vm.onEvent(GameEvent.OnArrowTapped(hint!!, ambiguous = false))
        assertEquals(null, vm.state.value.hintArrowId)
        vm.onEvent(GameEvent.OnUndo)
        assertEquals(0, vm.state.value.removed)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `quick releases build a combo, a slow one starts over`() = runTest(dispatcher) {
        var now = 10_000L
        val vm = viewModel(PlayMode.Classic(Difficulty.EASY)) { now }
        advanceUntilIdle()
        val order = Solver.solve(vm.state.value.puzzle!!.level).order

        vm.onEvent(GameEvent.OnArrowTapped(order[0], ambiguous = false))
        now += 500
        vm.onEvent(GameEvent.OnArrowTapped(order[1], ambiguous = false))
        assertEquals(2, vm.state.value.combo)
        val base = ComboRules.basePoints(vm.state.value.puzzle!!.level.arrow(order[1]))
        assertEquals(base * 2, vm.state.value.lastPoints)
        advanceUntilIdle()
        assertTrue(haptics.played.contains(HapticEvent.Combo(2)))

        now += ComboRules.WINDOW_MS + 1
        vm.onEvent(GameEvent.OnArrowTapped(order[2], ambiguous = false))
        assertEquals(1, vm.state.value.combo)
        assertEquals(2, vm.state.value.bestCombo)
    }

    @Test
    fun `sounds follow the events and the setting`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val puzzle = vm.state.value.puzzle!!
        val blocked = puzzle.remaining.first { !puzzle.level.isFree(it, puzzle.remaining) }
        vm.onEvent(GameEvent.OnArrowTapped(blocked, ambiguous = false))
        assertEquals(SoundEvent.Blocked, sound.played.last())
        vm.onEvent(GameEvent.OnArrowTapped(puzzle.level.freeArrows(puzzle.remaining).first(), ambiguous = false))
        assertEquals(SoundEvent.Released(1), sound.played.last())

        repository.settings.value = Settings(sound = false)
        advanceUntilIdle()
        val count = sound.played.size
        val now = vm.state.value.puzzle!!
        vm.onEvent(GameEvent.OnArrowTapped(now.level.freeArrows(now.remaining).first(), ambiguous = false))
        advanceUntilIdle()
        assertEquals(count, sound.played.size)
    }

    @Test
    fun `a win is saved to the local ranking`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        solve(vm)
        advanceUntilIdle()
        val board = repository.boards.value.getValue("easy")
        assertEquals(vm.state.value.score, board.best?.score)
        assertEquals(TODAY, board.best?.day)
        assertEquals(1, vm.state.value.recordRank)
    }

    @Test
    fun `adaptive mode advances and raises the rating after a clean win`() = runTest(dispatcher) {
        val vm = viewModel(PlayMode.Adaptive)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.levelNumber)
        assertEquals(AdaptiveDifficulty.spec(AdaptiveProgress()).gridSize, vm.state.value.gridSize)
        solve(vm)
        advanceUntilIdle()
        assertEquals(2, repository.adaptive.value.level)
        assertTrue(repository.adaptive.value.rating > AdaptiveDifficulty.START_RATING)

        // replaying the old level does not count again
        val rating = repository.adaptive.value.rating
        vm.onEvent(GameEvent.OnRestart)
        solve(vm)
        advanceUntilIdle()
        assertEquals(rating, repository.adaptive.value.rating, 1e-9)

        vm.onEvent(GameEvent.OnNextLevel)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.levelNumber)
    }

    @Test
    fun `losing in adaptive mode lowers the rating and keeps the level`() = runTest(dispatcher) {
        val vm = viewModel(PlayMode.Adaptive)
        advanceUntilIdle()
        val puzzle = vm.state.value.puzzle!!
        val blocked = puzzle.remaining.first { !puzzle.level.isFree(it, puzzle.remaining) }
        repeat(puzzle.lives!!) { vm.onEvent(GameEvent.OnArrowTapped(blocked, ambiguous = false)) }
        advanceUntilIdle()
        assertEquals(PuzzleStatus.LOST, vm.state.value.status)
        assertEquals(1, repository.adaptive.value.level)
        assertTrue(repository.adaptive.value.rating < AdaptiveDifficulty.START_RATING)
    }

    @Test
    fun `daily challenge is today's puzzle and builds the streak`() = runTest(dispatcher) {
        repository.daily.value = DailyRecord(lastCompletedDay = TODAY - 1, streak = 4, bestStreak = 4, bestScore = 10)
        val vm = viewModel(PlayMode.Daily)
        advanceUntilIdle()
        assertTrue(vm.state.value.isDaily)
        assertEquals(DailyChallenge.spec(TODAY).gridSize, vm.state.value.gridSize)
        solve(vm)
        advanceUntilIdle()
        assertEquals(5, vm.state.value.dailyStreak)
        assertTrue(repository.daily.value.completedOn(TODAY))
        assertEquals(vm.state.value.score, repository.boards.value.getValue("daily").best?.score)
    }

    private companion object {
        const val TODAY = 20_004L
    }
}
