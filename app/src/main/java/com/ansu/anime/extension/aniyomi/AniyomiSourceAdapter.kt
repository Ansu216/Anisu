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
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
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
class AniyomiSourceAdapter(private val source: AnimeCatalogueSource) : AnsuSource {

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
            throw ExtensionApiError("${e::class.java.simpleName}: ${e.message ?: "extension is not compatible with Ansu"}", e)
        }
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
        val details = source.getAnimeDetails(anime.toSource())
        anime.copy(
            title = anime.title,
            posterUrl = details.thumbnail_url ?: anime.posterUrl,
            description = details.description ?: anime.description,
            genres = details.getGenres() ?: anime.genres,
        )
    }

    override suspend fun getEpisodeList(anime: AnsuAnime): List<AnsuEpisode> = io {
        val episodes = source.getEpisodeList(anime.toSource())
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

    override suspend fun getVideoList(episode: AnsuEpisode): List<AnsuVideo> = io {
        val sourceEpisode = SEpisode.create().apply {
            url = episode.id
            name = episode.name
            episode_number = episode.episodeNumber
        }
        // Library 16 extensions list hosters first and videos second; library 12-15 ones return a single
        // pseudo hoster that already carries the videos. getHosterList handles both.
        val hosters = try {
            source.getHosterList(sourceEpisode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnsupportedOperationException) {
            // A message-less UnsupportedOperationException comes from the extension's own stub
            // (e.g. `override fun videoListParse(...) = throw UnsupportedOperationException()`).
            // Try the other API generation before giving up, and say where it was thrown.
            android.util.Log.w("AniyomiSource", "$name: getHosterList threw UnsupportedOperationException", e)
            try {
                source.getVideoList(sourceEpisode).toHosterList()
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Throwable) {
                android.util.Log.w("AniyomiSource", "$name: legacy getVideoList also failed", e2)
                val frame = e.stackTrace.firstOrNull { !it.className.startsWith("eu.kanade.tachiyomi.animesource") }
                throw ExtensionApiError(
                    "$name: extension cannot list videos for this episode (${e::class.java.simpleName}" +
                        (frame?.let { " at ${it.className.substringAfterLast('.')}.${it.methodName}" } ?: "") + ")",
                    e,
                )
            }
        }
        val gate = Semaphore(HOSTER_PARALLELISM)
        coroutineScope {
            hosters.map { hoster ->
                async {
                    gate.withPermit {
                        // One broken hoster must not take the others down with it.
                        withTimeoutOrNull(HOSTER_TIMEOUT_MS) {
                            try {
                                videosOf(hoster).mapNotNull { resolve(it) }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                android.util.Log.w("AniyomiSource", "$name: hoster '${hoster.hosterName}' failed", e)
                                emptyList()
                            }
                        }.orEmpty()
                    }
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun videosOf(hoster: Hoster): List<Video> {
        val label = hoster.hosterName.takeUnless { it == Hoster.NO_HOSTER_LIST || it.isBlank() }
        return source.getVideoList(hoster).map { video ->
            // Put the hoster name in front of the title so the source sheet can tell servers apart.
            if (label != null && !video.videoTitle.contains(label, ignoreCase = true)) {
                video.copy(videoTitle = "$label - ${video.videoTitle}").also { it.url = video.url }
            } else {
                video
            }
        }
    }

    /** Turns a source video into something the player can open, or null if it cannot be resolved. */
    private suspend fun resolve(video: Video): AnsuVideo? {
        val http = source as? AnimeHttpSource
        val ready = if (!video.initialized && http != null) {
            runCatching { http.resolveVideo(video) }.getOrNull() ?: video
        } else {
            video
        }
        val finalUrl = ready.videoUrl.takeIf { it.isNotBlank() }
            ?: http?.let { runCatching { it.getVideoUrl(ready) }.getOrNull() }
            ?: ready.url.takeIf { it.startsWith("http") }
            ?: return null
        return AnsuVideo(
            url = finalUrl,
            quality = ready.videoTitle,
            sourceLabel = name,
            headers = ready.headers?.toMultimap()?.mapValues { (_, values) -> values.first() }.orEmpty(),
            subtitleTracks = ready.subtitleTracks.map { track -> SubtitleTrack(track.url, track.lang) },
        )
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
        const val HOSTER_PARALLELISM = 4
        const val HOSTER_TIMEOUT_MS = 25_000L
    }
}

/** A failure inside an extension that is not an Exception (a linkage Error), wrapped so it can be shown instead of crashing. */
class ExtensionApiError(message: String, cause: Throwable) : Exception(message, cause)
