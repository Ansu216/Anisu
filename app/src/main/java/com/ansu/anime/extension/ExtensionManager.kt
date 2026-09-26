package com.ansu.anime.extension

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.ansu.anime.extension.api.AnimeCatalogueSource
import dalvik.system.PathClassLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * An extension the app found installed on the device, whether or not it
 * loaded successfully.
 */
data class InstalledExtension(
    val packageName: String,
    val displayName: String,
    val versionName: String,
    val sources: List<AnimeCatalogueSource>,
    val isNsfw: Boolean = false,
    val loadError: String? = null,
) {
    val isValid: Boolean get() = loadError == null && sources.isNotEmpty()
}

/**
 * Finds anime source extensions installed as separate APKs and loads their
 * source classes with a [PathClassLoader], the same discovery + reflection
 * approach Keiyoushi/Aniyomi extensions use:
 *
 *  - An extension APK declares an empty `<receiver>` with an intent-filter for
 *    [EXTENSION_ACTION]. That's enough for [findAvailableExtensions] to spot it
 *    with a plain package query, no custom permission required.
 *  - The same APK's `<application>` tag carries a `<meta-data>` entry named
 *    [METADATA_SOURCE_CLASS] whose value is a semicolon-separated list of fully
 *    qualified class names implementing [AnimeCatalogueSource] (an APK can
 *    bundle more than one source, e.g. one per language).
 *  - We open a [PathClassLoader] on the extension's APK path and instantiate
 *    each class via reflection, preferring a `(Context)` constructor and
 *    falling back to a no-arg one.
 */
class ExtensionManager(private val context: Context) {

    companion object {
        const val EXTENSION_ACTION = "com.ansu.anime.extension.ANIME_SOURCE"
        const val METADATA_SOURCE_CLASS = "ansu.anime.extension.class"
        const val METADATA_NSFW = "ansu.anime.extension.nsfw"
    }

    private val _extensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val extensions: StateFlow<List<InstalledExtension>> = _extensions

    private val sourcesById = mutableMapOf<Long, AnimeCatalogueSource>()
    private val builtInSources = mutableListOf<AnimeCatalogueSource>()

    /** Registers a source compiled into the app itself (e.g. [DemoSource]), as opposed to a separately installed extension APK. */
    fun registerBuiltIn(source: AnimeCatalogueSource) {
        builtInSources += source
        sourcesById[source.id] = source
    }

    /** Every package on the device that declares itself as a Anisu anime source. */
    fun findAvailableExtensions(): List<PackageInfo> {
        val pm = context.packageManager
        val intent = Intent(EXTENSION_ACTION)
        val receivers = try {
            pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }
        return receivers.mapNotNull { resolveInfo ->
            try {
                pm.getPackageInfo(resolveInfo.activityInfo.packageName, PackageManager.GET_META_DATA)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    /** Re-scans the device and (re)loads every discoverable extension. Call off the main thread. */
    fun reloadAll(): List<InstalledExtension> {
        val loaded = findAvailableExtensions().map { pkgInfo -> loadExtension(pkgInfo) }
        sourcesById.clear()
        builtInSources.forEach { sourcesById[it.id] = it }
        loaded.forEach { ext -> ext.sources.forEach { sourcesById[it.id] = it } }
        _extensions.update { loaded }
        return loaded
    }

    private fun loadExtension(pkgInfo: PackageInfo): InstalledExtension {
        val pm = context.packageManager
        val appInfo = pkgInfo.applicationInfo
        val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkgInfo.packageName
        val versionName = pkgInfo.versionName ?: "?"

        val metaData = appInfo?.metaData
        val classNames = metaData?.getString(METADATA_SOURCE_CLASS)?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }

        if (appInfo == null || classNames.isNullOrEmpty()) {
            return InstalledExtension(
                packageName = pkgInfo.packageName,
                displayName = label,
                versionName = versionName,
                sources = emptyList(),
                loadError = "No source class declared in extension metadata",
            )
        }

        return try {
            val classLoader = PathClassLoader(appInfo.sourceDir, context.classLoader)
            val sources = classNames.mapNotNull { className -> instantiateSource(classLoader, className) }
            InstalledExtension(
                packageName = pkgInfo.packageName,
                displayName = label,
                versionName = versionName,
                sources = sources,
                isNsfw = metaData.getBoolean(METADATA_NSFW, false),
                loadError = if (sources.isEmpty()) "Declared source classes failed to load" else null,
            )
        } catch (e: Throwable) {
            InstalledExtension(
                packageName = pkgInfo.packageName,
                displayName = label,
                versionName = versionName,
                sources = emptyList(),
                loadError = e.message ?: e::class.simpleName,
            )
        }
    }

    private fun instantiateSource(classLoader: PathClassLoader, className: String): AnimeCatalogueSource? {
        return try {
            val clazz = Class.forName(className, true, classLoader)
            val instance = try {
                clazz.getConstructor(Context::class.java).newInstance(context)
            } catch (e: NoSuchMethodException) {
                clazz.getDeclaredConstructor().newInstance()
            }
            instance as? AnimeCatalogueSource
        } catch (e: Throwable) {
            null
        }
    }

    fun getSource(id: Long): AnimeCatalogueSource? = sourcesById[id]

    fun allSources(): List<AnimeCatalogueSource> = sourcesById.values.toList()

    fun uninstall(packageName: String) {
        val uri = android.net.Uri.parse("package:$packageName")
        val intent = Intent(Intent.ACTION_DELETE, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
