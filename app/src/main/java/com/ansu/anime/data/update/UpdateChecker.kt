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
        val release = json.decodeFromString<GitHubRelease>(get(RELEASES_LATEST_URL))
        val latest = release.tagName.removePrefix("v").trim()
        if (latest.isBlank()) return UpdateCheckResult.Failed("The latest release has no version tag")
        if (!isNewerVersion(latest, installedVersionName)) return UpdateCheckResult.UpToDate(latest)

        // Both workflows publish a minified APK and a -debug one; always prefer the release build.
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("debug", ignoreCase = true) }
            ?: release.assets.firstOrNull { it.name.endsWith(".apk") }
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

    private fun checkNightly(installedVersionCode: Int): UpdateCheckResult {
        val manifest = json.decodeFromString<NightlyManifest>(get(NIGHTLY_JSON_URL))
        if (manifest.versionCode <= 0) return UpdateCheckResult.Failed("The nightly manifest is empty")
        if (manifest.versionCode <= installedVersionCode) return UpdateCheckResult.UpToDate(manifest.versionName)

        val notes = buildString {
            append("Nightly build from ${manifest.builtAt ?: "an unknown date"}.")
            manifest.commit?.take(7)?.let { append("\nCommit $it.") }
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
        private const val NIGHTLY_PAGE_URL = "$REPO_URL/tree/apk-nightly"
        private const val NIGHTLY_BRANCH_URL =
            "https://raw.githubusercontent.com/Ansu216/Anisu/apk-nightly"
        private const val NIGHTLY_JSON_URL = "$NIGHTLY_BRANCH_URL/nightly.json"
        private const val DEFAULT_NIGHTLY_APK = "Ansu-nightly.apk"
        private const val USER_AGENT = "Ansu-Android-Updater"
    }
}

/** A non-2xx answer from GitHub, kept typed so [UpdateChecker] can explain a 404 per channel. */
private class HttpStatusException(val code: Int) : Exception("GitHub returned HTTP $code")

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
