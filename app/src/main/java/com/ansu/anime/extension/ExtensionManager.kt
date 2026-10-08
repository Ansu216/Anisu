package com.ansu.anime.extension

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.pm.PackageInfoCompat
import com.ansu.anime.core.util.ContentFilter
import com.ansu.anime.extension.aniyomi.AniyomiExtensionLoader
import com.ansu.anime.extension.api.AnimeCatalogueSource
import dalvik.system.PathClassLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

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
    /** Android version code, used to tell whether a repo offers a newer build. */
    val versionCode: Long = 0,
    /** True when the APK was downloaded inside Ansu and lives in its private storage, not installed on the phone. */
    val isPrivate: Boolean = false,
    val icon: Drawable? = null,
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
        const val ANIYOMI_METADATA_NSFW = "tachiyomi.animeextension.nsfw"
        const val ANIYOMI_METADATA_CONTENT_WARNING = "aniyomix.contentWarning"

        /** Extension files downloaded inside Ansu are kept as `<package>.ext` in this folder of the app's private storage. */
        private const val PRIVATE_DIR = "exts"
        private const val PRIVATE_SUFFIX = ".ext"
    }

    private val privateDir: File get() = File(context.filesDir, PRIVATE_DIR).apply { mkdirs() }

    private fun privateFile(packageName: String) = File(privateDir, packageName + PRIVATE_SUFFIX)

    /** Where the native libraries (`.so`) of a private extension are unpacked. */
    private fun libsDir(packageName: String) = File(context.filesDir, "exts-libs/$packageName")

    /**
     * Unpacks the extension's native libraries for this device. An APK loaded from a plain file has no
     * Android-managed library folder, so without this an extension that bundles a `.so` fails to load.
     * The first of the device's supported ABIs that the APK actually ships is used.
     */
    private fun extractNativeLibs(apk: File, packageName: String) {
        val dir = libsDir(packageName)
        dir.deleteRecursively()
        ZipFile(apk).use { zip ->
            val libs = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("lib/") && it.name.endsWith(".so") }
                .toList()
            if (libs.isEmpty()) return
            val abi = Build.SUPPORTED_ABIS.firstOrNull { candidate -> libs.any { it.name.startsWith("lib/$candidate/") } }
                ?: return
            dir.mkdirs()
            libs.filter { it.name.startsWith("lib/$abi/") }.forEach { entry ->
                val out = File(dir, entry.name.substringAfterLast('/'))
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                out.setReadOnly()
            }
        }
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

    /** Reads an extension APK straight from a file, without installing it. Null if it is not a readable extension. */
    @Suppress("DEPRECATION")
    private fun readArchive(file: File): PackageInfo? {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        info.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
            // Loaders pass this to the class loader so System.loadLibrary finds the unpacked libraries.
            nativeLibraryDir = libsDir(info.packageName).absolutePath
        }
        return info
    }

    /** Every extension downloaded inside Ansu (see [installPrivate]). */
    private fun findPrivateExtensions(): List<PackageInfo> =
        privateDir.listFiles { f -> f.isFile && f.name.endsWith(PRIVATE_SUFFIX) }.orEmpty()
            .mapNotNull { file -> runCatching { readArchive(file) }.getOrNull() }

    /**
     * Installs a downloaded extension APK inside Ansu: validates it, moves it into the app's private
     * storage and loads it. Nothing is installed on the phone, so there is no Android installer prompt.
     * Android 14+ refuses to load code from a writable file, so the stored copy is made read-only.
     */
    fun installPrivate(apk: File): Result<InstalledExtension> = runCatching {
        val info = readArchive(apk) ?: error("The downloaded file is not a valid extension")
        val meta = info.applicationInfo?.metaData
        val isExtension = info.reqFeatures?.any { it.name == ANIYOMI_FEATURE } == true ||
            !meta?.getString(METADATA_SOURCE_CLASS).isNullOrBlank()
        if (!isExtension) error("The downloaded file is not an anime extension")
        if (isAdultExtension(info)) {
            apk.delete()
            error("18+ extensions are blocked by the built-in content filter")
        }

        val target = privateFile(info.packageName)
        if (target.exists()) {
            target.setWritable(true)
            target.delete()
        }
        apk.copyTo(target, overwrite = true)
        apk.delete()
        target.setReadOnly()
        extractNativeLibs(target, info.packageName)

        val loaded = reloadAll()
        loaded.firstOrNull { it.packageName == info.packageName }
            ?: error("Extension could not be loaded, or was blocked by the built-in 18+ content filter")
    }

    /**
     * Re-scans the device and the private folder and (re)loads every discoverable extension. Call off the main thread.
     * The built-in 18+ filter applies here: an adult extension is never loaded, so none of its sources reach Home,
     * title matching or the player. One that was downloaded inside Ansu is deleted; one installed on the phone
     * itself cannot be removed silently, so it is only skipped.
     */
    fun reloadAll(): List<InstalledExtension> {
        val privatePackages = findPrivateExtensions()
        val privateNames = privatePackages.map { it.packageName }.toSet()
        val candidates = privatePackages.map { it to true } +
            findAvailableExtensions().filter { it.packageName !in privateNames }.map { it to false }
        val loaded = candidates.mapNotNull { (pkg, isPrivate) ->
            if (isAdultExtension(pkg)) {
                if (isPrivate) deletePrivate(pkg.packageName)
                return@mapNotNull null
            }
            val ext = loadExtension(pkg, isPrivate = isPrivate)
            // Second pass on what actually loaded: the loader's own NSFW reading and the sources' names.
            if (ext.isNsfw || ext.sources.any { ContentFilter.isAdultName(it.name) }) {
                if (isPrivate) deletePrivate(pkg.packageName)
                return@mapNotNull null
            }
            ext
        }
        val next = LinkedHashMap<Long, AnimeCatalogueSource>()
        builtInSources.forEach { next[it.id] = it }
        loaded.forEach { ext -> ext.sources.forEach { next[it.id] = it } }
        sourcesById = next
        _extensions.update { loaded }
        return loaded
    }

    /** True for an extension that flags itself 18+ in its manifest, or whose package or app name says so. */
    private fun isAdultExtension(info: PackageInfo): Boolean {
        val appInfo = info.applicationInfo
        val meta = appInfo?.metaData
        if (meta != null && (
                meta.getBoolean(METADATA_NSFW, false) ||
                    meta.getInt(ANIYOMI_METADATA_NSFW, 0) == 1 ||
                    meta.getInt(ANIYOMI_METADATA_CONTENT_WARNING, 0) > 0
                )
        ) return true
        val label = appInfo?.let { runCatching { context.packageManager.getApplicationLabel(it).toString() }.getOrNull() }
        return ContentFilter.isAdultName(info.packageName) || ContentFilter.isAdultName(label)
    }

    /** Deletes a private extension's stored APK and unpacked libraries. */
    private fun deletePrivate(packageName: String) {
        val file = privateFile(packageName)
        if (file.exists()) {
            file.setWritable(true)
            file.delete()
        }
        libsDir(packageName).deleteRecursively()
    }

    private fun loadExtension(pkgInfo: PackageInfo, isPrivate: Boolean = false): InstalledExtension {
        val icon = runCatching { pkgInfo.applicationInfo?.loadIcon(context.packageManager) }.getOrNull()
        return loadExtensionInternal(pkgInfo).copy(
            versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
            isPrivate = isPrivate,
            icon = icon,
        )
    }

    private fun loadExtensionInternal(pkgInfo: PackageInfo): InstalledExtension {
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
            val classLoader = PathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
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

    /** Removes an extension: a private one is simply deleted, a phone-installed one goes through Android's uninstall prompt. */
    fun uninstall(packageName: String) {
        if (privateFile(packageName).exists()) {
            deletePrivate(packageName)
            reloadAll()
            return
        }
        val uri = android.net.Uri.parse("package:$packageName")
        val intent = Intent(Intent.ACTION_DELETE, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
