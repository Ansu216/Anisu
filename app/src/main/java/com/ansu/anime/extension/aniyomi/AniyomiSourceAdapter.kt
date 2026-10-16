package com.ansu.anime.extension.aniyomi

import com.ansu.anime.core.model.AnimeFilterList as AnsuFilterList
import com.ansu.anime.core.model.AnimesPage as AnsuPage
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime as AnsuAnime
import com.ansu.anime.core.model.SEpisode as AnsuEpisode
import com.ansu.anime.core.model.SubtitleTrack
import com.ansu.anime.core.model.Video as AnsuVideo
import com.ansu.anime.extension.api.AnimeCatalogueSource as AnsuSource
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.preferenceKey
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Presents one source loaded from an Aniyomi/Keiyoushi extension through Ansu's own
 * [AnsuSource] contract, so Home, Search, Details and the player treat it like any other.
 * Extension code does blocking network I/O, so every call is moved to [Dispatchers.IO].
 */
class AniyomiSourceAdapter(
    private val source: AnimeCatalogueSource,
    /** The extension API the source was built for; library 17 sources answer the combined update calls. */
    private val libVersion: Double = AniyomiRuntime.MIN_LIB_VERSION,
) : AnsuSource {

    /**
     * Runs extension code on [Dispatchers.IO]. An extension built for a different API version can raise
     * `Error`s (NoSuchMethodError, AbstractMethodError, NoClassDefFoundError...) which nothing in the app
     * catches and which close the app. They are turned into a normal exception carrying the real cause.
     */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("AniyomiSource", "${source.name}: extension raised ${e::class.java.name}", e)
            throw ExtensionApiError("${e::class.java.simpleName}: ${e.message ?: "extension is not compatible with Anisu"}", e)
        }
    }


    /**
     * The episodes exactly as the source returned them. Aniyomi stores the source's own episode and hands it back
     * when it asks for hosters, so a source can read the `scanlator`, `memo` or `episode_number` it set itself.
     */
    private val episodeCache = java.util.concurrent.ConcurrentHashMap<String, SEpisode>()

    /** True when the extension declares settings (Aniyomi's `ConfigurableAnimeSource`). */
    val hasSettings: Boolean get() = source is ConfigurableAnimeSource

    /** The SharedPreferences file name the extension's settings are stored in, or null without settings. */
    val settingsKey: String? get() = (source as? ConfigurableAnimeSource)?.preferenceKey()

    /** Lets the extension fill [screen] with its settings; returns false when it has none. */
    fun setupSettings(screen: androidx.preference.PreferenceScreen): Boolean {
        val configurable = source as? ConfigurableAnimeSource ?: return false
        configurable.setupPreferenceScreen(screen)
        return true
    }

    override val id: Long get() = source.id
    override val name: String get() = source.name
    override val lang: String get() = source.lang
    override val baseUrl: String get() = (source as? AnimeHttpSource)?.baseUrl.orEmpty()
    override val supportsLatest: Boolean get() = source.supportsLatest

    override suspend fun getPopularAnime(page: Int): AnsuPage = io {
        source.getPopularAnime(page).toAnsu()
    }

    override suspend fun getLatestUpdates(page: Int): AnsuPage = io {
        source.getLatestUpdates(page).toAnsu()
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnsuFilterList): AnsuPage =
        io {
            // Ansu's own filter model cannot describe a source's filters, so a search uses the source's defaults.
            source.getSearchAnime(page, query, source.getFilterList()).toAnsu()
        }

    override suspend fun getAnimeDetails(anime: AnsuAnime): AnsuAnime = io {
        val details = if (libVersion >= LIB_17) {
            source.getAnimeEpisodeUpdate(anime.toSource(), emptyList(), fetchDetails = true, fetchEpisodes = false).anime
        } else {
            source.getAnimeDetails(anime.toSource())
        }
        anime.copy(
            title = anime.title,
            posterUrl = details.thumbnail_url ?: anime.posterUrl,
            description = details.description ?: anime.description,
            genres = details.getGenres() ?: anime.genres,
        )
    }

    override suspend fun getEpisodeList(anime: AnsuAnime): List<AnsuEpisode> = io {
        val episodes = if (libVersion >= LIB_17) {
            source.getAnimeEpisodeUpdate(anime.toSource(), emptyList(), fetchDetails = false, fetchEpisodes = true).episodes
        } else {
            source.getEpisodeList(anime.toSource())
        }
        episodes.forEach { episodeCache[it.url] = it }
        // Sources list newest first. With real episode numbers sort by them; otherwise number from the bottom up.
        val useNumbers = episodes.isNotEmpty() && episodes.all { it.episode_number > 0f }
        val ordered = if (useNumbers) episodes.sortedBy { it.episode_number } else episodes.reversed()
        ordered.mapIndexed { index, episode ->
            AnsuEpisode(
                id = episode.url,
                name = episode.name,
                episodeNumber = if (useNumbers) episode.episode_number else (index + 1).toFloat(),
                thumbnailUrl = episode.preview_url,
                dateUpload = episode.date_upload,
                description = episode.summary,
                sourceId = source.id,
            )
        }
    }

    override suspend fun getVideoList(episode: AnsuEpisode): List<AnsuVideo> {
        val all = mutableListOf<Pair<Boolean, AnsuVideo>>()
        streamPairs(episode, log = {}) { batch -> synchronized(all) { all += batch } }
        // The source's own "preferred" videos go first (Aniyomi plays the first preferred one), then source order.
        val listed = synchronized(all) { all.toList() }.sortedByDescending { it.first }.map { it.second }
        // This call promises playable videos, so the ones that were listed for lazy resolution are resolved here.
        return listed.mapNotNull { video ->
            val pending = video.resolve ?: return@mapNotNull video
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { pending() }?.takeIf { it.url.isNotBlank() }
        }
    }

    override suspend fun streamVideos(
        episode: AnsuEpisode,
        log: (String) -> Unit,
        onVideos: suspend (List<AnsuVideo>) -> Unit,
    ) {
        streamPairs(episode, log) { batch ->
            // Preferred videos first inside each batch; batches arrive in the order the servers answer.
            onVideos(batch.sortedByDescending { it.first }.map { it.second })
        }
    }

    /**
     * Asks every hoster at once and reports each one's videos the moment they are listed. Like Aniyomi, listing
     * is cheap and a video the source has not resolved yet is only resolved when it is picked, so one slow or
     * broken video never costs the hoster (or the other hosters) their videos.
     */
    private suspend fun streamPairs(
        episode: AnsuEpisode,
        log: (String) -> Unit,
        onBatch: suspend (List<Pair<Boolean, AnsuVideo>>) -> Unit,
    ) = io {
        val hosters = loadHosters(sourceEpisodeFor(episode))
        log("  $name: ${hosters.size} hoster(s): " + hosters.joinToString { it.hosterName.ifBlank { "?" } }.take(200))
        val gate = Semaphore(HOSTER_PARALLELISM)
        coroutineScope {
            // Aniyomi only loads a lazy hoster once it is picked; here those go last so they never delay the rest.
            hosters.sortedBy { it.lazy }.map { hoster ->
                async {
                    val label = hoster.hosterName.ifBlank { "?" }
                    gate.withPermit {
                        try {
                            val found = withTimeoutOrNull(HOSTER_TIMEOUT_MS) {
                                listVideos(hoster, log).mapNotNull { video -> entryFor(hoster, video) }
                            }
                            when {
                                found == null -> log("  $name / $label: timed out after ${HOSTER_TIMEOUT_MS / 1000}s")
                                found.isEmpty() -> log("  $name / $label: no playable video")
                                else -> {
                                    val pending = found.count { it.second.resolve != null }
                                    log("  $name / $label: ${found.size} video(s)" + if (pending > 0) ", $pending resolved when picked" else "")
                                    onBatch(found)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            android.util.Log.w("AniyomiSource", "$name: hoster '$label' failed", e)
                            log("  $name / $label: ${e::class.java.simpleName}: ${e.message}")
                        }
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * The episode as the source itself listed it (Aniyomi hands the source its stored `SEpisode`, with `scanlator`,
     * `memo`, `date_upload`...). It is rebuilt from Ansu's own fields only when it was never listed in this session.
     */
    private fun sourceEpisodeFor(episode: AnsuEpisode): SEpisode {
        val copy = SEpisode.create()
        val original = episodeCache[episode.id]
        if (original != null) {
            copy.copyFrom(original)
        } else {
            copy.url = episode.id
            copy.name = episode.name
            copy.episode_number = episode.episodeNumber
            copy.date_upload = episode.dateUpload
        }
        return copy
    }

    /**
     * Gets the hosters of an episode the way Aniyomi's EpisodeLoader does: a source that really implements
     * hosters is asked for them (then `sortHosters`), every other source is asked for its videos (then
     * `sortVideos`) which are wrapped in one pseudo hoster. If the chosen way is not implemented, the other
     * one is tried before giving up.
     */
    private suspend fun loadHosters(episode: SEpisode): List<Hoster> {
        val http = source as? AnimeHttpSource
        val viaHosters = http == null || implementsHosters(http)
        return try {
            if (viaHosters) fromHosters(episode, http) else fromVideos(episode, http)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnsupportedOperationException) {
            // A message-less UnsupportedOperationException comes from the extension's own stub
            // (e.g. `override fun videoListParse(...) = throw UnsupportedOperationException()`).
            android.util.Log.w("AniyomiSource", "$name: ${if (viaHosters) "hoster" else "video"} list threw UnsupportedOperationException", e)
            try {
                if (viaHosters) fromVideos(episode, http) else fromHosters(episode, http)
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Throwable) {
                android.util.Log.w("AniyomiSource", "$name: the other video list API also failed", e2)
                val frame = e.stackTrace.firstOrNull { !it.className.startsWith("eu.kanade.tachiyomi.animesource") }
                throw ExtensionApiError(
                    "$name: extension cannot list videos for this episode (${e::class.java.simpleName}" +
                        (frame?.let { " at ${it.className.substringAfterLast('.')}.${it.methodName}" } ?: "") + ")",
                    e,
                )
            }
        }
    }

    private suspend fun fromHosters(episode: SEpisode, http: AnimeHttpSource?): List<Hoster> {
        val hosters = source.getHosterList(episode)
        return if (http != null) with(http) { hosters.sortHosters() } else hosters
    }

    private suspend fun fromVideos(episode: SEpisode, http: AnimeHttpSource?): List<Hoster> {
        val videos = source.getVideoList(episode)
        return (if (http != null) with(http) { videos.sortVideos() } else videos).toHosterList()
    }

    /** True when the extension itself declares `getHosterList`, `hosterListRequest` or `hosterListParse`. */
    private fun implementsHosters(http: AnimeHttpSource): Boolean = runCatching {
        var current: Class<*>? = http.javaClass
        var found = false
        while (current != null && !found &&
            current != ParsedAnimeHttpSource::class.java &&
            current != AnimeHttpSource::class.java &&
            current != AnimeSource::class.java
        ) {
            found = current.declaredMethods.any { it.name in HOSTER_METHODS }
            current = current.superclass
        }
        found
    }.getOrDefault(true)

    /**
     * Lists the videos of a hoster the way Aniyomi's `EpisodeLoader.getVideos` does: the videos the hoster already
     * carries (or the source's `getVideoList(hoster)`), a legacy video whose URL is still the string "null" asks the
     * source for it, then `sortVideos`. Nothing is resolved here.
     */
    private suspend fun listVideos(hoster: Hoster, log: (String) -> Unit): List<Video> {
        val http = source as? AnimeHttpSource
        val preset = hoster.videoList
        val videos = when {
            preset != null && http != null -> parseVideoUrls(http, preset, log)
            preset != null -> preset
            http != null -> parseVideoUrls(http, source.getVideoList(hoster), log)
            else -> source.getVideoList(hoster)
        }
        if (videos.isEmpty()) log("  $name: the source returned no videos for this episode (its servers gave nothing or failed inside the extension)")
        return if (http != null) with(http) { videos.sortVideos() } else videos
    }

    /** Library 12-15 sources return videos without a final URL (the string "null"); the source is asked for it. */
    private suspend fun parseVideoUrls(http: AnimeHttpSource, videos: List<Video>, log: (String) -> Unit): List<Video> =
        videos.mapNotNull { video ->
            if (video.videoUrl != "null") {
                video
            } else {
                try {
                    // The generated copy() does not carry the library 12-15 `url`, so it is put back by hand.
                    val link = http.getVideoUrl(video)
                    video.copy(videoUrl = link).also { it.url = video.url }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    android.util.Log.w("AniyomiSource", "$name: getVideoUrl for '${video.videoTitle}' failed", e)
                    // Say why the video was dropped; otherwise the test only shows "no playable video".
                    log("  $name: link for '${video.videoTitle}' failed: ${e::class.java.simpleName}: ${e.message ?: "no message"}")
                    null
                }
            }
        }

    /**
     * What the list shows for one video. A source video that is not `initialized` goes through the source's
     * `resolveVideo` only when it is picked, like Aniyomi (it lists cheaply, then resolves the chosen one); every
     * other video is already final. The hoster name is added to the title for display only, after resolving,
     * because a source may read the title it set itself inside `resolveVideo`.
     */
    private fun entryFor(hoster: Hoster, video: Video): Pair<Boolean, AnsuVideo>? {
        val label = hosterLabel(hoster)
        if (source !is AnimeHttpSource || video.initialized) {
            return toPlayer(label, video)?.let { video.preferred to it }
        }
        val pending = AnsuVideo(
            url = "",
            quality = labelled(label, video.videoTitle),
            sourceLabel = name,
            serverName = label,
            resolve = { io { getResolvedVideo(video)?.let { toPlayer(label, it) } } },
        )
        return video.preferred to pending
    }

    /**
     * Aniyomi's `HosterLoader.getResolvedVideo`: an uninitialized video of an online source goes through the
     * source's `resolveVideo` (null when that fails, so the video is skipped) and comes back `initialized`.
     */
    private suspend fun getResolvedVideo(video: Video): Video? {
        val http = source as? AnimeHttpSource
        val resolved = if (http != null && !video.initialized) {
            try {
                http.resolveVideo(video)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.w("AniyomiSource", "$name: resolving '${video.videoTitle}' failed", e)
                null
            }
        } else {
            video
        }
        return resolved?.copy(initialized = true)?.also { it.url = resolved.url }
    }

    private fun hosterLabel(hoster: Hoster): String? =
        hoster.hosterName.takeUnless { it == Hoster.NO_HOSTER_LIST || it.isBlank() }

    /** Puts the hoster name in front of the title so the source sheet can tell servers apart. */
    private fun labelled(label: String?, title: String): String =
        if (label != null && !title.contains(label, ignoreCase = true)) "$label - $title" else title

    /** Turns a final source video into something the player can open, or null if it has no URL. */
    private fun toPlayer(label: String?, video: Video): AnsuVideo? {
        if (video.videoUrl.isBlank() || video.videoUrl == "null") return null
        // Streams that need the source's local proxy: start it and point the video at its real port.
        val playable = if (video.usesHttpServer()) withLocalServer(video) else video
        return AnsuVideo(
            url = playable.videoUrl,
            quality = labelled(label, playable.videoTitle),
            sourceLabel = name,
            serverName = label,
            headers = (
                playable.headers?.toMultimap()?.mapKeys { it.key.lowercase() }?.mapValues { (_, values) -> values.first() }.orEmpty() +
                    mpvHeaders(playable.mpvArgs)
                ),
            subtitleTracks = playable.subtitleTracks.map { track -> SubtitleTrack(track.url, track.lang) },
            audioTracks = playable.audioTracks.map { track -> SubtitleTrack(track.url, track.lang) },
        )
    }

    /**
     * Aniyomi plays with MPV and lets a source pass it options; Ansu plays with Media3, which has no such
     * options. The ones that only describe the HTTP request are translated into request headers
     * (`user-agent`, `referrer`, `http-header-fields`, `cookies`); every other option is not applicable here.
     */
    private fun mpvHeaders(args: List<Pair<String, String>>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for ((rawName, value) in args) {
            when (rawName.trim().removePrefix("--").lowercase()) {
                "user-agent" -> out["user-agent"] = value
                "referrer", "referer" -> out["referer"] = value
                "cookies" -> if (value.isNotBlank() && value != "no") out["cookie"] = value
                "http-header-fields" -> value.split(',').forEach { field ->
                    val index = field.indexOf(':')
                    if (index > 0) {
                        val name = field.substring(0, index).trim().lowercase()
                        if (name.isNotEmpty()) out[name] = field.substring(index + 1).trim()
                    }
                }
            }
        }
        return out
    }

    @Volatile
    private var localServer: eu.kanade.tachiyomi.animesource.model.HttpServer? = null

    /** Starts (once) the source's local HTTP server and rewrites [video] to use it; unchanged if it has none. */
    private fun withLocalServer(video: Video): Video {
        val http = source as? AnimeHttpSource ?: return video
        return try {
            val server = synchronized(this) {
                localServer?.takeIf { it.isRunning() } ?: http.createHttpServer()?.also {
                    it.start()
                    localServer = it
                }
            } ?: return video
            video.copyHttpServer(server.listeningPort)
        } catch (e: Exception) {
            android.util.Log.w("AniyomiSource", "$name: could not start the local http server", e)
            video
        }
    }

    private fun AnimesPage.toAnsu() = AnsuPage(animes.map { it.toAnsu() }, hasNextPage)

    private fun SAnime.toAnsu() = AnsuAnime(
        id = "${source.id}:$url",
        title = title,
        posterUrl = thumbnail_url,
        description = description,
        genres = getGenres().orEmpty(),
        origin = MediaOrigin.Extension(sourceId = source.id, urlPath = url),
    )

    private fun AnsuAnime.toSource(): SAnime {
        val path = (origin as? MediaOrigin.Extension)?.urlPath ?: id
        return SAnime.create().apply {
            url = path
            title = this@toSource.title
        }
    }

    private companion object {
        const val LIB_17 = 17.0
        val HOSTER_METHODS = listOf("getHosterList", "hosterListRequest", "hosterListParse")
        const val HOSTER_PARALLELISM = 4

        /** Listing a hoster's videos (the cheap part); resolving a picked video has [RESOLVE_TIMEOUT_MS]. */
        const val HOSTER_TIMEOUT_MS = 40_000L
        const val RESOLVE_TIMEOUT_MS = 30_000L
    }
}

/** A failure inside an extension that is not an Exception (a linkage Error), wrapped so it can be shown instead of crashing. */
class ExtensionApiError(message: String, cause: Throwable) : Exception(message, cause)
