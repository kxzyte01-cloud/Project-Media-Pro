package com.projectmediapro.app

import android.app.Application
import com.projectmediapro.app.di.AppContainer

/**
 * Application entry point holding manual dependency wiring in [AppContainer].
 */
class MediaProApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
