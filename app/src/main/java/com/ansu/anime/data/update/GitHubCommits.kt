package com.ansu.anime.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One commit as the Commits screen shows it: the subject, who wrote it, when, and where to read it. */
data class RepoCommit(
    val sha: String,
    val message: String,
    val authorName: String,
    val avatarUrl: String?,
    val date: String,
    val htmlUrl: String,
) {
    /** The seven characters every Git host shows next to a commit. */
    val shortSha: String get() = sha.take(7)
}

/**
 * The repository's commit list from the public GitHub API, newest first — the same source
 * vivi-music's `CommitScreen` reads, and the same host [GitHubStats] already talks to.
 *
 * The answer is kept for the app run: opening Commits again costs no request, and the
 * unauthenticated rate limit (60 requests an hour) is spent sparingly. A manual refresh passes
 * [load]'s `force`, so "Refresh" always sees what CI has just pushed.
 */
class GitHubCommits(private val client: OkHttpClient) {

    private var cached: List<RepoCommit>? = null

    suspend fun load(force: Boolean = false): Result<List<RepoCommit>> = withContext(Dispatchers.IO) {
        if (!force) cached?.let { return@withContext Result.success(it) }
        runCatching {
            val request = Request.Builder()
                .url(COMMITS_URL)
                // The GitHub API answers 403 without a User-Agent, so always send one.
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub returned HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            json.decodeFromString<List<CommitDto>>(body)
                .mapNotNull { dto ->
                    val info = dto.commit ?: return@mapNotNull null
                    val subject = info.message.lineSequence().firstOrNull { it.isNotBlank() } ?: info.message
                    RepoCommit(
                        sha = dto.sha,
                        message = subject.trim(),
                        authorName = dto.author?.login?.takeIf { it.isNotBlank() }
                            ?: info.author?.name
                            ?: "Unknown",
                        avatarUrl = dto.author?.avatarUrl,
                        date = formatDate(info.author?.date),
                        htmlUrl = dto.htmlUrl,
                    )
                }
                .also { cached = it }
        }
    }

    /** `2026-11-12T21:48:34Z` becomes `12 Nov 2026`; anything unreadable is kept as it came. */
    private fun formatDate(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return runCatching {
            OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        }.getOrDefault(raw.take(10))
    }

    companion object {
        private const val COMMITS_URL =
            "https://api.github.com/repos/Ansu216/Anisu/commits?branch=main&per_page=50"
        private const val USER_AGENT = "Ansu-Android"

        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class CommitDto(
    val sha: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val commit: CommitInfoDto? = null,
    val author: CommitUserDto? = null,
)

@Serializable
private data class CommitInfoDto(
    val message: String = "",
    val author: CommitAuthorDto? = null,
)

@Serializable
private data class CommitAuthorDto(
    val name: String? = null,
    val date: String? = null,
)

@Serializable
private data class CommitUserDto(
    val login: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
