package com.kernel.anime.data.repository

import com.kernel.anime.anilist.AniListRepository
import com.kernel.anime.core.model.MediaOrigin
import com.kernel.anime.core.model.SAnime
import com.kernel.anime.core.model.SEpisode
import com.kernel.anime.data.db.ContinueWatchingDao
import com.kernel.anime.data.db.ContinueWatchingEntity
import kotlinx.coroutines.flow.Flow

class ContinueWatchingRepository(
    private val dao: ContinueWatchingDao,
    private val aniList: AniListRepository,
) {
    val entries: Flow<List<ContinueWatchingEntity>> = dao.observeAll()

    /** Called periodically by the player as it advances, and once more on pause/exit. */
    suspend fun updateProgress(
        anime: SAnime,
        episode: SEpisode,
        positionSeconds: Long,
        durationSeconds: Long,
    ) {
        val anilistId = anime.anilistId ?: return
        dao.upsert(
            ContinueWatchingEntity(
                anilistId = anilistId,
                title = anime.title,
                posterUrl = anime.posterUrl,
                bannerUrl = anime.bannerUrl,
                episodeId = episode.id,
                episodeNumber = episode.episodeNumber,
                episodeName = episode.name,
                positionSeconds = positionSeconds,
                durationSeconds = durationSeconds,
                originExtensionSourceId = (anime.origin as? MediaOrigin.Extension)?.sourceId,
                originAddonId = (anime.origin as? MediaOrigin.Addon)?.addonId,
                originAddonBaseUrl = (anime.origin as? MediaOrigin.Addon)?.addonBaseUrl,
                lastWatchedAt = System.currentTimeMillis(),
            ),
        )

        // Consider an episode "watched" for AniList sync once it's mostly played through.
        val watchedThreshold = durationSeconds > 0 && positionSeconds >= durationSeconds * 0.9
        if (watchedThreshold) {
            aniList.reportProgress(anilistId, episode.episodeNumber.toInt())
        }
    }

    suspend fun remove(anilistId: Int) = dao.remove(anilistId)

    suspend fun resumePointFor(anilistId: Int) = dao.get(anilistId)
}
