package com.arrow3d.game

import android.app.Application
import com.arrow3d.game.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class Arrow3dApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@Arrow3dApplication)
            modules(appModule)
        }
    }
}
