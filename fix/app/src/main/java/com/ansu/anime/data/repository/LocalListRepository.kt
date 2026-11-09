package com.ansu.anime.data.repository

import com.ansu.anime.anilist.AniListMedia
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.data.db.LocalListDao
import com.ansu.anime.data.db.LocalListEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/** List statuses, using AniList's own strings so local and synced lists look the same. */
object ListStatus {
    const val CURRENT = "CURRENT"
    const val PLANNING = "PLANNING"
    const val COMPLETED = "COMPLETED"
    const val PAUSED = "PAUSED"
    const val DROPPED = "DROPPED"
    const val REPEATING = "REPEATING"

    /** Every list a show can sit in, in the order they are offered. */
    val ALL = listOf(CURRENT, PLANNING, COMPLETED, PAUSED, DROPPED, REPEATING)

    fun label(status: String?): String = when (status) {
        CURRENT -> "Watching"
        PLANNING -> "Planning"
        COMPLETED -> "Completed"
        PAUSED -> "Paused"
        DROPPED -> "Dropped"
        REPEATING -> "Repeating"
        else -> "Add to list"
    }
}

/**
 * The on-device library used when the user is not signed in to AniList:
 * favourites plus the Watching / Planning / Completed lists. It only stores
 * shows that have an AniList id (that is what the rest of the app keys on).
 */
class LocalListRepository(private val dao: LocalListDao) {

    val favourites: Flow<List<AniListMedia>> =
        dao.observeAll().map { rows -> rows.filter { it.isFavourite }.map { it.toMedia() } }

    fun withStatus(status: String): Flow<List<AniListMedia>> =
        dao.observeAll().map { rows -> rows.filter { it.status == status }.map { it.toMedia() } }

    /** Everything saved on this device, for pushing to AniList. */
    suspend fun snapshot(): List<LocalListEntity> = dao.getAll()

    fun observe(anilistId: Int): Flow<LocalListEntity?> = dao.observe(anilistId)

    /**
     * Fills in what a saved show is missing (its format, and its year if none was stored) once the
     * details page has loaded them. Does nothing for a show that is not saved on this device.
     */
    suspend fun backfillDetails(anilistId: Int, format: String?, year: Int?, title: String? = null) {
        if (dao.get(anilistId) == null) return
        if (!title.isNullOrBlank()) dao.setTitle(anilistId, title)
        if (format != null) dao.setFormat(anilistId, format)
        if (year != null) dao.fillYear(anilistId, year)
    }

    /** Flips the heart. Returns the new state, or false when the show has no AniList id. */
    suspend fun toggleFavourite(anime: SAnime, episodes: Int? = null, format: String? = null): Boolean {
        val id = anime.anilistId ?: return false
        val current = dao.get(id) ?: anime.toEntity(episodes, format)
        val updated = current.copy(isFavourite = !current.isFavourite, updatedAt = System.currentTimeMillis())
        save(updated)
        return updated.isFavourite
    }

    /** Puts the show in a list, or takes it out of every list when [status] is null. */
    suspend fun setStatus(anime: SAnime, status: String?, episodes: Int? = null, format: String? = null) {
        val id = anime.anilistId ?: return
        val current = dao.get(id) ?: anime.toEntity(episodes, format)
        save(
            current.copy(
                status = status,
                episodes = current.episodes ?: episodes,
                progress = if (status == ListStatus.COMPLETED) current.episodes ?: episodes ?: current.progress else current.progress,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Called by the player. Starting a show moves it to Watching (unless it is
     * already Completed); finishing an episode records progress, and finishing
     * the last known episode moves it to Completed.
     */
    suspend fun recordPlayback(anime: SAnime, episodeNumber: Int, watched: Boolean) {
        val id = anime.anilistId ?: return
        val existing = dao.get(id)
        if (existing?.status == ListStatus.COMPLETED) return
        val base = existing ?: anime.toEntity(null)
        var next = if (base.status != ListStatus.CURRENT && base.status != ListStatus.REPEATING) base.copy(status = ListStatus.CURRENT) else base
        if (watched && episodeNumber > next.progress) {
            next = next.copy(progress = episodeNumber)
            val total = next.episodes
            if (total != null && total > 0 && episodeNumber >= total) next = next.copy(status = ListStatus.COMPLETED)
        }
        if (existing == null || next != existing) save(next.copy(updatedAt = System.currentTimeMillis()))
    }

    /** A row with no heart and no list has nothing left to remember, so it is deleted. */
    private suspend fun save(entity: LocalListEntity) {
        if (!entity.isFavourite && entity.status == null) dao.remove(entity.anilistId) else dao.upsert(entity)
    }

    private fun SAnime.toEntity(episodes: Int?, format: String? = null) = LocalListEntity(
        anilistId = anilistId ?: 0,
        title = title,
        posterUrl = posterUrl,
        bannerUrl = bannerUrl,
        description = description,
        genresCsv = genres.joinToString("|"),
        year = releaseYear,
        averageScore = rating?.let { (it * 10).roundToInt() },
        episodes = episodes,
        isFavourite = false,
        status = null,
        progress = 0,
        updatedAt = System.currentTimeMillis(),
        format = format,
    )

    private fun LocalListEntity.toMedia() = AniListMedia(
        id = anilistId,
        title = title,
        posterUrl = posterUrl,
        bannerUrl = bannerUrl,
        description = description,
        genres = genresCsv.split("|").filter { it.isNotEmpty() },
        averageScore = averageScore,
        episodes = episodes,
        year = year,
        format = format,
        progress = progress,
    )
}
