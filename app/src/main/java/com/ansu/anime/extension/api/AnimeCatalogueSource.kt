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

    fun getFilterList(): AnimeFilterList = emptyList()
}
