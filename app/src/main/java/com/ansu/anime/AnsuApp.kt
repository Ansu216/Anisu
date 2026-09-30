package com.ansu.anime

import android.app.Application
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.di.AppContainer

class AnsuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Must be installed as early as possible so a crash at startup is captured too.
        container.diagnostics.installCrashHandler()
        container.diagnostics.log(
            LogCategory.APP,
            "Ansu ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) started · " +
                "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        )
    }
}
