package com.arrow3d.domain.progress

import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.modes.PlayMode

/** Lifetime numbers of the player. Every field only grows, so achievements never get lost. */
data class PlayerStats(
    val levelsWon: Int = 0,
    val levelsLost: Int = 0,
    val arrowsRemoved: Int = 0,
    val bentRemoved: Int = 0,
    val travelersRemoved: Int = 0,
    /** Taps on a blocked arrow. */
    val mistakes: Int = 0,
    val bestCombo: Int = 0,
    val bestLevelScore: Int = 0,
    val totalScore: Long = 0,
    /** Wins without losing a life (modes with lives only). */
    val perfectWins: Int = 0,
    val perfectHardWins: Int = 0,
    /** Expert wins without losing a life and without undo or hints. */
    val cleanExpertWins: Int = 0,
    val dailyWins: Int = 0,
    val bestDailyStreak: Int = 0,
    val bestAdaptiveRating: Double = 0.0,
    val tutorialDone: Boolean = false,
) {
    /** Text form for a key-value store: "name=value;...". Unknown or broken fields are ignored when read. */
    fun encode(): String = listOf(
        "levelsWon" to levelsWon, "levelsLost" to levelsLost, "arrowsRemoved" to arrowsRemoved,
        "bentRemoved" to bentRemoved, "travelersRemoved" to travelersRemoved, "mistakes" to mistakes,
        "bestCombo" to bestCombo, "bestLevelScore" to bestLevelScore, "totalScore" to totalScore,
        "perfectWins" to perfectWins, "perfectHardWins" to perfectHardWins, "cleanExpertWins" to cleanExpertWins,
        "dailyWins" to dailyWins, "bestDailyStreak" to bestDailyStreak, "bestAdaptiveRating" to bestAdaptiveRating,
        "tutorialDone" to tutorialDone,
    ).joinToString(";") { (k, v) -> "$k=$v" }

    companion object {
        fun decode(text: String?): PlayerStats {
            if (text.isNullOrBlank()) return PlayerStats()
            val map = text.split(";").mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
            }.toMap()
            fun int(k: String) = map[k]?.toIntOrNull() ?: 0
            return PlayerStats(
                levelsWon = int("levelsWon"),
                levelsLost = int("levelsLost"),
                arrowsRemoved = int("arrowsRemoved"),
                bentRemoved = int("bentRemoved"),
                travelersRemoved = int("travelersRemoved"),
                mistakes = int("mistakes"),
                bestCombo = int("bestCombo"),
                bestLevelScore = int("bestLevelScore"),
                totalScore = map["totalScore"]?.toLongOrNull() ?: 0,
                perfectWins = int("perfectWins"),
                perfectHardWins = int("perfectHardWins"),
                cleanExpertWins = int("cleanExpertWins"),
                dailyWins = int("dailyWins"),
                bestDailyStreak = int("bestDailyStreak"),
                bestAdaptiveRating = map["bestAdaptiveRating"]?.toDoubleOrNull() ?: 0.0,
                tutorialDone = map["tutorialDone"]?.toBoolean() ?: false,
            )
        }
    }
}

/** How one attempt at a level ended, as far as stats are concerned. */
data class LevelSummary(
    val mode: PlayMode,
    val won: Boolean,
    /** Whether the mode has lives at all (Zen and the tutorial do not). */
    val hasLives: Boolean,
    val livesLost: Int,
    val score: Int,
    val bestCombo: Int,
    val arrowsRemoved: Int,
    val bentRemoved: Int,
    val travelersRemoved: Int,
    val mistakes: Int,
    val helpUsed: Boolean,
    /** Daily streak after this win, when it was the daily challenge. */
    val dailyStreak: Int = 0,
    /** Adaptive rating after this level, in adaptive mode. */
    val adaptiveRating: Double? = null,
    /** True when this was the last tutorial lesson. */
    val finishedTutorial: Boolean = false,
)

object StatsRules {

    fun apply(stats: PlayerStats, s: LevelSummary): PlayerStats {
        // The tutorial teaches; it only counts for its own achievement.
        if (s.mode == PlayMode.Tutorial) return stats.copy(tutorialDone = stats.tutorialDone || s.finishedTutorial)
        val perfect = s.won && s.hasLives && s.livesLost == 0
        val difficulty = (s.mode as? PlayMode.Classic)?.difficulty
        return stats.copy(
            levelsWon = stats.levelsWon + if (s.won) 1 else 0,
            levelsLost = stats.levelsLost + if (s.won) 0 else 1,
            arrowsRemoved = stats.arrowsRemoved + s.arrowsRemoved,
            bentRemoved = stats.bentRemoved + s.bentRemoved,
            travelersRemoved = stats.travelersRemoved + s.travelersRemoved,
            mistakes = stats.mistakes + s.mistakes,
            bestCombo = maxOf(stats.bestCombo, s.bestCombo),
            bestLevelScore = if (s.won) maxOf(stats.bestLevelScore, s.score) else stats.bestLevelScore,
            totalScore = stats.totalScore + if (s.won) s.score else 0,
            perfectWins = stats.perfectWins + if (perfect) 1 else 0,
            perfectHardWins = stats.perfectHardWins + if (perfect && difficulty == Difficulty.HARD) 1 else 0,
            cleanExpertWins = stats.cleanExpertWins + if (perfect && !s.helpUsed && difficulty == Difficulty.EXPERT) 1 else 0,
            dailyWins = stats.dailyWins + if (s.won && s.mode == PlayMode.Daily) 1 else 0,
            bestDailyStreak = maxOf(stats.bestDailyStreak, s.dailyStreak),
            bestAdaptiveRating = maxOf(stats.bestAdaptiveRating, s.adaptiveRating ?: 0.0),
        )
    }
}

/** Achievements are pure functions of the stats: nothing extra to store, and they cannot drift. */
enum class Achievement(private val reached: (PlayerStats) -> Boolean) {
    TUTORIAL({ it.tutorialDone }),
    FIRST_WIN({ it.levelsWon >= 1 }),
    COMBO_5({ it.bestCombo >= 5 }),
    COMBO_MAX({ it.bestCombo >= 8 }),
    PERFECT_10({ it.perfectWins >= 10 }),
    HARD_PERFECT({ it.perfectHardWins >= 1 }),
    EXPERT_CLEAN({ it.cleanExpertWins >= 1 }),
    SNAKES_100({ it.bentRemoved >= 100 }),
    TRAVELERS_50({ it.travelersRemoved >= 50 }),
    ARROWS_1000({ it.arrowsRemoved >= 1_000 }),
    LEVELS_50({ it.levelsWon >= 50 }),
    SCORE_10K({ it.bestLevelScore >= 10_000 }),
    DAILY_STREAK_7({ it.bestDailyStreak >= 7 }),
    ADAPTIVE_TOP({ it.bestAdaptiveRating >= 4.0 }),
    ;

    fun isUnlocked(stats: PlayerStats): Boolean = reached(stats)

    companion object {
        fun unlocked(stats: PlayerStats): Set<Achievement> = entries.filterTo(LinkedHashSet()) { it.isUnlocked(stats) }

        /** Achievements that [after] has and [before] did not, in declaration order. */
        fun newlyUnlocked(before: PlayerStats, after: PlayerStats): List<Achievement> =
            entries.filter { it.isUnlocked(after) && !it.isUnlocked(before) }
    }
}
