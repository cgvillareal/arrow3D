package com.arrow3d.domain.modes

import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.generator.GeneratorConfig

/**
 * Daily challenge state. Days are epoch days (days since 1970-01-01, local time), so the domain
 * stays free of clocks and time zones.
 */
data class DailyRecord(
    val lastCompletedDay: Long? = null,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Best score on [lastCompletedDay]. */
    val bestScore: Int = 0,
) {
    fun completedOn(day: Long): Boolean = lastCompletedDay == day

    /** The streak still counts if the last challenge was today or yesterday. */
    fun currentStreak(today: Long): Int =
        if (lastCompletedDay != null && today - lastCompletedDay in 0..1) streak else 0
}

object DailyChallenge {

    /** Monday to Sunday: the week ramps up and the weekend is the hardest. */
    private val byWeekday = listOf(
        Difficulty.MEDIUM, Difficulty.MEDIUM, Difficulty.HARD, Difficulty.HARD,
        Difficulty.HARD, Difficulty.EXPERT, Difficulty.EXPERT,
    )

    /** 0 = Monday. 1970-01-01 was a Thursday. */
    fun weekday(day: Long): Int = Math.floorMod(day + 3, 7L).toInt()

    fun difficultyFor(day: Long): Difficulty = byWeekday[weekday(day)]

    fun spec(day: Long): LevelSpec {
        val d = difficultyFor(day)
        return LevelSpec(
            config = GeneratorConfig.from(d),
            seed = day * 1_000_033L + 424_242L,
            lives = d.lives,
            undos = d.undos,
            hints = d.hints,
        )
    }

    /** Records a win on [day]. Replaying the same day only improves the best score. */
    fun complete(record: DailyRecord, day: Long, score: Int): DailyRecord {
        val last = record.lastCompletedDay
        return when {
            last == day -> record.copy(bestScore = maxOf(record.bestScore, score))
            last != null && day < last -> record
            else -> {
                val streak = if (last == day - 1) record.streak + 1 else 1
                DailyRecord(lastCompletedDay = day, streak = streak, bestStreak = maxOf(record.bestStreak, streak), bestScore = score)
            }
        }
    }
}
