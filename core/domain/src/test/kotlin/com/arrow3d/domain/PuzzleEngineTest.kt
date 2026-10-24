package com.arrow3d.domain

import com.arrow3d.domain.engine.ComboRules
import com.arrow3d.domain.engine.PuzzleEngine
import com.arrow3d.domain.engine.PuzzleStatus
import com.arrow3d.domain.engine.TapOutcome
import com.arrow3d.domain.model.Arrow
import com.arrow3d.domain.model.Level
import com.arrow3d.domain.surface.Cell
import com.arrow3d.domain.surface.CubeSurface
import com.arrow3d.domain.surface.Dir2D
import com.arrow3d.domain.surface.FaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleEngineTest {

    // Row on the top face: 0 is free, 0 blocks 1, 1 blocks 2.
    private val level = Level(
        CubeSurface(3),
        listOf(
            Arrow.fromTail(0, FaceId.TOP, Cell(2, 0), Dir2D.V_PLUS, 1),
            Arrow.fromTail(1, FaceId.TOP, Cell(1, 0), Dir2D.U_PLUS, 1),
            Arrow.fromTail(2, FaceId.TOP, Cell(0, 0), Dir2D.U_PLUS, 1),
        ),
    )

    private fun start(lives: Int? = 3, undos: Int? = 2, hints: Int? = 1) =
        PuzzleEngine.start(level, lives, undos, hints)

    @Test
    fun `free arrow is released and reports the arrows it frees`() {
        val result = PuzzleEngine.tap(start(), 0)
        val outcome = result.outcome as TapOutcome.Released
        assertEquals(setOf(1), outcome.newlyFree)
        assertFalse(outcome.levelComplete)
        assertEquals(setOf(1, 2), result.state.remaining)
    }

    @Test
    fun `blocked arrow costs a life and names the blocker`() {
        val result = PuzzleEngine.tap(start(), 2)
        val outcome = result.outcome as TapOutcome.Blocked
        assertEquals(1, outcome.blockerId)
        assertTrue(outcome.lifeLost)
        assertEquals(2, result.state.lives)
        assertEquals(3, result.state.remaining.size)
    }

    @Test
    fun `ambiguous blocked tap and zen mode never cost a life`() {
        assertEquals(3, PuzzleEngine.tap(start(), 2, ambiguous = true).state.lives)
        val zen = PuzzleEngine.tap(start(lives = null), 2)
        assertNull(zen.state.lives)
        assertFalse((zen.outcome as TapOutcome.Blocked).lifeLost)
    }

    @Test
    fun `running out of lives loses the level and ignores further taps`() {
        var state = start(lives = 1)
        val r = PuzzleEngine.tap(state, 2)
        assertTrue((r.outcome as TapOutcome.Blocked).outOfLives)
        state = r.state
        assertEquals(PuzzleStatus.LOST, state.status)
        assertEquals(TapOutcome.Ignored, PuzzleEngine.tap(state, 0).outcome)
    }

    @Test
    fun `releasing every arrow wins with stars by lives lost`() {
        var state = PuzzleEngine.tap(start(), 2).state // one life lost
        listOf(0, 1, 2).forEach { state = PuzzleEngine.tap(state, it).state }
        assertEquals(PuzzleStatus.WON, state.status)
        assertEquals(2, state.stars)
    }

    @Test
    fun `undo restores the last arrow and spends an undo`() {
        val afterTap = PuzzleEngine.tap(start(undos = 1), 0).state
        val (undone, restored) = PuzzleEngine.undo(afterTap)
        assertEquals(0, restored)
        assertEquals(setOf(0, 1, 2), undone.remaining)
        assertEquals(0, undone.undosLeft)
        // no undos left
        val again = PuzzleEngine.tap(undone, 0).state
        assertNull(PuzzleEngine.undo(again).second)
    }

    @Test
    fun `hint picks a free arrow and is limited`() {
        val (state, hint) = PuzzleEngine.hint(start(hints = 1))
        assertEquals(0, hint)
        assertEquals(0, state.hintsLeft)
        assertNull(PuzzleEngine.hint(state).second)
    }

    @Test
    fun `quick releases build a combo that multiplies points`() {
        var state = start()
        val first = PuzzleEngine.tap(state, 0, nowMillis = 1_000).also { state = it.state }.outcome as TapOutcome.Released
        val second = PuzzleEngine.tap(state, 1, nowMillis = 2_000).also { state = it.state }.outcome as TapOutcome.Released
        val third = PuzzleEngine.tap(state, 2, nowMillis = 3_000).also { state = it.state }.outcome as TapOutcome.Released
        assertEquals(listOf(1, 2, 3), listOf(first.combo, second.combo, third.combo))
        val base = ComboRules.basePoints(level.arrow(0))
        assertEquals(listOf(base, base * 2, base * 3), listOf(first.points, second.points, third.points))
        assertEquals(base * 6, state.score)
        assertEquals(3, state.bestCombo)
    }

    @Test
    fun `a slow release starts a new combo`() {
        var state = PuzzleEngine.tap(start(), 0, nowMillis = 1_000).state
        state = PuzzleEngine.tap(state, 1, nowMillis = 1_000 + ComboRules.WINDOW_MS + 1).state
        assertEquals(1, state.combo)
        assertEquals(1, state.bestCombo)
    }

    @Test
    fun `a blocked tap breaks the combo, an ambiguous one does not`() {
        val state = PuzzleEngine.tap(start(), 0, nowMillis = 1_000).state
        val ambiguous = PuzzleEngine.tap(state, 2, ambiguous = true, nowMillis = 1_100)
        assertEquals(1, ambiguous.state.combo)
        assertEquals(0, (ambiguous.outcome as TapOutcome.Blocked).comboLost)

        val blocked = PuzzleEngine.tap(state, 2, nowMillis = 1_200)
        assertEquals(1, (blocked.outcome as TapOutcome.Blocked).comboLost)
        assertEquals(0, blocked.state.combo)
        val next = PuzzleEngine.tap(blocked.state, 1, nowMillis = 1_300).outcome as TapOutcome.Released
        assertEquals(1, next.combo)
    }

    @Test
    fun `undo takes back the points and ends the combo`() {
        var state = PuzzleEngine.tap(start(), 0, nowMillis = 1_000).state
        state = PuzzleEngine.tap(state, 1, nowMillis = 1_500).state
        val before = state.score
        val (undone, _) = PuzzleEngine.undo(state)
        assertEquals(before - ComboRules.basePoints(level.arrow(1)) * 2, undone.score)
        assertEquals(0, undone.combo)
    }
}
