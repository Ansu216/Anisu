package eu.kanade.tachiyomi.animesource.model

import android.net.Uri
import okhttp3.Headers

/** A sub/dub track. */
data class Track(val url: String, val lang: String)

enum class ChapterType { Opening, Ending, Recap, MixedOp, Other }

/** A chapter-like marker inside a video (intro, outro, ...). */
data class TimeStamp(
    val start: Double,
    val end: Double,
    val name: String,
    val type: ChapterType = ChapterType.Other,
)

/**
 * Extension API library 16 `Video` (a data class), plus the library 12-15 constructors, `url`, `quality`
 * and the mutable `videoUrl` so that older extensions keep linking.
 */
data class Video(
    var videoUrl: String = "",
    val videoTitle: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: Headers? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),
    val mpvArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
) {

    /** Library 12-15: the page the video is on (used to resolve [videoUrl] later). */
    @Transient
    var url: String = ""

    /** Library 12-15 name for [videoTitle]. */
    val quality: String get() = videoTitle

    // ---- library 12-15 constructors ----

    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        headers: Headers? = null,
        subtitleTracks: List<Track> = emptyList(),
        audioTracks: List<Track> = emptyList(),
    ) : this(
        videoUrl = videoUrl.orEmpty(),
        videoTitle = quality,
        headers = headers,
        subtitleTracks = subtitleTracks,
        audioTracks = audioTracks,
    ) {
        this.url = url
    }

    @Suppress("UNUSED_PARAMETER")
    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        uri: Uri?,
        headers: Headers? = null,
    ) : this(url, quality, videoUrl, headers, emptyList(), emptyList())

    @Transient
    @Volatile
    var progress: Int = 0

    @Transient
    @Volatile
    var status: State = State.QUEUE

    enum class State { QUEUE, LOAD_VIDEO, READY, ERROR }
}
