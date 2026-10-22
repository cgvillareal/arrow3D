package com.arrow3d.game.presentation.game

import app.cash.turbine.test
import com.arrow3d.domain.engine.PuzzleStatus
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.solver.Solver
import com.arrow3d.game.data.IProgressRepository
import com.arrow3d.game.haptics.HapticEvent
import com.arrow3d.game.haptics.IHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
    val vibration = MutableStateFlow(true)
    override fun observeLevels(): Flow<Map<Difficulty, Int>> = levels
    override suspend fun currentLevel(difficulty: Difficulty) = levels.value.getValue(difficulty)
    override suspend fun setCurrentLevel(difficulty: Difficulty, level: Int) {
        levels.value = levels.value + (difficulty to level)
    }
    override fun observeVibrationEnabled(): Flow<Boolean> = vibration.map { it }
    override suspend fun setVibrationEnabled(enabled: Boolean) {
        vibration.value = enabled
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

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        repository = FakeProgressRepository()
        haptics = FakeHaptics()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(difficulty: Difficulty = Difficulty.EASY) =
        GameViewModel(difficulty, repository, haptics, computeDispatcher = dispatcher)

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
        repository.vibration.value = false
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
}
