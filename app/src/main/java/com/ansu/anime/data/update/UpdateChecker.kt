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
                notes = releaseNotes(release),
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
     * The release body with the message of the commit it was built from appended. The body already
     * lists the commits since the previous release, but the build itself is only a bare SHA on its
     * "built automatically from commit `…`" line — the same lookup the nightly channel uses puts a
     * readable headline next to it.
     */
    private fun releaseNotes(release: GitHubRelease): String? {
        val body = release.body?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val sha = SHA_IN_RELEASE_BODY.find(body)?.groupValues?.get(1) ?: return body
        val message = commitMessage(sha) ?: return body
        return "$body\n\nCommit ${sha.take(7)}: $message"
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

        /** `release-apk.yml` writes "built automatically from commit `<sha>`" into the body. */
        private val SHA_IN_RELEASE_BODY = Regex("commit `([0-9a-fA-F]{7,40})`")
    }
}

/** A non-2xx answer from GitHub, kept typed so [UpdateChecker] can explain a 404 per channel. */
private class HttpStatusException(val code: Int, message: String = "GitHub returned HTTP $code") :
    Exception(message)

/**
 * Whether [candidate] is a newer version than [installed], the way SemVer orders them.
 *
 * The release part is compared component by component ("1.10.0" is newer than "1.9.0",
 * and a missing component counts as zero, so a timestamp-style name such as
 * "2026.09.28.1500" compares sensibly against "0.1.0"). Build metadata after `+` is
 * ignored, as the specification says.
 *
 * The pre-release part decides when the release parts are equal, and that is exactly what
 * used to go wrong: the old comparison kept only the numeric components, so "0.1.0-nightly"
 * collapsed to 0.1.0 and the app answered "up to date" on a pre-release that the released
 * 0.1.0 had already superseded — an old build that was never offered the new one. Now a
 * version carrying a pre-release part is always older than the same version without one,
 * and two pre-releases are ordered by their dot-separated identifiers (numeric ones
 * numerically, and a numeric identifier ranks below an alphanumeric one).
 */
internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    val (candidateNumbers, candidatePre) = splitVersion(candidate)
    val (installedNumbers, installedPre) = splitVersion(installed)

    val byNumbers = compareComponents(candidateNumbers, installedNumbers)
    if (byNumbers != 0) return byNumbers > 0

    return when {
        candidatePre == null && installedPre == null -> false
        // 1.0.0 supersedes its own pre-releases (1.0.0-rc.1 and the like).
        candidatePre == null -> true
        installedPre == null -> false
        else -> comparePreRelease(candidatePre, installedPre) > 0
    }
}

/** Splits "v1.2.3-rc.1+build.5" into [1, 2, 3] and "rc.1"; build metadata is dropped. */
private fun splitVersion(version: String): Pair<List<Int>, String?> {
    val withoutMetadata = version.trim().removePrefix("v").substringBefore('+')
    val separator = listOf(withoutMetadata.indexOf('-'), withoutMetadata.indexOf('_'))
        .filter { it >= 0 }
        .minOrNull()

    val release = if (separator == null) withoutMetadata else withoutMetadata.substring(0, separator)
    val preRelease = if (separator == null) null else withoutMetadata.substring(separator + 1)
    return release.split('.').mapNotNull { it.toIntOrNull() } to preRelease?.takeIf { it.isNotBlank() }
}

/** Component-wise numeric comparison; a missing component counts as zero. */
private fun compareComponents(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(i) { 0 }
        val right = b.getOrElse(i) { 0 }
        if (left != right) return left.compareTo(right)
    }
    return 0
}

/** SemVer pre-release ordering: "rc.1" is older than "rc.2", and "rc" older than "rc.1". */
private fun comparePreRelease(a: String, b: String): Int {
    val left = a.split('.')
    val right = b.split('.')
    for (i in 0 until maxOf(left.size, right.size)) {
        // Fewer identifiers means the lower version: 1.0.0-rc < 1.0.0-rc.1.
        if (i >= left.size) return -1
        if (i >= right.size) return 1

        val leftNumber = left[i].toIntOrNull()
        val rightNumber = right[i].toIntOrNull()
        val result = when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            // A numeric identifier always ranks below an alphanumeric one.
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left[i].compareTo(right[i])
        }
        if (result != 0) return result
    }
    return 0
}
