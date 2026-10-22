package com.arrow3d.game.di

import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.game.data.IProgressRepository
import com.arrow3d.game.data.ProgressRepositoryImpl
import com.arrow3d.game.haptics.AndroidHaptics
import com.arrow3d.game.haptics.IHaptics
import com.arrow3d.game.presentation.game.GameViewModel
import com.arrow3d.game.presentation.select.ModeSelectViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<IProgressRepository> { ProgressRepositoryImpl(androidContext()) }
    single<IHaptics> { AndroidHaptics(androidContext()) }

    viewModel { ModeSelectViewModel(get()) }
    viewModel { params -> GameViewModel(difficulty = params.get<Difficulty>(), progressRepository = get(), haptics = get()) }
}
