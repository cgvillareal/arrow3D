package com.arrow3d.online

import com.arrow3d.domain.engine.ComboRules
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.generator.LevelGenerator
import com.arrow3d.domain.modes.DailyChallenge
import com.arrow3d.domain.modes.LevelSpec
import com.arrow3d.domain.solver.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunVerifierTest {

    private val day = 20_004L

    private fun solution(spec: LevelSpec) = Solver.solve(LevelGenerator(spec.config).generate(spec.seed).level).order

    /** A clean run: the solver's order, one tap every [gapMs]. */
    private fun taps(order: List<Int>, gapMs: Long = 400, start: Long = 1_000) =
        order.mapIndexed { i, id -> RunAction(ActionType.TAP, t = start + i * gapMs, arrowId = id) }

    @Test
    fun `a real solution is accepted and scored by the replay`() {
        val spec = LevelSpec.classic(Difficulty.EASY, 3)
        val order = solution(spec)
        val verdict = RunVerifier.verify(RunSubmission("easy", 3, day, actions = taps(order)), day)
        verdict as Verdict.Accepted
        assertEquals(order.size, verdict.bestCombo) // every tap inside the combo window
        assertTrue(verdict.score > 0)

        // slow play scores less: no combo
        val slow = RunVerifier.verify(RunSubmission("easy", 3, day, actions = taps(order, gapMs = ComboRules.WINDOW_MS + 1)), day)
        assertTrue((slow as Verdict.Accepted).score < verdict.score)
        assertEquals(1, slow.bestCombo)
    }

    @Test
    fun `unsolved, impossible or too fast runs are rejected`() {
        val spec = LevelSpec.classic(Difficulty.EASY, 3)
        val order = solution(spec)
        fun verify(actions: List<RunAction>, level: Int = 3) = RunVerifier.verify(RunSubmission("easy", level, day, actions = actions), day)

        assertTrue(verify(taps(order.dropLast(1))) is Verdict.Rejected) // not finished
        assertTrue(verify(taps(order + order.first())) is Verdict.Rejected) // tapping a removed arrow
        assertTrue(verify(taps(order, gapMs = 10)) is Verdict.Rejected) // bot speed
        assertTrue(verify(taps(order), level = 4) is Verdict.Rejected) // solution of another level
        assertTrue(verify(taps(order).reversed()) is Verdict.Rejected) // time goes backwards
        assertTrue(verify(emptyList()) is Verdict.Rejected)
    }

    @Test
    fun `undo, hint and restart are replayed with their limits`() {
        val spec = LevelSpec.classic(Difficulty.EASY, 3)
        val order = solution(spec)
        val actions = buildList {
            add(RunAction(ActionType.TAP, 100, order[0]))
            add(RunAction(ActionType.UNDO, 200))
            add(RunAction(ActionType.HINT, 300))
            add(RunAction(ActionType.RESTART, 400))
            addAll(taps(order, start = 500))
        }
        assertTrue(RunVerifier.verify(RunSubmission("easy", 3, day, actions = actions), day) is Verdict.Accepted)

        // Expert allows a single undo
        val expert = LevelSpec.classic(Difficulty.EXPERT, 1)
        val first = solution(expert).first()
        val twoUndos = listOf(
            RunAction(ActionType.TAP, 100, first), RunAction(ActionType.UNDO, 200),
            RunAction(ActionType.TAP, 300, first), RunAction(ActionType.UNDO, 400),
        )
        assertTrue(RunVerifier.verify(RunSubmission("expert", 1, day, actions = twoUndos), day) is Verdict.Rejected)
    }

    @Test
    fun `level specs come from the mode`() {
        assertEquals(DailyChallenge.spec(day), RunVerifier.specFor(RunSubmission("daily", 1, day, actions = emptyList()), day).getOrThrow())
        assertTrue(RunVerifier.specFor(RunSubmission("daily", 1, day - 5, actions = emptyList()), day).isFailure)
        assertTrue(RunVerifier.specFor(RunSubmission("adaptive", 2, day, rating = null, actions = emptyList()), day).isFailure)
        assertTrue(RunVerifier.specFor(RunSubmission("adaptive", 2, day, rating = 9.0, actions = emptyList()), day).isFailure)
        assertTrue(RunVerifier.specFor(RunSubmission("adaptive", 2, day, rating = 2.5, actions = emptyList()), day).isSuccess)
        assertTrue(RunVerifier.specFor(RunSubmission("chess", 1, day, actions = emptyList()), day).isFailure)
        assertTrue(RunVerifier.specFor(RunSubmission("easy", 0, day, actions = emptyList()), day).isFailure)
    }

    @Test
    fun `nicknames are trimmed and limited`() {
        assertTrue(NameRules.isValid("  Gabi_78 "))
        assertTrue(NameRules.isValid("Ñandú veloz"))
        assertEquals("a b", NameRules.normalize(" a   b "))
        assertFalse(NameRules.isValid("ab"))
        assertFalse(NameRules.isValid("x".repeat(17)))
        assertFalse(NameRules.isValid("<script>"))
    }

    @Test
    fun `protocol round trips through json`() {
        val run = RunSubmission("adaptive", 4, day, rating = 1.5, actions = listOf(RunAction(ActionType.TAP, 10, 3, ambiguous = true)))
        val text = Api.json.encodeToString(RunSubmission.serializer(), run)
        assertEquals(run, Api.json.decodeFromString(RunSubmission.serializer(), text))
        assertTrue(text.contains("\"TAP\""))
    }
}
