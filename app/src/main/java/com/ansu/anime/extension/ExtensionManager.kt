package com.ansu.anime.extension

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.ansu.anime.extension.aniyomi.AniyomiExtensionLoader
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
    /** True for an Aniyomi/Keiyoushi-format APK (declares `tachiyomi.animeextension`) rather than an Ansu one. */
    val isAniyomiFormat: Boolean = false,
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

        /** Feature + metadata names Aniyomi/Keiyoushi APKs declare, so they can at least be recognised. */
        const val ANIYOMI_FEATURE = "tachiyomi.animeextension"
        const val ANIYOMI_METADATA_CLASS = "tachiyomi.animeextension.class"
    }

    private val prefs = context.getSharedPreferences("extension_sources", Context.MODE_PRIVATE)
    private val _disabledSourceIds = MutableStateFlow(
        prefs.getStringSet("disabled_ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
    )

    /** Ids of sources the person switched off on the Extensions screen; they stay installed but are skipped by Home and Search. */
    val disabledSourceIds: StateFlow<Set<Long>> = _disabledSourceIds

    fun setSourceEnabled(id: Long, enabled: Boolean) {
        val next = if (enabled) _disabledSourceIds.value - id else _disabledSourceIds.value + id
        _disabledSourceIds.value = next
        prefs.edit().putStringSet("disabled_ids", next.map { it.toString() }.toSet()).apply()
    }

    private val _extensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val extensions: StateFlow<List<InstalledExtension>> = _extensions

    @Volatile
    private var sourcesById: Map<Long, AnimeCatalogueSource> = emptyMap()
    private val builtInSources = mutableListOf<AnimeCatalogueSource>()

    /** Registers a source compiled into the app itself (e.g. [DemoSource]), as opposed to a separately installed extension APK. */
    fun registerBuiltIn(source: AnimeCatalogueSource) {
        builtInSources += source
        sourcesById = sourcesById + (source.id to source)
    }

    /** Every package on the device that declares itself as an Ansu or Aniyomi-format anime source. */
    @Suppress("DEPRECATION")
    fun findAvailableExtensions(): List<PackageInfo> {
        val pm = context.packageManager
        val intent = Intent(EXTENSION_ACTION)
        val receivers = try {
            pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }
        val ansuPackages = receivers.mapNotNull { resolveInfo ->
            try {
                pm.getPackageInfo(resolveInfo.activityInfo.packageName, PackageManager.GET_META_DATA)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
        val aniyomiPackages = try {
            pm.getInstalledPackages(PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA)
                .filter { pkg -> pkg.reqFeatures?.any { it.name == ANIYOMI_FEATURE } == true }
        } catch (e: Exception) {
            emptyList()
        }
        return (ansuPackages + aniyomiPackages).distinctBy { it.packageName }
    }

    /** Re-scans the device and (re)loads every discoverable extension. Call off the main thread. */
    fun reloadAll(): List<InstalledExtension> {
        val loaded = findAvailableExtensions().map { pkgInfo -> loadExtension(pkgInfo) }
        val next = LinkedHashMap<Long, AnimeCatalogueSource>()
        builtInSources.forEach { next[it.id] = it }
        loaded.forEach { ext -> ext.sources.forEach { next[it.id] = it } }
        sourcesById = next
        _extensions.update { loaded }
        return loaded
    }

    private fun loadExtension(pkgInfo: PackageInfo): InstalledExtension {
        val pm = context.packageManager
        val appInfo = pkgInfo.applicationInfo
        val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkgInfo.packageName
        val versionName = pkgInfo.versionName ?: "?"

        val metaData = appInfo?.metaData
        val isAniyomi = pkgInfo.reqFeatures?.any { it.name == ANIYOMI_FEATURE } == true &&
            metaData?.getString(METADATA_SOURCE_CLASS).isNullOrBlank()
        if (isAniyomi) return AniyomiExtensionLoader(context).load(pkgInfo, label)
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

    /** Every source that is switched on; Home and Search fan out over this list. */
    fun allSources(): List<AnimeCatalogueSource> =
        sourcesById.values.filter { it.id !in _disabledSourceIds.value }

    /** Every source including switched-off ones, for the Extensions screen. */
    fun allSourcesIncludingDisabled(): List<AnimeCatalogueSource> = sourcesById.values.toList()

    fun uninstall(packageName: String) {
        val uri = android.net.Uri.parse("package:$packageName")
        val intent = Intent(Intent.ACTION_DELETE, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
