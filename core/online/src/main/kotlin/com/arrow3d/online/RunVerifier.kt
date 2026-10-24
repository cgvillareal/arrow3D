package com.arrow3d.online

import com.arrow3d.domain.engine.PuzzleEngine
import com.arrow3d.domain.engine.PuzzleState
import com.arrow3d.domain.engine.PuzzleStatus
import com.arrow3d.domain.engine.TapOutcome
import com.arrow3d.domain.generator.LevelGenerator
import com.arrow3d.domain.modes.AdaptiveDifficulty
import com.arrow3d.domain.modes.AdaptiveProgress
import com.arrow3d.domain.modes.DailyChallenge
import com.arrow3d.domain.modes.LevelSpec
import com.arrow3d.domain.modes.PlayMode

sealed interface Verdict {
    data class Accepted(val score: Int, val bestCombo: Int) : Verdict
    data class Rejected(val reason: String) : Verdict
}

/**
 * Checks a submitted run by playing it again: the level is rebuilt from its seed and every
 * action goes through the same [PuzzleEngine] the game uses. A run is accepted only if it ends
 * with the level won, and its score is the replay's, never the client's.
 *
 * It cannot tell a real player from a bot that plays legal moves at human speed, but it does
 * stop invented scores, impossible moves and inhumanly fast tapping.
 */
object RunVerifier {
    const val MAX_ACTIONS = 5_000
    const val MAX_LEVEL = 1_000_000

    /** Faster than this between two taps is not a person. */
    const val MIN_TAP_GAP_MS = 60L

    /** How far the daily challenge's day may be from the server's (time zones). */
    const val DAILY_DAY_SLACK = 1L

    /** Rebuilds the level a run was played on, or explains why it cannot exist. */
    fun specFor(run: RunSubmission, serverDay: Long): Result<LevelSpec> {
        if (run.level !in 1..MAX_LEVEL) return Result.failure(IllegalArgumentException("level out of range"))
        return when (val mode = PlayMode.all.firstOrNull { it.key == run.mode }) {
            null, PlayMode.Tutorial -> Result.failure(IllegalArgumentException("unknown mode ${run.mode}"))
            is PlayMode.Classic -> Result.success(LevelSpec.classic(mode.difficulty, run.level))
            PlayMode.Adaptive -> {
                val rating = run.rating
                if (rating == null || rating.isNaN() || rating !in 0.0..AdaptiveDifficulty.MAX_RATING) {
                    Result.failure(IllegalArgumentException("adaptive runs need a rating between 0 and ${AdaptiveDifficulty.MAX_RATING}"))
                } else {
                    Result.success(AdaptiveDifficulty.spec(AdaptiveProgress(run.level, rating)))
                }
            }
            PlayMode.Daily -> {
                if (kotlin.math.abs(run.day - serverDay) > DAILY_DAY_SLACK) {
                    Result.failure(IllegalArgumentException("daily challenge is not open for day ${run.day}"))
                } else {
                    Result.success(DailyChallenge.spec(run.day))
                }
            }
        }
    }

    fun verify(run: RunSubmission, serverDay: Long): Verdict {
        val spec = specFor(run, serverDay).getOrElse { return Verdict.Rejected(it.message ?: "invalid level") }
        return replay(spec, run.actions)
    }

    fun replay(spec: LevelSpec, actions: List<RunAction>): Verdict {
        if (actions.isEmpty() || actions.size > MAX_ACTIONS) return Verdict.Rejected("wrong number of actions")
        val level = LevelGenerator(spec.config).generate(spec.seed).level
        var state: PuzzleState = PuzzleEngine.start(level, spec.lives, spec.undos, spec.hints)
        var lastTap: Long? = null
        var lastT = Long.MIN_VALUE
        for ((index, action) in actions.withIndex()) {
            if (action.t < 0 || action.t < lastT) return Verdict.Rejected("action $index goes back in time")
            lastT = action.t
            when (action.type) {
                ActionType.TAP -> {
                    val id = action.arrowId ?: return Verdict.Rejected("tap $index has no arrow")
                    val previous = lastTap
                    if (previous != null && action.t - previous < MIN_TAP_GAP_MS) return Verdict.Rejected("tap $index is too fast")
                    lastTap = action.t
                    val result = PuzzleEngine.tap(state, id, action.ambiguous, nowMillis = action.t)
                    if (result.outcome == TapOutcome.Ignored) return Verdict.Rejected("tap $index does nothing")
                    state = result.state
                }
                ActionType.UNDO -> {
                    val (next, restored) = PuzzleEngine.undo(state)
                    if (restored == null) return Verdict.Rejected("undo $index is not allowed")
                    state = next
                }
                ActionType.HINT -> {
                    val (next, hint) = PuzzleEngine.hint(state)
                    if (hint == null) return Verdict.Rejected("hint $index is not allowed")
                    state = next
                }
                ActionType.RESTART -> state = PuzzleEngine.restart(state)
            }
        }
        if (state.status != PuzzleStatus.WON) return Verdict.Rejected("the level is not solved")
        return Verdict.Accepted(state.score, state.bestCombo)
    }
}
