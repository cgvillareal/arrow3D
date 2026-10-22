package com.arrow3d.domain.modes

import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.generator.GeneratorConfig
import kotlin.math.floor
import kotlin.math.roundToInt

/** Where the player is in adaptive mode. [rating] goes from 0 (Zen) to 4 (Expert). */
data class AdaptiveProgress(val level: Int = 1, val rating: Double = AdaptiveDifficulty.START_RATING)

/** How a level went, as far as adapting is concerned. */
data class LevelResult(val won: Boolean, val livesLost: Int, val helpUsed: Boolean)

/**
 * Adaptive mode: a rating between the presets. Clean wins push it up, mistakes and losses pull it
 * down. Grid size and rules come from the nearest preset; generator biases are interpolated, so
 * small rating changes already make levels a bit harder or easier.
 */
object AdaptiveDifficulty {
    const val START_RATING = 1.0
    val MAX_RATING = (Difficulty.entries.size - 1).toDouble()

    fun preset(rating: Double): Difficulty = Difficulty.entries[rating.coerceIn(0.0, MAX_RATING).roundToInt()]

    fun spec(progress: AdaptiveProgress): LevelSpec {
        val r = progress.rating.coerceIn(0.0, MAX_RATING)
        val low = Difficulty.entries[floor(r).toInt()]
        val high = Difficulty.entries[minOf(floor(r).toInt() + 1, Difficulty.entries.size - 1)]
        val t = r - floor(r)
        val nearest = preset(r)
        fun lerp(a: Double, b: Double) = a + (b - a) * t
        val config = GeneratorConfig(
            gridSize = nearest.gridSize,
            maxArrowLength = nearest.maxArrowLength,
            lengthBias = lerp(low.lengthBias, high.lengthBias),
            pathBias = lerp(low.pathBias, high.pathBias),
            travelerChance = lerp(low.travelerChance, high.travelerChance),
            bendChance = lerp(low.bendChance, high.bendChance),
            maxBentLength = nearest.maxBentLength,
        )
        return LevelSpec(
            config = config,
            seed = SEED_BASE + progress.level * 7_919L + nearest.ordinal * 104_729L,
            // Adaptive always has lives: losing them is how it learns the level was too hard.
            lives = nearest.lives ?: DEFAULT_LIVES,
            undos = nearest.undos ?: DEFAULT_UNDOS,
            hints = nearest.hints ?: DEFAULT_HINTS,
        )
    }

    /** Progress after playing [progress].level. A win moves to the next level, a loss stays. */
    fun next(progress: AdaptiveProgress, result: LevelResult): AdaptiveProgress {
        val delta = when {
            !result.won -> -0.6
            result.livesLost == 0 && !result.helpUsed -> 0.4
            result.livesLost == 0 -> 0.2
            else -> 0.1 - 0.15 * result.livesLost
        }
        return AdaptiveProgress(
            level = if (result.won) progress.level + 1 else progress.level,
            rating = (progress.rating + delta).coerceIn(0.0, MAX_RATING),
        )
    }

    private const val SEED_BASE = 5_000_011L
    private const val DEFAULT_LIVES = 5
    private const val DEFAULT_UNDOS = 5
    private const val DEFAULT_HINTS = 3
}
