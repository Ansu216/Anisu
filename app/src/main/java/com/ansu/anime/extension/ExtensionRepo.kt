package com.ansu.anime.extension

import android.content.Context
import com.ansu.anime.core.util.ContentFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** One source an extension bundles, as published in a repo's `index.min.json`. */
@Serializable
data class ExtensionRepoSource(
    val name: String = "",
    val lang: String = "",
    val id: String? = null,
    val baseUrl: String? = null,
)

/** One row of a repo's `index.min.json`, in the shape Keiyoushi repos publish. */
@Serializable
data class ExtensionRepoEntry(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String = "",
    val version: String = "",
    val code: Int = 0,
    val nsfw: Int = 0,
    val icon: String? = null,
    val sources: List<ExtensionRepoSource> = emptyList(),
)

/** True for an 18+ extension: it flags itself `nsfw`, or it or one of its sources has an adult name. */
fun ExtensionRepoEntry.isAdult(): Boolean =
    nsfw == 1 || ContentFilter.isAdultName(name) || ContentFilter.isAdultName(pkg) ||
        sources.any { ContentFilter.isAdultName(it.name) }

/** What a pasted JSON URL turned out to be. */
sealed class JsonUrlResult {
    /** An extension repo index: [entries] were read from [indexUrl]. */
    data class Repo(val indexUrl: String, val entries: List<ExtensionRepoEntry>) : JsonUrlResult()

    /** A Stremio/Nuvio addon manifest: the caller adds it through the addon manager. */
    data class AddonManifest(val manifestUrl: String) : JsonUrlResult()
}

/**
 * Reads whatever JSON URL the person pastes into the Extensions screen, keeps the
 * list of repos they added, and downloads the APKs they pick so they can be
 * installed inside the app.
 */
class ExtensionRepo(
    private val context: Context,
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val prefs = context.getSharedPreferences("extension_repos", Context.MODE_PRIVATE)

    /** Index URLs of the repos added so far, oldest first. */
    fun savedRepoUrls(): List<String> =
        prefs.getString(KEY_REPOS, "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun saveRepoUrl(indexUrl: String) {
        val all = (savedRepoUrls() + indexUrl).distinct()
        prefs.edit().putString(KEY_REPOS, all.joinToString("\n")).apply()
    }

    fun removeRepoUrl(indexUrl: String) {
        val all = savedRepoUrls() - indexUrl
        prefs.edit().putString(KEY_REPOS, all.joinToString("\n")).apply()
    }

    /**
     * Turns what a person pasted into the URL candidates worth trying: handles
     * `aniyomi://add-repo?url=`, `tachiyomi://`, `stremio://`, GitHub "blob" links
     * and bare repo folders (which get `index.min.json` appended).
     */
    fun candidates(input: String): List<String> {
        var url = input.trim()
        if (url.isEmpty()) return emptyList()
        if (url.startsWith("aniyomi://") || url.startsWith("tachiyomi://") || url.startsWith("mihon://")) {
            url = android.net.Uri.parse(url).getQueryParameter("url") ?: url
        }
        if (url.startsWith("stremio://")) url = "https://" + url.removePrefix("stremio://")
        if (url.startsWith("github.com/") || url.startsWith("raw.githubusercontent.com/")) url = "https://$url"
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        url = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").replace(url) { m ->
            "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}"
        }
        val trimmed = url.trimEnd('/')
        val looksLikeFile = trimmed.substringAfterLast('/').contains('.')
        return if (looksLikeFile) listOf(trimmed) else listOf("$trimmed/index.min.json", "$trimmed/manifest.json", trimmed)
    }

    /** Fetches [input] and works out which kind of JSON it is. */
    suspend fun resolve(input: String): Result<JsonUrlResult> = withContext(Dispatchers.IO) {
        val urls = candidates(input)
        if (urls.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Paste a JSON URL first"))
        var lastError: Throwable? = null
        for (url in urls) {
            val parsed = runCatching { fetchAndClassify(url) }
            if (parsed.isSuccess) return@withContext parsed
            lastError = parsed.exceptionOrNull()
        }
        Result.failure(lastError ?: IllegalStateException("Could not read that URL"))
    }

    private fun fetchAndClassify(url: String): JsonUrlResult {
        val request = Request.Builder().url(url).build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} from $url")
            response.body.string()
        }
        val root = json.parseToJsonElement(body)
        return classify(url, root)
    }

    private fun classify(url: String, root: JsonElement): JsonUrlResult {
        if (root is JsonArray) return JsonUrlResult.Repo(url, parseEntries(root))
        val obj = root as? JsonObject ?: error("That URL does not return a JSON repo or addon manifest")
        val isManifest = obj.containsKey("resources") || obj.containsKey("catalogs") ||
            (obj.containsKey("id") && obj.containsKey("name") && obj.containsKey("version") && !obj.containsKey("pkg"))
        if (isManifest) return JsonUrlResult.AddonManifest(url)
        for (key in listOf("extensions", "sources", "plugins", "items", "data")) {
            val arr = obj[key]?.let { runCatching { it.jsonArray }.getOrNull() } ?: continue
            return JsonUrlResult.Repo(url, parseEntries(arr))
        }
        error("Unrecognised JSON: expected an extension repo (list of extensions) or an addon manifest")
    }

    private fun parseEntries(array: JsonArray): List<ExtensionRepoEntry> {
        val entries = array.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(ExtensionRepoEntry.serializer(), element.jsonObject) }.getOrNull()
        }
        if (entries.isEmpty() && array.isNotEmpty()) error("The list is not in a supported extension repo format")
        // The built-in adult filter: 18+ extensions are never listed, so they cannot be installed.
        return entries.filterNot { it.isAdult() }
    }

    /** Base folder an entry's relative `apk` / `icon` paths hang off. */
    fun baseOf(indexUrl: String): String = indexUrl.substringBeforeLast('/')

    /** Keiyoushi repos keep APKs in `/apk` beside the index; flat repos keep them next to it. */
    fun apkUrls(indexUrl: String, entry: ExtensionRepoEntry): List<String> {
        if (entry.apk.startsWith("http")) return listOf(entry.apk)
        val base = baseOf(indexUrl)
        return listOf("$base/apk/${entry.apk}", "$base/${entry.apk}")
    }

    fun iconUrl(indexUrl: String, entry: ExtensionRepoEntry): String? {
        val icon = entry.icon
        if (icon != null) return if (icon.startsWith("http")) icon else "${baseOf(indexUrl)}/$icon"
        return "${baseOf(indexUrl)}/icon/${entry.pkg}.png"
    }

    /**
     * Downloads the extension APK into the app's cache and returns the file. The caller hands it to
     * `ExtensionManager.installPrivate`, which stores it inside Ansu; nothing is installed on the phone.
     */
    suspend fun downloadApk(indexUrl: String, entry: ExtensionRepoEntry): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val apkFile = File(context.cacheDir, "${entry.pkg}-${entry.version}.apk")
            var downloaded = false
            var lastCode = 0
            for (apkUrl in apkUrls(indexUrl, entry)) {
                client.newCall(Request.Builder().url(apkUrl).build()).execute().use { response ->
                    lastCode = response.code
                    if (!response.isSuccessful) return@use
                    response.body.byteStream().use { input -> apkFile.outputStream().use { output -> input.copyTo(output) } }
                    downloaded = true
                }
                if (downloaded) break
            }
            if (!downloaded) error("Download failed: HTTP $lastCode")
            apkFile
        }
    }

    private companion object {
        const val KEY_REPOS = "repo_urls"
    }
}
