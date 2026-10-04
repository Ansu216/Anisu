package eu.kanade.tachiyomi.animesource.model

import android.net.Uri
import kotlinx.serialization.json.JsonObject
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
 * Extension API library 17 `Video` (a data class), plus the library 12-15 constructors, `url`, `quality`
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
    val memo: JsonObject = EmptyMemo,
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
        videoUrl = videoUrl ?: "null",
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

    // ---- library 16 constructor and copy (no `memo`), kept so extensions built for 16 still link ----

    @Deprecated("Used only for compatibility with ext lib 16, do not use", level = DeprecationLevel.HIDDEN)
    constructor(
        videoUrl: String = "",
        videoTitle: String = "",
        resolution: Int? = null,
        bitrate: Int? = null,
        headers: Headers? = null,
        preferred: Boolean = false,
        subtitleTracks: List<Track> = emptyList(),
        audioTracks: List<Track> = emptyList(),
        timestamps: List<TimeStamp> = emptyList(),
        mpvArgs: List<Pair<String, String>> = emptyList(),
        ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
        ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
        internalData: String = "",
        initialized: Boolean = false,
    ) : this(
        videoUrl, videoTitle, resolution, bitrate, headers, preferred, subtitleTracks, audioTracks, timestamps,
        mpvArgs, ffmpegStreamArgs, ffmpegVideoArgs, internalData, initialized, EmptyMemo,
    )

    @Deprecated("Used only for compatibility with ext lib 16, do not use", level = DeprecationLevel.HIDDEN)
    fun copy(
        videoUrl: String = this.videoUrl,
        videoTitle: String = this.videoTitle,
        resolution: Int? = this.resolution,
        bitrate: Int? = this.bitrate,
        headers: Headers? = this.headers,
        preferred: Boolean = this.preferred,
        subtitleTracks: List<Track> = this.subtitleTracks,
        audioTracks: List<Track> = this.audioTracks,
        timestamps: List<TimeStamp> = this.timestamps,
        mpvArgs: List<Pair<String, String>> = this.mpvArgs,
        ffmpegStreamArgs: List<Pair<String, String>> = this.ffmpegStreamArgs,
        ffmpegVideoArgs: List<Pair<String, String>> = this.ffmpegVideoArgs,
        internalData: String = this.internalData,
        initialized: Boolean = this.initialized,
    ): Video = Video(
        videoUrl = videoUrl,
        videoTitle = videoTitle,
        resolution = resolution,
        bitrate = bitrate,
        headers = headers,
        preferred = preferred,
        subtitleTracks = subtitleTracks,
        audioTracks = audioTracks,
        timestamps = timestamps,
        mpvArgs = mpvArgs,
        ffmpegStreamArgs = ffmpegStreamArgs,
        ffmpegVideoArgs = ffmpegVideoArgs,
        internalData = internalData,
        initialized = initialized,
        memo = memo,
    ).also { it.url = url }

    /** True when the video, an audio track or a subtitle points at the `http://localhost:1` placeholder. */
    fun usesHttpServer(): Boolean =
        LOCAL_URL.find(videoUrl) != null ||
            audioTracks.any { LOCAL_URL.find(it.url) != null } ||
            subtitleTracks.any { LOCAL_URL.find(it.url) != null }

    /** The same video with the placeholder swapped for the real local server on [port]. */
    fun copyHttpServer(port: Int): Video {
        val host = "http://localhost:$port"
        return copy(
            videoUrl = LOCAL_URL.replace(videoUrl, host),
            subtitleTracks = subtitleTracks.map { it.copy(url = LOCAL_URL.replace(it.url, host)) },
            audioTracks = audioTracks.map { it.copy(url = LOCAL_URL.replace(it.url, host)) },
        ).also { it.url = url }
    }

    @Transient
    @Volatile
    var progress: Int = 0

    @Transient
    @Volatile
    var status: State = State.QUEUE

    enum class State { QUEUE, LOAD_VIDEO, READY, ERROR }

    private companion object {
        val LOCAL_URL = Regex("""http://localhost:1(?!\d)""")
    }
}
