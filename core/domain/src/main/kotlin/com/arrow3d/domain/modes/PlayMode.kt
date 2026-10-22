package com.arrow3d.domain.modes

import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.generator.GeneratorConfig
import com.arrow3d.domain.generator.LevelGenerator

/** Everything needed to build and play one level. */
data class LevelSpec(
    val config: GeneratorConfig,
    val seed: Long,
    val lives: Int?,
    val undos: Int?,
    val hints: Int?,
) {
    val gridSize: Int get() = config.gridSize

    companion object {
        fun classic(difficulty: Difficulty, levelNumber: Int) = LevelSpec(
            config = GeneratorConfig.from(difficulty),
            seed = LevelGenerator.seedFor(difficulty, levelNumber),
            lives = difficulty.lives,
            undos = difficulty.undos,
            hints = difficulty.hints,
        )
    }
}

/** What the player chose on the mode screen. [key] names it in routes, storage and records. */
sealed interface PlayMode {
    val key: String

    data class Classic(val difficulty: Difficulty) : PlayMode {
        override val key: String get() = difficulty.name.lowercase()
    }

    /** Endless levels whose difficulty follows how the player is doing. */
    data object Adaptive : PlayMode {
        override val key: String = "adaptive"
    }

    /** One puzzle per day, the same for everybody. */
    data object Daily : PlayMode {
        override val key: String = "daily"
    }

    companion object {
        val all: List<PlayMode> = Difficulty.entries.map { Classic(it) } + Adaptive + Daily

        fun fromKey(key: String): PlayMode = all.firstOrNull { it.key == key } ?: Classic(Difficulty.EASY)
    }
}
