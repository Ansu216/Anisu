package com.ansu.anime.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** The two numbers the About screen shows for the project: APK downloads and repository stars. */
data class RepoStats(val downloads: Long, val stars: Long)

/**
 * Reads the project's counters from the public GitHub API, the same host [UpdateChecker] already
 * talks to, so nothing new has to be configured. Best-effort on purpose: a failure leaves the About
 * screen without numbers instead of raising an error, because a counter is a nice-to-have. The
 * answer is kept for the life of the app, so opening About again costs no request and the
 * unauthenticated rate limit is spent sparingly.
 */
class GitHubStats(private val client: OkHttpClient) {

    private var cached: RepoStats? = null

    suspend fun load(): RepoStats? {
        cached?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val repo = json.decodeFromString<RepoResponse>(get(REPO_URL))
                // Every APK of every published release counts: the sum is what "downloads" means.
                val releases = json.decodeFromString<List<ReleaseResponse>>(get(RELEASES_URL))
                val downloads = releases.sumOf { release ->
                    release.assets.sumOf { asset -> asset.downloadCount.toLong() }
                }
                RepoStats(downloads = downloads, stars = repo.stars)
            }.getOrNull()?.also { cached = it }
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            // The GitHub API answers 403 without a User-Agent, so always send one.
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub returned HTTP ${response.code}")
            return response.body.string()
        }
    }

    companion object {
        private const val REPO_URL = "https://api.github.com/repos/Ansu216/Anisu"
        private const val RELEASES_URL = "$REPO_URL/releases?per_page=100"
        private const val USER_AGENT = "Ansu-Android"

        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class RepoResponse(
    @SerialName("stargazers_count") val stars: Long = 0,
)

@Serializable
private data class ReleaseResponse(
    val assets: List<AssetResponse> = emptyList(),
)

@Serializable
private data class AssetResponse(
    @SerialName("download_count") val downloadCount: Int = 0,
)
