package com.ansu.anime.data.contributors

import android.content.Context
import com.ansu.anime.core.net.ApiErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** One person credited on the Contributors screen, as declared in `contributorsansu.json`. */
@Serializable
data class Contributor(
    val username: String,
    val name: String? = null,
    val description: String? = null,
    val url: String? = null,
) {
    /** "Pietro Bonaldo (@PiBOH)" when a real name is known, "@PiBOH" otherwise. */
    val displayName: String get() = name?.takeIf { it.isNotBlank() }?.let { "$it (@$username)" } ?: "@$username"

    val profileUrl: String get() = url?.takeIf { it.isNotBlank() } ?: "https://github.com/$username"

    val avatarUrl: String get() = "https://github.com/$username.png?size=200"
}

@Serializable
private data class ContributorsFile(
    val contributors: List<Contributor> = emptyList(),
)

/**
 * Reads `contributorsansu.json` live from the project's repository on GitHub (the same way the desktop
 * edition credits its contributors), so a name can be added without shipping a new APK. The copy
 * bundled in `assets/` is used whenever the request fails — offline, rate-limited or repository moved —
 * so the screen always has something to show. A successful answer is cached for the session.
 */
class ContributorsRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val errors: ApiErrorHandler,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: List<Contributor>? = null

    suspend fun load(force: Boolean = false): List<Contributor> {
        if (!force) cached?.let { return it }
        val live = runCatching { fetchRemote() }.getOrElse { error ->
            errors.report("Loading contributors", error, quiet = true)
            emptyList()
        }
        val result = live.ifEmpty { bundled() }
        if (result.isNotEmpty()) cached = result
        return result
    }

    private suspend fun fetchRemote(): List<Contributor> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(REMOTE_URL)
            .header("User-Agent", "Ansu-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Contributors: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            json.decodeFromString<ContributorsFile>(body).contributors
        }
    }

    private suspend fun bundled(): List<Contributor> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.assets.open("contributorsansu.json").bufferedReader().use { it.readText() }
            json.decodeFromString<ContributorsFile>(text).contributors
        }.getOrDefault(emptyList())
    }

    companion object {
        /** The official Ansu repository, where `contributorsansu.json` lives at the root of `main`. */
        const val REMOTE_URL = "https://raw.githubusercontent.com/Ansu216/Anisu/main/contributorsansu.json"
    }
}
