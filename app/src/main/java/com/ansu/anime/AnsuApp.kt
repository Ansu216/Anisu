package com.ansu.anime

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.di.AppContainer

class AnsuApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    /**
     * One app-wide image loader. Posters and thumbnails from AniList and the sources rarely change, so cached
     * copies are reused without asking the server again (`respectCacheHeaders(false)`), which removes a network
     * round trip per image while scrolling and keeps lists smooth and working on a weak connection.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(200L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(false)
        .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Keep the scheduled background update check in step with the stored preference.
        container.updateManager.applyAutoCheck()
        // Must be installed as early as possible so a crash at startup is captured too.
        container.diagnostics.installCrashHandler()
        installLinkageGuard()
        container.diagnostics.log(
            LogCategory.APP,
            "Anisu ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) started · " +
                "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        )
    }

    /**
     * An extension built against different library versions can raise a LinkageError (NoSuchMethodError,
     * NoClassDefFoundError...) on a background thread we do not control, e.g. inside an OkHttp or Rx worker.
     * That would close the app, so such an error off the main thread is logged and swallowed instead; the
     * request that caused it simply never completes and times out in the UI.
     */
    private fun installLinkageGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            var cause: Throwable? = error
            var linkage = false
            while (cause != null) {
                if (cause is LinkageError) { linkage = true; break }
                cause = cause.cause?.takeIf { it !== cause }
            }
            if (linkage && thread.name != "main") {
                android.util.Log.e("AnsuApp", "Extension linkage error on ${thread.name} ignored", error)
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }
}
