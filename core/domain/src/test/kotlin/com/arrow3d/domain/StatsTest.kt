package com.arrow3d.domain

import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.progress.Achievement
import com.arrow3d.domain.progress.LevelSummary
import com.arrow3d.domain.progress.PlayerStats
import com.arrow3d.domain.progress.StatsRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {

    private fun win(
        mode: PlayMode = PlayMode.Classic(Difficulty.EASY),
        livesLost: Int = 0,
        combo: Int = 2,
        score: Int = 300,
        helpUsed: Boolean = false,
        hasLives: Boolean = true,
    ) = LevelSummary(
        mode = mode, won = true, hasLives = hasLives, livesLost = livesLost, score = score, bestCombo = combo,
        arrowsRemoved = 20, bentRemoved = 3, travelersRemoved = 1, mistakes = livesLost, helpUsed = helpUsed,
    )

    @Test
    fun `wins and losses add up`() {
        var s = StatsRules.apply(PlayerStats(), win())
        s = StatsRules.apply(s, win(livesLost = 1, score = 900, combo = 6))
        s = StatsRules.apply(s, win().copy(won = false, score = 50, mistakes = 3))
        assertEquals(2, s.levelsWon)
        assertEquals(1, s.levelsLost)
        assertEquals(60, s.arrowsRemoved)
        assertEquals(1, s.perfectWins)
        assertEquals(6, s.bestCombo)
        assertEquals(900, s.bestLevelScore)
        assertEquals(1_200L, s.totalScore) // a lost level scores nothing
        assertEquals(4, s.mistakes)
    }

    @Test
    fun `perfect counts only where there are lives, clean expert needs no help`() {
        val zen = StatsRules.apply(PlayerStats(), win(mode = PlayMode.Classic(Difficulty.ZEN), hasLives = false))
        assertEquals(0, zen.perfectWins)
        val hard = StatsRules.apply(PlayerStats(), win(mode = PlayMode.Classic(Difficulty.HARD)))
        assertEquals(1, hard.perfectHardWins)
        val helped = StatsRules.apply(PlayerStats(), win(mode = PlayMode.Classic(Difficulty.EXPERT), helpUsed = true))
        assertEquals(0, helped.cleanExpertWins)
        val clean = StatsRules.apply(PlayerStats(), win(mode = PlayMode.Classic(Difficulty.EXPERT)))
        assertEquals(1, clean.cleanExpertWins)
    }

    @Test
    fun `the tutorial only unlocks its own achievement`() {
        val lesson = win(mode = PlayMode.Tutorial, hasLives = false)
        val s = StatsRules.apply(PlayerStats(), lesson)
        assertEquals(PlayerStats(), s)
        val done = StatsRules.apply(s, lesson.copy(finishedTutorial = true))
        assertEquals(listOf(Achievement.TUTORIAL), Achievement.newlyUnlocked(s, done))
    }

    @Test
    fun `achievements unlock once, when crossing their threshold`() {
        val before = PlayerStats()
        val after = StatsRules.apply(before, win(combo = 5))
        assertEquals(listOf(Achievement.FIRST_WIN, Achievement.COMBO_5), Achievement.newlyUnlocked(before, after))
        val again = StatsRules.apply(after, win(combo = 5))
        assertTrue(Achievement.newlyUnlocked(after, again).isEmpty())
        assertEquals(setOf(Achievement.FIRST_WIN, Achievement.COMBO_5), Achievement.unlocked(again))
    }

    @Test
    fun `stats survive encoding and ignore broken text`() {
        val s = PlayerStats(levelsWon = 3, totalScore = 12_345_678_901L, bestAdaptiveRating = 2.6, tutorialDone = true)
        assertEquals(s, PlayerStats.decode(s.encode()))
        assertEquals(PlayerStats(levelsWon = 4), PlayerStats.decode("levelsWon=4;garbage;mistakes=x;future=9"))
        assertEquals(PlayerStats(), PlayerStats.decode(null))
    }
}
