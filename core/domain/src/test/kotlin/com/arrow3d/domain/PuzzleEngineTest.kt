package com.arrow3d.domain

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
}
