package com.ansu.anime.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads what this project publishes on GitHub and reports whether it is newer
 * than the running build. One class covers both channels, because they match
 * the two workflows exactly: tagged GitHub Releases, and the `apk-nightly`
 * branch that also carries a machine-readable `nightly.json`.
 */
class UpdateChecker(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(
        channel: UpdateChannel,
        installedVersionName: String,
        installedVersionCode: Int,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            when (channel) {
                UpdateChannel.RELEASES -> checkReleases(installedVersionName)
                UpdateChannel.NIGHTLY -> checkNightly(installedVersionCode)
            }
        }.getOrElse { error -> UpdateCheckResult.Failed(describe(error, channel)) }
    }

    /** Turns transport errors into something a person can act on. */
    private fun describe(error: Throwable, channel: UpdateChannel): String = when {
        error is HttpStatusException && error.code == 404 && channel == UpdateChannel.RELEASES ->
            "No releases published yet. Switch to the Nightly channel to get the latest build."
        error is HttpStatusException && error.code == 404 ->
            "Nothing published on the nightly branch yet."
        else -> error.message ?: error::class.simpleName ?: "Update check failed"
    }

    private fun checkReleases(installedVersionName: String): UpdateCheckResult {
        val release = latestRelease()
        val latest = release.tagName.removePrefix("v").trim()
        if (latest.isBlank()) return UpdateCheckResult.Failed("The latest release has no version tag")
        if (!isNewerVersion(latest, installedVersionName)) return UpdateCheckResult.UpToDate(latest)

        val apk = pickReleaseApk(release, latest)
            ?: return UpdateCheckResult.Failed("Release $latest has no APK attached")

        return UpdateCheckResult.Available(
            AvailableUpdate(
                channel = UpdateChannel.RELEASES,
                versionName = latest,
                notes = release.body?.trim()?.takeIf { it.isNotEmpty() },
                apkUrl = apk.downloadUrl,
                pageUrl = release.htmlUrl.ifBlank { "$REPO_URL/releases" },
                publishedAt = release.publishedAt,
            ),
        )
    }

    /**
     * The newest published release. `releases/latest` deliberately ignores pre-releases, so a
     * repository that has only ever published pre-releases answers 404 there and the Stable channel
     * used to report "no releases published yet" while the builds were sitting right there. In that
     * case the full list is read and its newest non-draft entry is taken instead.
     */
    private fun latestRelease(): GitHubRelease {
        runCatching { json.decodeFromString<GitHubRelease>(get(RELEASES_LATEST_URL)) }
            .getOrNull()
            ?.let { return it }

        val all = json.decodeFromString<List<GitHubRelease>>(get(RELEASES_LIST_URL))
        return all.firstOrNull { !it.draft }
            ?: throw HttpStatusException(404, "No release has been published yet")
    }

    /**
     * The **release** APK of a release, never the debug one. Both workflows attach a minified
     * `Ansu-<version>.apk` next to a `-debug` build, and GitHub lists the two alphabetically, which
     * puts the debug asset first. The exact release name is therefore preferred, and any asset whose
     * name mentions "debug" is refused outright instead of falling back to it.
     */
    private fun pickReleaseApk(release: GitHubRelease, version: String): GitHubAsset? {
        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val expected = "Ansu-$version.apk"
        return apks.firstOrNull { it.name.equals(expected, ignoreCase = true) }
            ?: apks.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
    }

    /**
     * The headline of a commit, read from GitHub so the Updates screen can show *what* a build
     * contains and not just its short SHA. Best-effort on purpose: if the lookup fails the SHA is
     * shown on its own, because a nice-to-have must never block an update.
     */
    private fun commitMessage(sha: String): String? =
        runCatching { json.decodeFromString<GitHubCommit>(get("$COMMITS_URL/$sha")) }
            .getOrNull()
            ?.commit
            ?.message
            ?.substringBefore('\n')
            ?.trim()
            ?.take(120)
            ?.takeIf { it.isNotBlank() }

    private fun checkNightly(installedVersionCode: Int): UpdateCheckResult {
        val manifest = json.decodeFromString<NightlyManifest>(get(NIGHTLY_JSON_URL))
        if (manifest.versionCode <= 0) return UpdateCheckResult.Failed("The nightly manifest is empty")
        if (manifest.versionCode <= installedVersionCode) return UpdateCheckResult.UpToDate(manifest.versionName)

        val notes = buildString {
            append("Nightly build from ${manifest.builtAt ?: "an unknown date"}.")
            manifest.commit?.let { sha ->
                append("\nCommit ${sha.take(7)}")
                commitMessage(sha)?.let { append(": $it") }
            }
        }
        // The workflow writes the file name into nightly.json, so renaming the
        // published APK never breaks the updater; old manifests fall back to it.
        val apkName = manifest.apk?.takeIf { it.isNotBlank() } ?: DEFAULT_NIGHTLY_APK

        return UpdateCheckResult.Available(
            AvailableUpdate(
                channel = UpdateChannel.NIGHTLY,
                versionName = manifest.versionName,
                notes = notes,
                apkUrl = "$NIGHTLY_BRANCH_URL/$apkName",
                pageUrl = NIGHTLY_PAGE_URL,
                publishedAt = manifest.builtAt,
            ),
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            // The GitHub API answers 403 without a User-Agent, so always send one.
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        const val REPO_URL = "https://github.com/Ansu216/Anisu"

        private const val RELEASES_LATEST_URL = "https://api.github.com/repos/Ansu216/Anisu/releases/latest"
        private const val RELEASES_LIST_URL = "https://api.github.com/repos/Ansu216/Anisu/releases"
        private const val COMMITS_URL = "https://api.github.com/repos/Ansu216/Anisu/commits"
        private const val NIGHTLY_PAGE_URL = "$REPO_URL/tree/apk-nightly"
        private const val NIGHTLY_BRANCH_URL =
            "https://raw.githubusercontent.com/Ansu216/Anisu/apk-nightly"
        private const val NIGHTLY_JSON_URL = "$NIGHTLY_BRANCH_URL/nightly.json"
        private const val DEFAULT_NIGHTLY_APK = "Ansu-nightly.apk"
        private const val USER_AGENT = "Ansu-Android-Updater"
    }
}

/** A non-2xx answer from GitHub, kept typed so [UpdateChecker] can explain a 404 per channel. */
private class HttpStatusException(val code: Int, message: String = "GitHub returned HTTP $code") :
    Exception(message)

/**
 * Numeric component comparison ("1.10.0" is newer than "1.9.0"): non-numeric
 * parts are ignored and missing parts count as zero, so a nightly versionName
 * like "2026.09.28.1500" never looks like an upgrade over a real release.
 */
internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    val a = candidate.split('.', '-', '+', '_').mapNotNull { it.toIntOrNull() }
    val b = installed.split('.', '-', '+', '_').mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(i) { 0 }
        val right = b.getOrElse(i) { 0 }
        if (left != right) return left > right
    }
    return false
}
