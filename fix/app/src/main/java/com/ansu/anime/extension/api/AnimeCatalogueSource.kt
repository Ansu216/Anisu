package com.ansu.anime.extension.api

import com.ansu.anime.core.model.AnimeFilterList
import com.ansu.anime.core.model.AnimesPage
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.model.Video

/**
 * Implemented by every extension source, exactly the way Keiyoushi / Aniyomi
 * source extensions implement `AnimeCatalogueSource`. An extension APK
 * bundles one or more classes implementing this interface, declares them in
 * its manifest metadata, and the app's [com.ansu.anime.extension.ExtensionManager]
 * loads them at runtime — no compile-time dependency between the app and any
 * given extension.
 */
interface AnimeCatalogueSource {
    /** Stable id, e.g. a hash of [name] + [lang]. Must not collide across sources. */
    val id: Long

    val name: String
    val lang: String
    val baseUrl: String
    val supportsLatest: Boolean
        get() = false

    suspend fun getPopularAnime(page: Int): AnimesPage
    suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage
    suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>
    suspend fun getVideoList(episode: SEpisode): List<Video>

    /**
     * Same as [getVideoList], but hands over videos as soon as they are ready instead of waiting for every
     * server of the source (hoster-based sources have several, and one slow server must not hide the rest).
     * [log] receives one line per server for the diagnostics report.
     */
    suspend fun streamVideos(episode: SEpisode, log: (String) -> Unit, onVideos: suspend (List<Video>) -> Unit) {
        val videos = getVideoList(episode)
        if (videos.isNotEmpty()) onVideos(videos)
    }

    fun getFilterList(): AnimeFilterList = emptyList()
}
