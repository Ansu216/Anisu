package com.ansu.anime.extension.aniyomi

import android.content.Context
import android.content.pm.PackageInfo
import com.ansu.anime.extension.InstalledExtension
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

/**
 * Loads an Aniyomi/Keiyoushi extension APK the way Aniyomi does: read the class names from the
 * `tachiyomi.animeextension.class` metadata, open the APK with a class loader whose parent is Ansu
 * (so the extension finds the `eu.kanade.tachiyomi.*` API there) and instantiate each class.
 */
internal class AniyomiExtensionLoader(private val context: Context) {

    fun load(pkgInfo: PackageInfo, label: String): InstalledExtension {
        val appInfo = pkgInfo.applicationInfo
        val versionName = pkgInfo.versionName ?: "?"
        val displayName = label.removePrefix("Aniyomi: ")
        fun failed(message: String) = InstalledExtension(
            packageName = pkgInfo.packageName,
            displayName = displayName,
            versionName = versionName,
            sources = emptyList(),
            loadError = message,
            isAniyomiFormat = true,
        )

        if (appInfo == null) return failed("Not installed correctly")
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
            ?: return failed("Unreadable extension version \"$versionName\"")
        if (libVersion < AniyomiRuntime.MIN_LIB_VERSION || libVersion > AniyomiRuntime.MAX_LIB_VERSION) {
            return failed(
                "Built for extension API ${libVersion.toInt()}; Ansu runs " +
                    "${AniyomiRuntime.MIN_LIB_VERSION.toInt()}-${AniyomiRuntime.MAX_LIB_VERSION.toInt()}",
            )
        }

        val metaData = appInfo.metaData
        val classNames = metaData?.getString(CLASS_METADATA)
            ?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }
            .orEmpty()
            .map { if (it.startsWith(".")) pkgInfo.packageName + it else it }
        if (classNames.isEmpty()) return failed("No source class declared in extension metadata")

        // Retry setup here in case it failed at startup, so the real cause shows up instead of a missing instance.
        try {
            AniyomiRuntime.install(context, AniyomiRuntime.sharedClient ?: okhttp3.OkHttpClient())
        } catch (t: Throwable) {
            android.util.Log.e("AniyomiLoader", "Injekt setup failed", t)
            return failed("Injekt setup failed: ${t::class.java.simpleName}: ${t.message}")
        }

        val classLoader = PathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
        val errors = mutableListOf<String>()
        val sources = classNames.flatMap { className ->
            try {
                val instance = Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()
                when (instance) {
                    is AnimeSourceFactory -> instance.createSources()
                    is AnimeSource -> listOf(instance)
                    else -> emptyList()
                }
            } catch (e: Throwable) {
                // newInstance() wraps anything the constructor throws in InvocationTargetException,
                // which hides the real problem. Unwrap to the actual cause.
                val root = rootCause(e)
                android.util.Log.e("AniyomiLoader", "Failed to load $className", e)
                errors += "${root::class.java.simpleName}: ${root.message ?: className}"
                emptyList()
            }
        }.filterIsInstance<AnimeCatalogueSource>().map { AniyomiSourceAdapter(it) }

        return InstalledExtension(
            packageName = pkgInfo.packageName,
            displayName = displayName,
            versionName = versionName,
            sources = sources,
            isNsfw = metaData?.getInt(NSFW_METADATA, 0) == 1,
            loadError = if (sources.isEmpty()) errors.firstOrNull() ?: "No sources found in this extension" else null,
            isAniyomiFormat = true,
        )
    }

    private fun rootCause(e: Throwable): Throwable {
        var t = e
        while (true) {
            val next = when (t) {
                is java.lang.reflect.InvocationTargetException -> t.targetException
                is ExceptionInInitializerError -> t.cause
                else -> null
            } ?: t.cause?.takeIf { t is java.lang.reflect.InvocationTargetException || t is ExceptionInInitializerError }
            if (next == null || next === t) return t
            t = next
        }
    }

    private companion object {
        const val CLASS_METADATA = "tachiyomi.animeextension.class"
        const val NSFW_METADATA = "tachiyomi.animeextension.nsfw"
    }
}
