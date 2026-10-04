package com.ansu.anime.extension.aniyomi

import android.content.Context
import android.content.pm.PackageInfo
import com.ansu.anime.extension.InstalledExtension
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader

/**
 * Loads an Aniyomi/Keiyoushi extension APK the way Aniyomi does: read the class names from the
 * `tachiyomi.animeextension.class` metadata, open the APK with a class loader whose parent is Ansu
 * (so the extension finds the `eu.kanade.tachiyomi.*` API there) and instantiate each class.
 */
internal class AniyomiExtensionLoader(private val context: Context) {

    fun load(pkgInfo: PackageInfo, label: String): InstalledExtension {
        val appInfo = pkgInfo.applicationInfo
        val versionName = pkgInfo.versionName ?: "?"
        val displayName = appInfo?.metaData?.getString(NAME_METADATA) ?: label.removePrefix("Aniyomi: ")
        fun failed(message: String) = InstalledExtension(
            packageName = pkgInfo.packageName,
            displayName = displayName,
            versionName = versionName,
            sources = emptyList(),
            loadError = message,
            isAniyomiFormat = true,
        )

        if (appInfo == null) return failed("Not installed correctly")
        // Aniyomi's own builds declare the library in metadata; Keiyoushi/older ones only in the version name.
        val libVersion = appInfo.metaData?.getInt(LIB_METADATA)?.takeIf { it != 0 }?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
            ?: return failed("Unreadable extension version \"$versionName\"")
        if (libVersion < AniyomiRuntime.MIN_LIB_VERSION || libVersion > AniyomiRuntime.MAX_LIB_VERSION) {
            return failed(
                "Built for extension API ${libVersion.toInt()}; Anisu runs " +
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

        val classLoader = try {
            ChildFirstPathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
        } catch (e: Exception) {
            android.util.Log.e("AniyomiLoader", "Could not open $displayName", e)
            return failed("Could not open the extension: ${e.message ?: e::class.java.simpleName}")
        }
        val errors = mutableListOf<String>()
        val sources = classNames.flatMap { className ->
            try {
                instantiate(className, classLoader)
            } catch (e: LinkageError) {
                // The extension's own copy of a class clashed with Ansu's; Aniyomi retries with the plain
                // parent-first loader before giving up.
                try {
                    instantiate(className, PathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader))
                } catch (e2: Throwable) {
                    errors += describe(className, e2)
                    emptyList()
                }
            } catch (e: Throwable) {
                errors += describe(className, e)
                emptyList()
            }
        }.filterIsInstance<AnimeCatalogueSource>().map { AniyomiSourceAdapter(it, libVersion) }

        return InstalledExtension(
            packageName = pkgInfo.packageName,
            displayName = displayName,
            versionName = versionName,
            sources = sources,
            isNsfw = metaData?.getInt(NSFW_METADATA, 0) == 1 || (metaData?.getInt(CONTENT_WARNING_METADATA, 0) ?: 0) > 0,
            loadError = if (sources.isEmpty()) errors.firstOrNull() ?: "No sources found in this extension" else null,
            isAniyomiFormat = true,
        )
    }

    private fun instantiate(className: String, classLoader: ClassLoader): List<AnimeSource> =
        when (val instance = Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()) {
            is AnimeSourceFactory -> instance.createSources()
            is AnimeSource -> listOf(instance)
            else -> throw IllegalStateException("Unknown source class type: ${instance.javaClass}")
        }

    /** `newInstance()` wraps what a constructor throws in InvocationTargetException; report the real cause. */
    private fun describe(className: String, e: Throwable): String {
        val root = rootCause(e)
        android.util.Log.e("AniyomiLoader", "Failed to load $className", e)
        return "${root::class.java.simpleName}: ${root.message ?: className}"
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
        const val NAME_METADATA = "aniyomix.name"
        const val LIB_METADATA = "aniyomix.extensionLib"
        const val CONTENT_WARNING_METADATA = "aniyomix.contentWarning"
    }
}
