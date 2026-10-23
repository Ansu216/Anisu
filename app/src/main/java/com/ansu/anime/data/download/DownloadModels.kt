package com.ansu.anime.data.download

import kotlinx.serialization.Serializable

/** One subtitle file saved next to a download; [path] is an absolute file path in the app's private storage. */
@Serializable
data class DownloadedSubtitle(val lang: String, val path: String)

/** What the app remembers about a downloaded (or downloading) episode, so the Downloads screen can list it offline. */
@Serializable
data class DownloadEntry(
    val id: String,
    val anilistId: Int,
    val animeTitle: String,
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val episodeId: String,
    val episodeName: String,
    val episodeNumber: Float,
    val sourceId: Long? = null,
    /** The stream that was downloaded, e.g. "Anikoto · HD-1 · 1080p". */
    val sourceLabel: String = "",
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<DownloadedSubtitle> = emptyList(),
    val createdAt: Long = 0L,
)

enum class DownloadPhase { Preparing, Queued, Downloading, Completed, Failed }

/** Where one download stands. [percent] is 0..100, or -1 while the size is not known yet. */
data class DownloadStatus(
    val phase: DownloadPhase,
    val percent: Float = 0f,
    val bytes: Long = 0L,
    val message: String? = null,
    /** 1-based place in the waiting queue (the order downloads will start in); 0 when it is not waiting there. */
    val queuePosition: Int = 0,
    /** True for a failed download that the Retry button can start again. */
    val retryable: Boolean = false,
)

/** Why the whole download queue is on hold, if it is. */
enum class PauseState { None, User, Battery }

data class DownloadItem(val entry: DownloadEntry, val status: DownloadStatus)

/** The id a download is stored under: one per show and episode number. */
fun downloadKey(anilistId: Int, episodeNumber: Float): String = "${anilistId}_$episodeNumber"
