package com.ansu.anime

import android.app.Application
import com.ansu.anime.di.AppContainer

class AnisuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
