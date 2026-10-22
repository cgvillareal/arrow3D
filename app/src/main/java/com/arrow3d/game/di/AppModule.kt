package com.arrow3d.game.di

import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.game.audio.AndroidSoundPlayer
import com.arrow3d.game.audio.ISoundPlayer
import com.arrow3d.game.data.IProgressRepository
import com.arrow3d.game.data.ProgressRepositoryImpl
import com.arrow3d.game.haptics.AndroidHaptics
import com.arrow3d.game.haptics.IHaptics
import com.arrow3d.game.presentation.game.GameViewModel
import com.arrow3d.game.presentation.records.RecordsViewModel
import com.arrow3d.game.presentation.select.ModeSelectViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<IProgressRepository> { ProgressRepositoryImpl(androidContext()) }
    single<IHaptics> { AndroidHaptics(androidContext()) }
    single<ISoundPlayer> { AndroidSoundPlayer(androidContext()) }

    viewModel { ModeSelectViewModel(get()) }
    viewModel { RecordsViewModel(get()) }
    viewModel { params ->
        GameViewModel(mode = params.get<PlayMode>(0), progressRepository = get(), haptics = get(), sound = get())
    }
}
