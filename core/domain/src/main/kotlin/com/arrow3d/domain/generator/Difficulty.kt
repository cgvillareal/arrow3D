package com.arrow3d.domain.generator

/**
 * Difficulty presets (design doc, section 5). Ranges are a first proposal to be calibrated
 * with play tests. A null value means "unlimited".
 */
enum class Difficulty(
    val gridSize: Int,
    val maxArrowLength: Int,
    /** Bias towards long arrows: 0 = uniform, higher = longer pieces. */
    val lengthBias: Double,
    /** Preference for arrows with long exit paths: more cells to cross = deeper blocking chains. */
    val pathBias: Double,
    /** Chance of placing a traveler arrow when one fits. */
    val travelerChance: Double,
    val lives: Int?,
    val undos: Int?,
    val hints: Int?,
) {
    ZEN(gridSize = 2, maxArrowLength = 2, lengthBias = 0.5, pathBias = 0.0, travelerChance = 0.0, lives = null, undos = null, hints = null),
    EASY(gridSize = 3, maxArrowLength = 3, lengthBias = 1.0, pathBias = 0.8, travelerChance = 0.0, lives = 5, undos = 5, hints = 3),
    MEDIUM(gridSize = 4, maxArrowLength = 4, lengthBias = 1.2, pathBias = 1.5, travelerChance = 0.0, lives = 3, undos = 3, hints = 2),
    HARD(gridSize = 5, maxArrowLength = 5, lengthBias = 1.4, pathBias = 2.2, travelerChance = 0.12, lives = 3, undos = 2, hints = 1),
    EXPERT(gridSize = 6, maxArrowLength = 6, lengthBias = 1.5, pathBias = 3.0, travelerChance = 0.25, lives = 2, undos = 1, hints = 1),
}
