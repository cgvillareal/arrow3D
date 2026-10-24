package com.arrow3d.game.presentation.model

import androidx.annotation.StringRes
import com.arrow3d.domain.modes.LessonTopic
import com.arrow3d.domain.progress.Achievement
import com.arrow3d.game.R

@StringRes
fun LessonTopic.titleRes(): Int = when (this) {
    LessonTopic.TAP -> R.string.lesson_tap_title
    LessonTopic.BLOCKING -> R.string.lesson_blocking_title
    LessonTopic.CHAIN -> R.string.lesson_chain_title
    LessonTopic.LONG -> R.string.lesson_long_title
    LessonTopic.BENT -> R.string.lesson_bent_title
    LessonTopic.TRAVELER -> R.string.lesson_traveler_title
    LessonTopic.COMBO -> R.string.lesson_combo_title
}

@StringRes
fun LessonTopic.bodyRes(): Int = when (this) {
    LessonTopic.TAP -> R.string.lesson_tap_body
    LessonTopic.BLOCKING -> R.string.lesson_blocking_body
    LessonTopic.CHAIN -> R.string.lesson_chain_body
    LessonTopic.LONG -> R.string.lesson_long_body
    LessonTopic.BENT -> R.string.lesson_bent_body
    LessonTopic.TRAVELER -> R.string.lesson_traveler_body
    LessonTopic.COMBO -> R.string.lesson_combo_body
}

@StringRes
fun Achievement.titleRes(): Int = when (this) {
    Achievement.TUTORIAL -> R.string.ach_tutorial_title
    Achievement.FIRST_WIN -> R.string.ach_first_win_title
    Achievement.COMBO_5 -> R.string.ach_combo_5_title
    Achievement.COMBO_MAX -> R.string.ach_combo_max_title
    Achievement.PERFECT_10 -> R.string.ach_perfect_10_title
    Achievement.HARD_PERFECT -> R.string.ach_hard_perfect_title
    Achievement.EXPERT_CLEAN -> R.string.ach_expert_clean_title
    Achievement.SNAKES_100 -> R.string.ach_snakes_100_title
    Achievement.TRAVELERS_50 -> R.string.ach_travelers_50_title
    Achievement.ARROWS_1000 -> R.string.ach_arrows_1000_title
    Achievement.LEVELS_50 -> R.string.ach_levels_50_title
    Achievement.SCORE_10K -> R.string.ach_score_10k_title
    Achievement.DAILY_STREAK_7 -> R.string.ach_daily_streak_7_title
    Achievement.ADAPTIVE_TOP -> R.string.ach_adaptive_top_title
}

@StringRes
fun Achievement.descriptionRes(): Int = when (this) {
    Achievement.TUTORIAL -> R.string.ach_tutorial_desc
    Achievement.FIRST_WIN -> R.string.ach_first_win_desc
    Achievement.COMBO_5 -> R.string.ach_combo_5_desc
    Achievement.COMBO_MAX -> R.string.ach_combo_max_desc
    Achievement.PERFECT_10 -> R.string.ach_perfect_10_desc
    Achievement.HARD_PERFECT -> R.string.ach_hard_perfect_desc
    Achievement.EXPERT_CLEAN -> R.string.ach_expert_clean_desc
    Achievement.SNAKES_100 -> R.string.ach_snakes_100_desc
    Achievement.TRAVELERS_50 -> R.string.ach_travelers_50_desc
    Achievement.ARROWS_1000 -> R.string.ach_arrows_1000_desc
    Achievement.LEVELS_50 -> R.string.ach_levels_50_desc
    Achievement.SCORE_10K -> R.string.ach_score_10k_desc
    Achievement.DAILY_STREAK_7 -> R.string.ach_daily_streak_7_desc
    Achievement.ADAPTIVE_TOP -> R.string.ach_adaptive_top_desc
}
