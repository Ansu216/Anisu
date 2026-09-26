package com.kernel.anime

import android.app.Application
import com.kernel.anime.di.AppContainer

class KernelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
