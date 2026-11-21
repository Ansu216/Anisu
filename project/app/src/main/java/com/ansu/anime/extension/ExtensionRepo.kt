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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
    /** True for a CloudStream plugin: [apk] is then the absolute `.cs3` url and [pkg] is `cs3.<internalName>`. */
    val cloudstream: Boolean = false,
    /** CloudStream's content types for the plugin (`Anime`, `Movie`, `NSFW`...). */
    val tvTypes: List<String> = emptyList(),
)

/** True for an 18+ extension: it flags itself `nsfw`, or it or one of its sources has an adult name. */
fun ExtensionRepoEntry.isAdult(): Boolean =
    nsfw == 1 || ContentFilter.isAdultName(name) || ContentFilter.isAdultName(pkg) ||
        tvTypes.any { it.equals("NSFW", ignoreCase = true) } ||
        sources.any { ContentFilter.isAdultName(it.name) }

/** What a pasted JSON URL turned out to be. */
sealed class JsonUrlResult {
    /** An extension repo index: [entries] were read from [indexUrl]. */
    data class Repo(val indexUrl: String, val entries: List<ExtensionRepoEntry>, val name: String? = null) : JsonUrlResult()
}

/**
 * The name to show for a repo: the one it publishes (a CloudStream `repo.json` has one) when known, otherwise
 * worked out from its address (`user/repo` for GitHub and Forgejo links, else the host), so two repos can be told apart
 * at a glance instead of by a long link.
 */
fun repoDisplayName(indexUrl: String, savedName: String?): String {
    savedName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val patterns = listOf(
        Regex("^https?://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/"),
        Regex("^https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/|$)"),
        Regex("^https?://[^/]+/([^/]+)/([^/]+)/raw/"),
    )
    for (p in patterns) {
        p.find(indexUrl)?.let { return "${it.groupValues[1]}/${it.groupValues[2]}" }
    }
    return indexUrl.substringAfter("://").substringBefore('/').removePrefix("www.").ifBlank { indexUrl }
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

    fun saveRepoUrl(indexUrl: String, name: String? = null) {
        val all = (savedRepoUrls() + indexUrl).distinct()
        prefs.edit().putString(KEY_REPOS, all.joinToString("\n")).apply()
        if (!name.isNullOrBlank()) saveRepoName(indexUrl, name)
    }

    /** Names repos published for themselves, by index url (stored one `url<TAB>name` per line). */
    fun savedRepoNames(): Map<String, String> =
        prefs.getString(KEY_REPO_NAMES, "").orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()

    fun saveRepoName(indexUrl: String, name: String) {
        val clean = name.replace('\t', ' ').replace('\n', ' ').trim()
        if (clean.isEmpty()) return
        val all = savedRepoNames() + (indexUrl to clean)
        prefs.edit().putString(KEY_REPO_NAMES, all.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }

    fun removeRepoUrl(indexUrl: String) {
        val all = savedRepoUrls() - indexUrl
        prefs.edit().putString(KEY_REPOS, all.joinToString("\n")).apply()
        val names = savedRepoNames() - indexUrl
        prefs.edit().putString(KEY_REPO_NAMES, names.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
        setRepoEnabled(indexUrl, true)
    }

    /** Index URLs of repos that are switched off: still saved, but their extensions are not listed. */
    fun disabledRepoUrls(): Set<String> =
        prefs.getString(KEY_DISABLED_REPOS, "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    fun setRepoEnabled(indexUrl: String, enabled: Boolean) {
        val off = if (enabled) disabledRepoUrls() - indexUrl else disabledRepoUrls() + indexUrl
        prefs.edit().putString(KEY_DISABLED_REPOS, off.joinToString("\n")).apply()
    }

    /**
     * Turns what a person pasted into the URL candidates worth trying: handles
     * `aniyomi://add-repo?url=`, `tachiyomi://`, GitHub "blob" links
     * and bare repo folders (which get `index.min.json` appended).
     */
    fun candidates(input: String): List<String> {
        var url = input.trim()
        if (url.isEmpty()) return emptyList()
        if (url.startsWith("aniyomi://") || url.startsWith("tachiyomi://") || url.startsWith("mihon://")) {
            url = android.net.Uri.parse(url).getQueryParameter("url") ?: url
        }
        if (url.startsWith("cloudstreamrepo://")) {
            // CloudStream share links carry either a full address or a cutt.ly short code (which redirects to the repo.json).
            val rest = url.removePrefix("cloudstreamrepo://").trim('/')
            url = if (rest.contains('.') || rest.contains('/')) rest else "https://cutt.ly/$rest"
        }
        if (url.startsWith("github.com/") || url.startsWith("raw.githubusercontent.com/")) url = "https://$url"
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        url = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").replace(url) { m ->
            "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}"
        }
        // A bare github.com/user/repo link: Keiyoushi-style repos publish the index on the `repo` branch.
        Regex("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$").matchEntire(url)?.let { m ->
            val (user, repo) = m.destructured
            url = "https://raw.githubusercontent.com/$user/$repo/refs/heads/repo"
        }
        val trimmed = url.trimEnd('/')
        val looksLikeFile = trimmed.substringAfterLast('/').contains('.')
        val primary = if (looksLikeFile) listOf(trimmed) else listOf("$trimmed/index.min.json", trimmed)
        return (mirrorsFor(primary.first()) + primary + branchFallbacks(primary.first())).distinct()
    }

    /**
     * Kohi-den moved its extension repo off GitHub (every `raw.githubusercontent.com/Kohi-den/extensions/...` URL now
     * 404s). Its index lives on its own Forgejo host, so that address is tried first for any Kohi-den link.
     */
    private fun mirrorsFor(url: String): List<String> {
        val m = Regex("^https://raw\\.githubusercontent\\.com/Kohi-den/extensions/(?:refs/heads/)?[^/]+/(.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(url) ?: return emptyList()
        return listOf("https://kohiden.xyz/Kohi-den/extensions/raw/branch/main/${m.groupValues[1]}")
    }

    /**
     * Extension repos keep the built index on a `repo` branch, not `main`. A pasted
     * `raw.githubusercontent.com/<user>/<repo>/main/index.min.json` therefore 404s, so the same file is
     * also tried on `refs/heads/repo` and `repo`.
     */
    private fun branchFallbacks(url: String): List<String> {
        val m = Regex("^(https://raw\\.githubusercontent\\.com/[^/]+/[^/]+)/(?:refs/heads/)?(main|master|repo)/(.+)$")
            .matchEntire(url) ?: return emptyList()
        val (root, branch, file) = m.destructured
        return listOf("refs/heads/repo", "repo", "refs/heads/main", "main")
            .filterNot { it.substringAfterLast('/') == branch }
            .map { "$root/$it/$file" }
    }

    /** Fetches [input] and works out which kind of JSON it is. */
    suspend fun resolve(input: String): Result<JsonUrlResult> = withContext(Dispatchers.IO) {
        val urls = candidates(input)
        if (urls.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Paste a JSON URL first"))
        val errors = mutableListOf<Throwable>()
        for (url in urls) {
            val parsed = runCatching { fetchAndClassify(url) }
            if (parsed.isSuccess) return@withContext parsed
            parsed.exceptionOrNull()?.let { errors += it }
        }
        // Reporting only the last attempt hid the real problem (a 404 on the final fallback branch looked like the
        // pasted link itself was wrong); every address that was tried is listed instead.
        val message = if (errors.size <= 1) {
            errors.firstOrNull()?.message ?: "Could not read that URL"
        } else {
            "Could not load a repo from any of ${errors.size} addresses tried:\n" +
                errors.joinToString("\n") { "• ${it.message ?: it::class.java.simpleName}" } +
                if (errors.all { it.message.orEmpty().startsWith("HTTP 404") }) {
                    "\nThe repo may have moved or been removed; check the repo's own page for its current index.min.json link."
                } else ""
        }
        Result.failure(IllegalStateException(message, errors.lastOrNull()))
    }

    private fun fetchAndClassify(url: String): JsonUrlResult = classify(url, json.parseToJsonElement(fetchBody(url)))

    private fun fetchBody(url: String): String {
        val request = Request.Builder().url(url).build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} from $url")
            val bytes = response.body.bytes()
            // Some hosts (and `.gz` index files) hand back gzip bytes the HTTP layer did not unpack: reading
            // those as text gives "Unexpected JSON token ... �". Unpack when the gzip magic number is there.
            if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
            } else {
                bytes.toString(Charsets.UTF_8)
            }
        }.removePrefix("\uFEFF")
        return body
    }

    private fun classify(url: String, root: JsonElement): JsonUrlResult {
        if (root is JsonArray) return JsonUrlResult.Repo(url, parseEntries(root))
        val obj = root as? JsonObject ?: error("That URL does not return a JSON extension repo")
        // A CloudStream repo.json only points at its plugin lists; each list is a plugins.json array.
        (obj["pluginLists"] as? JsonArray)?.let { lists ->
            val entries = lists.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.flatMap { listUrl ->
                runCatching { parseEntries(json.parseToJsonElement(fetchBody(listUrl)).jsonArray) }.getOrDefault(emptyList())
            }.distinctBy { it.pkg }
            if (entries.isEmpty()) error("That CloudStream repo lists no anime plugins")
            return JsonUrlResult.Repo(url, entries, name = (obj["name"] as? JsonPrimitive)?.contentOrNull)
        }
        for (key in listOf("extensions", "sources", "plugins", "items", "data")) {
            val arr = obj[key]?.let { runCatching { it.jsonArray }.getOrNull() } ?: continue
            return JsonUrlResult.Repo(url, parseEntries(arr))
        }
        error("Unrecognised JSON: expected an extension repo (a list of extensions)")
    }

    private fun parseEntries(array: JsonArray): List<ExtensionRepoEntry> {
        var skippedCloudStream = false
        val entries = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            if (obj.containsKey("internalName")) {
                // CloudStream plugins.json row; null for a plugin that is down or not for anime.
                return@mapNotNull parsePlugin(obj).also { if (it == null) skippedCloudStream = true }
            }
            runCatching { json.decodeFromJsonElement(ExtensionRepoEntry.serializer(), obj) }.getOrNull()
        }
        if (entries.isEmpty() && array.isNotEmpty() && !skippedCloudStream) error("The list is not in a supported extension repo format")
        // 18+ entries are kept here; the Extensions screen hides them unless its NSFW checkbox is on.
        return entries
    }

    /** One row of a CloudStream `plugins.json`; null when the plugin is marked down or serves no anime. */
    private fun parsePlugin(obj: JsonObject): ExtensionRepoEntry? {
        fun text(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
        val url = text("url") ?: return null
        val internalName = text("internalName") ?: text("name") ?: return null
        if ((obj["status"] as? JsonPrimitive)?.intOrNull == 0) return null
        val tvTypes = (obj["tvTypes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        if (tvTypes.isNotEmpty() && tvTypes.none { it in CLOUDSTREAM_ANIME_TYPES || it.equals("NSFW", true) }) return null
        val version = (obj["version"] as? JsonPrimitive)?.intOrNull ?: 0
        return ExtensionRepoEntry(
            name = text("name") ?: internalName,
            pkg = "cs3.$internalName",
            apk = url,
            lang = text("language").orEmpty(),
            version = version.toString(),
            code = version,
            icon = text("iconUrl")?.replace("%size%", "128"),
            cloudstream = true,
            tvTypes = tvTypes,
        )
    }

    /** Base folder an entry's relative `apk` / `icon` paths hang off. */
    fun baseOf(indexUrl: String): String = indexUrl.substringBeforeLast('/')

    /** Keiyoushi repos keep APKs in `/apk` beside the index; flat repos keep them next to it. */
    fun apkUrls(indexUrl: String, entry: ExtensionRepoEntry): List<String> {
        if (entry.cloudstream || entry.apk.startsWith("http")) return listOf(entry.apk)
        val base = baseOf(indexUrl)
        return listOf("$base/apk/${entry.apk}", "$base/${entry.apk}")
    }

    fun iconUrl(indexUrl: String, entry: ExtensionRepoEntry): String? {
        val icon = entry.icon
        if (entry.cloudstream) return icon
        if (icon != null) return if (icon.startsWith("http")) icon else "${baseOf(indexUrl)}/$icon"
        return "${baseOf(indexUrl)}/icon/${entry.pkg}.png"
    }

    /**
     * Downloads the extension APK into the app's cache and returns the file. The caller hands it to
     * `ExtensionManager.installPrivate`, which stores it inside Ansu; nothing is installed on the phone.
     */
    suspend fun downloadApk(indexUrl: String, entry: ExtensionRepoEntry): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val apkFile = File(context.cacheDir, "${entry.pkg}-${entry.version}." + if (entry.cloudstream) "cs3" else "apk")
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
        val CLOUDSTREAM_ANIME_TYPES = setOf("Anime", "AnimeMovie", "OVA")
        const val KEY_REPOS = "repo_urls"
        const val KEY_REPO_NAMES = "repo_names"
        const val KEY_DISABLED_REPOS = "disabled_repo_urls"
    }
}
