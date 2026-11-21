package com.ansu.anime.extension.cloudstream

import com.ansu.anime.core.model.AnimeFilterList as AnsuFilterList
import com.ansu.anime.core.model.AnimesPage as AnsuPage
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime as AnsuAnime
import com.ansu.anime.core.model.SEpisode as AnsuEpisode
import com.ansu.anime.core.model.SubtitleTrack
import com.ansu.anime.core.model.Video as AnsuVideo
import com.ansu.anime.extension.aniyomi.ExtensionApiError
import com.ansu.anime.extension.api.AnimeCatalogueSource as AnsuSource
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Presents one CloudStream provider ([MainAPI]) through Anisu's own source contract, so Home, Search, Details and
 * the player treat it like an Aniyomi source. A provider's calls are CloudStream's:
 * `getMainPage` / `search` for listings, `load` for details + episodes, `loadLinks` for streams.
 * Provider code does network I/O of its own, so every call is moved to [Dispatchers.IO].
 */
class CloudStreamSourceAdapter(private val api: MainAPI) : AnsuSource {

    override val id: Long = stableId(api.name, api.lang)
    override val name: String get() = api.name
    override val lang: String get() = api.lang
    override val baseUrl: String get() = api.mainUrl
    override val supportsLatest: Boolean get() = api.hasMainPage && api.mainPage.size > 1

    /** Provider code can raise `Error`s (NoSuchMethodError, NoClassDefFoundError...) that would close the app; they become a normal exception. */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } catch (t: Throwable) {
            android.util.Log.e("CloudStreamSource", "${api.name}: provider raised ${t::class.java.name}", t)
            throw ExtensionApiError(describePluginError(t), t)
        }
    }

    // ---- listings -------------------------------------------------------------------------------------------

    override suspend fun getPopularAnime(page: Int): AnsuPage = mainPage(0, page)

    override suspend fun getLatestUpdates(page: Int): AnsuPage = mainPage(1, page)

    private suspend fun mainPage(index: Int, page: Int): AnsuPage = io {
        if (!api.hasMainPage) return@io AnsuPage(emptyList(), false)
        val section = api.mainPage.getOrNull(index) ?: api.mainPage.firstOrNull() ?: return@io AnsuPage(emptyList(), false)
        val response = api.getMainPage(page, MainPageRequest(section.name, section.data, section.horizontalImages))
            ?: return@io AnsuPage(emptyList(), false)
        val items = response.items.flatMap { it.list }.distinctBy { it.url }
        AnsuPage(items.map { it.toAnsu() }, response.hasNext)
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnsuFilterList): AnsuPage = io {
        // CloudStream's search(query) has no paging; every page after the first is empty so the list does not repeat.
        if (page > 1) return@io AnsuPage(emptyList(), false)
        val results = api.search(query).orEmpty().distinctBy { it.url }
        AnsuPage(results.map { it.toAnsu() }, false)
    }

    // ---- details + episodes ---------------------------------------------------------------------------------

    /** `load` is the one expensive call and serves both details and episodes, so the last answers are kept briefly. */
    private val loadCache = ConcurrentHashMap<String, LoadResponse>()

    private suspend fun loadResponse(url: String): LoadResponse? {
        loadCache[url]?.let { return it }
        val fresh = api.load(url) ?: return null
        if (loadCache.size > 40) loadCache.clear()
        loadCache[url] = fresh
        return fresh
    }

    private fun AnsuAnime.url(): String = (origin as? MediaOrigin.Extension)?.urlPath ?: id

    override suspend fun getAnimeDetails(anime: AnsuAnime): AnsuAnime = io {
        val details = loadResponse(anime.url()) ?: return@io anime
        anime.copy(
            posterUrl = details.posterUrl ?: anime.posterUrl,
            bannerUrl = details.backgroundPosterUrl ?: anime.bannerUrl,
            description = details.plot ?: anime.description,
            genres = details.tags.orEmpty().ifEmpty { anime.genres },
            releaseYear = details.year ?: anime.releaseYear,
        )
    }

    override suspend fun getEpisodeList(anime: AnsuAnime): List<AnsuEpisode> = io {
        val details = loadResponse(anime.url()) ?: return@io emptyList()
        details.toEpisodes()
    }

    private fun LoadResponse.toEpisodes(): List<AnsuEpisode> {
        val list: List<Episode> = when (this) {
            is AnimeLoadResponse ->
                // Subbed first; a provider that lists only a dub (or does not say) still gets its episodes.
                episodes[DubStatus.Subbed] ?: episodes[DubStatus.None] ?: episodes.values.firstOrNull().orEmpty()
            is TvSeriesLoadResponse -> episodes
            is MovieLoadResponse -> return listOf(single(dataUrl))
            is LiveStreamLoadResponse -> return listOf(single(dataUrl))
            else -> emptyList()
        }
        val ordered = list.withIndex()
            .sortedWith(compareBy<IndexedValue<Episode>>({ it.value.season ?: 0 }, { it.value.episode ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
        // A real number is only trusted when it is unique; several seasons restart at 1, so those are numbered in order.
        val numbers = ordered.mapNotNull { it.episode }
        val useNumbers = numbers.size == ordered.size && numbers.toSet().size == numbers.size
        return ordered.mapIndexed { index, ep ->
            val number = if (useNumbers) ep.episode!!.toFloat() else (index + 1).toFloat()
            AnsuEpisode(
                id = ep.data,
                name = ep.name?.takeIf { it.isNotBlank() } ?: "Episode ${number.toInt()}",
                episodeNumber = number,
                thumbnailUrl = ep.posterUrl,
                dateUpload = ep.date ?: 0L,
                description = ep.description,
                sourceId = this@CloudStreamSourceAdapter.id,
            )
        }
    }

    private fun LoadResponse.single(data: String) = AnsuEpisode(
        id = data,
        name = name,
        episodeNumber = 1f,
        thumbnailUrl = posterUrl,
        sourceId = this@CloudStreamSourceAdapter.id,
    )

    // ---- streams --------------------------------------------------------------------------------------------

    override suspend fun getVideoList(episode: AnsuEpisode): List<AnsuVideo> {
        val all = mutableListOf<AnsuVideo>()
        streamVideos(episode, log = {}) { batch -> synchronized(all) { all += batch } }
        return synchronized(all) { all.toList() }
    }

    /**
     * `loadLinks` calls back once per stream and once per subtitle while it works, so each stream goes to the player as
     * soon as it arrives instead of after the slowest server. Subtitles known at that moment ride along with it.
     */
    override suspend fun streamVideos(episode: AnsuEpisode, log: (String) -> Unit, onVideos: suspend (List<AnsuVideo>) -> Unit) {
        io {
            val subtitles = java.util.Collections.synchronizedList(mutableListOf<SubtitleTrack>())
            val links = Channel<AnsuVideo>(Channel.UNLIMITED)
            var count = 0
            coroutineScope {
                val consumer = launch {
                    for (video in links) {
                        count++
                        onVideos(listOf(video.copy(subtitleTracks = synchronized(subtitles) { subtitles.toList() })))
                    }
                }
                try {
                    val finished = withTimeoutOrNull(LOAD_LINKS_TIMEOUT_MS) {
                        api.loadLinks(
                            episode.id,
                            false,
                            { sub -> if (sub.url.isNotBlank()) subtitles += SubtitleTrack(sub.url, sub.lang) },
                            { link ->
                                // Torrents cannot be played by the player.
                                if (!link.url.startsWith("magnet:") && link.url.isNotBlank()) {
                                    val headers = link.headers.toMutableMap().also { h ->
                                        if (link.referer.isNotBlank() && h.keys.none { it.equals("referer", true) }) h["Referer"] = link.referer
                                    }
                                    links.trySend(
                                        AnsuVideo(
                                            url = link.url,
                                            quality = if (link.quality > 0) "${link.quality}p" else "Auto",
                                            sourceLabel = name,
                                            headers = headers,
                                            serverName = link.name.ifBlank { link.source },
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    if (finished == null) log("  $name: timed out after ${LOAD_LINKS_TIMEOUT_MS / 1000}s")
                } finally {
                    links.close()
                }
                consumer.join()
            }
            log("  $name: $count stream(s)")
        }
    }

    // ---- mapping --------------------------------------------------------------------------------------------

    private fun SearchResponse.toAnsu() = AnsuAnime(
        id = "${this@CloudStreamSourceAdapter.id}:$url",
        title = name,
        posterUrl = posterUrl,
        format = when (type) {
            TvType.Anime -> "TV"
            TvType.AnimeMovie -> "Movie"
            TvType.OVA -> "OVA"
            else -> null
        },
        origin = MediaOrigin.Extension(sourceId = this@CloudStreamSourceAdapter.id, urlPath = url),
    )

    private companion object {
        const val LOAD_LINKS_TIMEOUT_MS = 60_000L

        /** A stable 63-bit id from name + language, so a source keeps its id (and the person's settings and history) across reloads. */
        fun stableId(name: String, lang: String): Long {
            val digest = MessageDigest.getInstance("MD5").digest("cloudstream/${name.lowercase()}/$lang".toByteArray())
            return (0 until 8).fold(0L) { acc, i -> (acc shl 8) or (digest[i].toLong() and 0xff) } and Long.MAX_VALUE
        }
    }
}
