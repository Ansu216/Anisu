package com.ansu.anime.data.repository

import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.Shelf
import com.ansu.anime.extension.ExtensionManager
import com.ansu.anime.addon.AddonManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CatalogRepository(
    private val extensionManager: ExtensionManager,
    private val addonManager: AddonManager,
    private val aniListRepository: AniListRepository,
) {
    suspend fun buildHomeShelves(): List<Shelf> = withContext(Dispatchers.IO) {
        coroutineScope {
            val trendingDeferred = async { fetchTrending() }
            val continueWatchingDeferred = async { fetchContinueWatching() }

            listOfNotNull(
                trendingDeferred.await()?.let { Shelf("Trending Now", it) },
                continueWatchingDeferred.await()?.let { Shelf("Continue Watching", it) },
            )
        }
    }

    private suspend fun fetchTrending(): List<SAnime> {
        val trending = aniListRepository.getTrending()
        return trending.map { ani ->
            SAnime(
                id = "anilist:${ani.id}",
                title = ani.displayTitle,
                posterUrl = ani.coverImage?.extraLarge ?: ani.coverImage?.large,
                bannerUrl = ani.bannerImage,
                description = ani.plainDescription.ifBlank { null },
                genres = ani.genres,
                releaseYear = ani.seasonYear,
                rating = ani.averageScore?.div(10.0),
                anilistId = ani.id,
                origin = MediaOrigin.Extension(sourceId = 0L, urlPath = "anilist:${ani.id}"),
            )
        }
    }

    private suspend fun fetchContinueWatching(): List<SAnime> {
        val watching = aniListRepository.getCurrentlyWatching()
        return watching.mapNotNull { entry ->
            entry.media?.let { media ->
                SAnime(
                    id = "anilist:${media.id}",
                    title = media.title,
                    posterUrl = media.posterUrl,
                    bannerUrl = media.bannerUrl,
                    description = media.description,
                    genres = media.genres,
                    releaseYear = media.year,
                    rating = media.averageScore?.div(10.0),
                    anilistId = media.id,
                    origin = MediaOrigin.Extension(sourceId = 0L, urlPath = "anilist:${media.id}"),
                )
            }
        }
    }

    suspend fun search(query: String): List<SAnime> = withContext(Dispatchers.IO) {
        val results = aniListRepository.search(query)
        results.map { ani ->
            SAnime(
                id = "anilist:${ani.id}",
                title = ani.displayTitle,
                posterUrl = ani.coverImage?.extraLarge ?: ani.coverImage?.large,
                bannerUrl = ani.bannerImage,
                description = ani.plainDescription.ifBlank { null },
                genres = ani.genres,
                releaseYear = ani.seasonYear,
                rating = ani.averageScore?.div(10.0),
                anilistId = ani.id,
                origin = MediaOrigin.Extension(sourceId = 0L, urlPath = "anilist:${ani.id}"),
            )
        }
    }
}
