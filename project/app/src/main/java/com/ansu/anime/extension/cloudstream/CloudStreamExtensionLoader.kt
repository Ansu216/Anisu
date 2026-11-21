package com.ansu.anime.extension.cloudstream

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import com.ansu.anime.core.util.ContentFilter
import com.ansu.anime.extension.InstalledExtension
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.PathClassLoader
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * Loads CloudStream plugins (`.cs3`). A `.cs3` is a zip holding `classes.dex` and a `manifest.json`
 * (`pluginClassName`, `name`, `version`, `requiresResources`). It is loaded the way CloudStream does it: open the file
 * with a class loader whose parent is Anisu (which carries the CloudStream API classes), instantiate the plugin class and
 * call `load`. The plugin then registers its `MainAPI` providers in the library's global registry, from where they
 * are picked up and wrapped as ordinary Anisu sources.
 */
internal class CloudStreamExtensionLoader(private val context: Context) {

    data class Manifest(
        val pluginClassName: String?,
        val name: String,
        val version: Int,
        val requiresResources: Boolean,
    ) {
        /** Same id the repo index uses for the plugin (`cs3.<internalName>`), so installed and available rows match. */
        val packageName: String get() = PACKAGE_PREFIX + name
    }

    /** What is loaded right now, by file path, so a reload can unregister it first. */
    private val loaded = HashMap<String, BasePlugin>()

    /** Reads `manifest.json` of a `.cs3`; null when the file is not one (an APK, for instance, has no such entry). */
    fun readManifest(file: File): Manifest? = runCatching {
        ZipFile(file).use { zip ->
            if (zip.getEntry("classes.dex") == null || zip.getEntry("AndroidManifest.xml") != null) return null
            val entry = zip.getEntry("manifest.json") ?: return null
            val json = JSONObject(zip.getInputStream(entry).bufferedReader().use { it.readText() })
            val name = json.optString("name").ifBlank { return null }
            Manifest(
                pluginClassName = json.optString("pluginClassName").takeIf { it.isNotBlank() && it != "null" },
                name = name,
                version = json.optInt("version", 0),
                requiresResources = json.optBoolean("requiresResources", false),
            )
        }
    }.getOrNull()

    /** Unregisters every plugin loaded so far. Call before loading again, or providers would be registered twice. */
    @Synchronized
    fun unloadAll() {
        loaded.keys.toList().forEach { unload(it) }
    }

    private fun unload(path: String) {
        val plugin = loaded.remove(path) ?: return
        runCatching { plugin.beforeUnload() }
        runCatching { synchronized(APIHolder.allProviders) { APIHolder.allProviders.removeAll { it.sourcePlugin == path } } }
        runCatching { extractorApis.removeAll { it.sourcePlugin == path } }
    }

    @Synchronized
    fun load(file: File, manifest: Manifest): InstalledExtension {
        fun failed(message: String) = InstalledExtension(
            packageName = manifest.packageName,
            displayName = manifest.name,
            versionName = manifest.version.toString(),
            sources = emptyList(),
            loadError = message,
            versionCode = manifest.version.toLong(),
            isCloudStreamFormat = true,
        )

        val className = manifest.pluginClassName ?: return failed("The plugin does not name its entry class")
        val path = file.absolutePath
        unload(path)

        return try {
            // Android 14+ refuses to load code from a writable file.
            if (file.canWrite()) file.setReadOnly()
            val classLoader = PathClassLoader(path, context.classLoader)
            val plugin = Class.forName(className, true, classLoader).getDeclaredConstructor().newInstance() as? BasePlugin
                ?: return failed("$className is not a CloudStream plugin")
            plugin.filename = path
            if (plugin is Plugin) {
                if (manifest.requiresResources) plugin.resources = openResources(path)
                plugin.load(context)
            } else {
                plugin.load()
            }
            loaded[path] = plugin

            val apis = synchronized(APIHolder.allProviders) { APIHolder.allProviders.filter { it.sourcePlugin == path } }
            if (apis.isEmpty()) return failed("The plugin registered no providers")

            // The built-in 18+ filter: a provider that declares NSFW, or is named like one, marks the whole plugin adult
            // and ExtensionManager then hides it unless NSFW is switched on, as for an adult Aniyomi extension.
            val adult = apis.any { TvType.NSFW in it.supportedTypes || ContentFilter.isAdultName(it.name) } ||
                ContentFilter.isAdultName(manifest.name)

            val sources = apis.filter { api -> api.supportedTypes.any { it in ANIME_TYPES } }
                .map { CloudStreamSourceAdapter(it) }
            InstalledExtension(
                packageName = manifest.packageName,
                displayName = manifest.name,
                versionName = manifest.version.toString(),
                sources = sources,
                isNsfw = adult,
                loadError = if (sources.isEmpty()) "None of this plugin's providers serve anime" else null,
                versionCode = manifest.version.toLong(),
                isCloudStreamFormat = true,
            )
        } catch (e: Throwable) {
            android.util.Log.e("CloudStreamLoader", "Failed to load ${manifest.name}", e)
            unload(path)
            failed(describePluginError(e))
        }
    }

    /** Resources packed in the plugin, for the few that need them (CloudStream builds them the same way). */
    private fun openResources(path: String): Resources? = runCatching {
        val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
        AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, path)
        @Suppress("DEPRECATION")
        Resources(assets, context.resources.displayMetrics, context.resources.configuration)
    }.getOrNull()

    companion object {
        const val PACKAGE_PREFIX = "cs3."

        /** Provider types Anisu serves; CloudStream plugins also carry movies, series, torrents... which are left out. */
        val ANIME_TYPES = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    }
}
