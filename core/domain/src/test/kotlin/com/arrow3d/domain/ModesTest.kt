package com.arrow3d.domain

import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.generator.LevelGenerator
import com.arrow3d.domain.modes.AdaptiveDifficulty
import com.arrow3d.domain.modes.AdaptiveProgress
import com.arrow3d.domain.modes.DailyChallenge
import com.arrow3d.domain.modes.DailyRecord
import com.arrow3d.domain.modes.Leaderboard
import com.arrow3d.domain.modes.LevelResult
import com.arrow3d.domain.modes.LevelSpec
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.modes.ScoreEntry
import com.arrow3d.domain.solver.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesTest {

    @Test
    fun `mode keys round trip`() {
        PlayMode.all.forEach { assertEquals(it, PlayMode.fromKey(it.key)) }
        assertEquals(PlayMode.Classic(Difficulty.EASY), PlayMode.fromKey("nonsense"))
    }

    @Test
    fun `classic spec matches the preset`() {
        val spec = LevelSpec.classic(Difficulty.HARD, 3)
        assertEquals(5, spec.gridSize)
        assertEquals(LevelGenerator.seedFor(Difficulty.HARD, 3), spec.seed)
        assertEquals(Difficulty.HARD.lives, spec.lives)
    }

    @Test
    fun `clean wins raise the adaptive rating and losses lower it`() {
        var p = AdaptiveProgress()
        repeat(3) { p = AdaptiveDifficulty.next(p, LevelResult(won = true, livesLost = 0, helpUsed = false)) }
        assertEquals(4, p.level)
        assertEquals(2.2, p.rating, 1e-9)
        val lost = AdaptiveDifficulty.next(p, LevelResult(won = false, livesLost = 3, helpUsed = false))
        assertEquals(4, lost.level)
        assertTrue(lost.rating < p.rating)
        val messy = AdaptiveDifficulty.next(p, LevelResult(won = true, livesLost = 2, helpUsed = true))
        assertTrue(messy.rating < p.rating)
        // never leaves the preset range
        var low = AdaptiveProgress(rating = 0.1)
        repeat(5) { low = AdaptiveDifficulty.next(low, LevelResult(false, 0, false)) }
        assertEquals(0.0, low.rating, 1e-9)
    }

    @Test
    fun `adaptive levels follow the rating and are solvable`() {
        assertEquals(3, AdaptiveDifficulty.spec(AdaptiveProgress(rating = 1.0)).gridSize)
        assertEquals(6, AdaptiveDifficulty.spec(AdaptiveProgress(rating = 4.0)).gridSize)
        listOf(0.0, 0.7, 1.5, 2.4, 3.3, 4.0).forEach { rating ->
            val spec = AdaptiveDifficulty.spec(AdaptiveProgress(level = 2, rating = rating))
            assertTrue(spec.lives != null) // adaptive always has lives
            val level = LevelGenerator(spec.config).generate(spec.seed).level
            assertTrue(Solver.isSolvable(level))
        }
    }

    @Test
    fun `daily challenge is the same all day and changes the next day`() {
        val day = 20_004L // 2024-10-08, a Tuesday
        assertEquals(1, DailyChallenge.weekday(day))
        assertEquals(DailyChallenge.spec(day), DailyChallenge.spec(day))
        assertNotEquals(DailyChallenge.spec(day).seed, DailyChallenge.spec(day + 1).seed)
        assertEquals(Difficulty.EXPERT, DailyChallenge.difficultyFor(day + 5)) // Sunday
        val spec = DailyChallenge.spec(day)
        assertTrue(Solver.isSolvable(LevelGenerator(spec.config).generate(spec.seed).level))
    }

    @Test
    fun `daily streak grows on consecutive days and resets after a gap`() {
        var r = DailyRecord()
        r = DailyChallenge.complete(r, 100, 500)
        r = DailyChallenge.complete(r, 101, 300)
        assertEquals(2, r.streak)
        r = DailyChallenge.complete(r, 101, 900) // replay same day
        assertEquals(2, r.streak)
        assertEquals(900, r.bestScore)
        assertTrue(r.completedOn(101))
        assertEquals(2, r.currentStreak(102))
        assertEquals(0, r.currentStreak(103))
        r = DailyChallenge.complete(r, 104, 100)
        assertEquals(1, r.streak)
        assertEquals(2, r.bestStreak)
        assertFalse(r.completedOn(105))
    }

    @Test
    fun `leaderboard keeps the top ten and survives encoding`() {
        var board = Leaderboard()
        (1..12).forEach { board = board.add(ScoreEntry(score = it * 100, bestCombo = it, level = it, day = 50)).first }
        assertEquals(10, board.entries.size)
        assertEquals(1200, board.best?.score)
        val (_, rank) = board.add(ScoreEntry(50, 1, 1, 51))
        assertNull(rank)
        val (withNew, newRank) = board.add(ScoreEntry(1150, 3, 13, 52))
        assertEquals(2, newRank)
        assertEquals(withNew, Leaderboard.decode(withNew.encode()))
        assertEquals(2, Leaderboard.decode("100,2,1,5;garbage;300,1,2,6").entries.size)
        assertTrue(Leaderboard.decode(null).entries.isEmpty())
    }
}
