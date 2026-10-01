package com.ansu.anime.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which published build the updater follows. */
enum class UpdateChannel {
    /** Tagged GitHub Releases (`v1.2.3`) — the stable channel. */
    RELEASES,

    /** The hourly `apk-nightly` branch, rebuilt on every push to `main`. */
    NIGHTLY,
}

/** A published build that is newer than the one installed. */
data class AvailableUpdate(
    val channel: UpdateChannel,
    val versionName: String,
    val notes: String?,
    val apkUrl: String,
    val pageUrl: String,
    val publishedAt: String?,
)

/** Outcome of a check, so the UI can tell "up to date" from "failed". */
sealed interface UpdateCheckResult {
    data class UpToDate(val latestVersionName: String) : UpdateCheckResult
    data class Available(val update: AvailableUpdate) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

/** Live state of the updater, exposed to the About screen. */
data class UpdateState(
    val isChecking: Boolean = false,
    val result: UpdateCheckResult? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val error: String? = null,
)

/** `nightly.json`, published on the `apk-nightly` branch by the nightly workflow. */
@Serializable
internal data class NightlyManifest(
    val app: String? = null,
    val versionName: String = "",
    val versionCode: Int = 0,
    val builtAt: String? = null,
    val commit: String? = null,
    /** File name of the published APK on the branch; absent in manifests built before this field existed. */
    val apk: String? = null,
)

/** The subset of the GitHub Releases API this app needs. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
    /** Draft releases are never offered; they are not published yet. */
    val draft: Boolean = false,
)

/** One entry of the GitHub commits API; only the message line is shown to the user. */
@Serializable
internal data class GitHubCommit(
    @SerialName("commit") val commit: CommitDetails = CommitDetails(),
)

@Serializable
internal data class CommitDetails(
    val message: String = "",
)

@Serializable
internal data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
)
