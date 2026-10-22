package com.arrow3d.game.presentation.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.game.R
import com.arrow3d.game.ui.theme.GameColors

@StringRes
fun Difficulty.nameRes(): Int = when (this) {
    Difficulty.ZEN -> R.string.mode_zen
    Difficulty.EASY -> R.string.mode_easy
    Difficulty.MEDIUM -> R.string.mode_medium
    Difficulty.HARD -> R.string.mode_hard
    Difficulty.EXPERT -> R.string.mode_expert
}

@StringRes
fun Difficulty.descriptionRes(): Int = when (this) {
    Difficulty.ZEN -> R.string.mode_zen_desc
    Difficulty.EASY -> R.string.mode_easy_desc
    Difficulty.MEDIUM -> R.string.mode_medium_desc
    Difficulty.HARD -> R.string.mode_hard_desc
    Difficulty.EXPERT -> R.string.mode_expert_desc
}

fun Difficulty.accent(): Color = when (this) {
    Difficulty.ZEN -> GameColors.Zen
    Difficulty.EASY -> GameColors.Easy
    Difficulty.MEDIUM -> GameColors.Medium
    Difficulty.HARD -> GameColors.Hard
    Difficulty.EXPERT -> GameColors.Expert
}

@StringRes
fun PlayMode.nameRes(): Int = when (this) {
    is PlayMode.Classic -> difficulty.nameRes()
    PlayMode.Adaptive -> R.string.mode_adaptive
    PlayMode.Daily -> R.string.mode_daily
}

fun PlayMode.accent(): Color = when (this) {
    is PlayMode.Classic -> difficulty.accent()
    PlayMode.Adaptive -> GameColors.Adaptive
    PlayMode.Daily -> GameColors.Star
}
