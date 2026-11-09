package com.ansu.anime.data.repository

import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.data.db.ContinueWatchingDao
import com.ansu.anime.data.db.ContinueWatchingEntity
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow

class ContinueWatchingRepository(
    private val dao: ContinueWatchingDao,
    private val aniList: AniListRepository,
    private val localList: LocalListRepository,
    /** Where the last played source of each show is kept; null disables the memory. */
    private val lastSourcePrefs: SharedPreferences? = null,
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
        // The format may be unknown here (an entry opened from Continue Watching); keep what was stored then.
        val isMovie = when {
            anime.format != null -> anime.format.equals("Movie", ignoreCase = true) || anime.format.equals("MOVIE", ignoreCase = true)
            else -> dao.get(anilistId)?.isMovie ?: false
        }
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
                lastWatchedAt = System.currentTimeMillis(),
                isMovie = isMovie,
            ),
        )

        // Consider an episode "watched" for AniList sync once it's mostly played through.
        val watchedThreshold = durationSeconds > 0 && positionSeconds >= durationSeconds * 0.9
        if (watchedThreshold) {
            aniList.reportProgress(anilistId, episode.episodeNumber.toInt())
        }
        // Signed out: keep the list on the device instead.
        if (!aniList.isLoggedIn.value) {
            localList.recordPlayback(anime, episode.episodeNumber.toInt(), watchedThreshold)
        }
    }

    suspend fun remove(anilistId: Int) {
        dao.remove(anilistId)
        lastSourcePrefs?.edit()?.remove(anilistId.toString())?.apply()
    }

    suspend fun resumePointFor(anilistId: Int) = dao.get(anilistId)

    /**
     * The stream a show was last playing from, or null once 3 days have passed since it last played.
     * Resuming a show tries this source first, so it keeps coming from the server that already worked.
     */
    fun lastSourceFor(anilistId: Int): LastPlayedSource? {
        val prefs = lastSourcePrefs ?: return null
        val stored = parse(prefs.getString(anilistId.toString(), null)) ?: return null
        if (System.currentTimeMillis() - stored.second > LAST_SOURCE_TTL_MILLIS) {
            prefs.edit().remove(anilistId.toString()).apply()
            return null
        }
        return stored.first
    }

    /** Called while a stream is playing; refreshes the 3 day clock. Cheap enough to call every second. */
    fun rememberSource(anilistId: Int, sourceId: Long, serverName: String?, label: String?) {
        val prefs = lastSourcePrefs ?: return
        val now = System.currentTimeMillis()
        val pick = LastPlayedSource(sourceId, serverName?.clean(), label?.clean())
        val old = parse(prefs.getString(anilistId.toString(), null))
        if (old != null && old.first == pick && now - old.second < REFRESH_EVERY_MILLIS) return
        val editor = prefs.edit()
        // Shows nobody resumed for 3 days drop out here, so the file never grows without bound.
        prefs.all.forEach { (key, value) ->
            val at = parse(value as? String)?.second
            if (at == null || now - at > LAST_SOURCE_TTL_MILLIS) editor.remove(key)
        }
        editor.putString(anilistId.toString(), "${pick.sourceId}\t${pick.serverName.orEmpty()}\t${pick.label.orEmpty()}\t$now")
        editor.apply()
    }

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ')

    private fun parse(raw: String?): Pair<LastPlayedSource, Long>? {
        val parts = raw?.split('\t') ?: return null
        if (parts.size != 4) return null
        val id = parts[0].toLongOrNull() ?: return null
        val at = parts[3].toLongOrNull() ?: return null
        return LastPlayedSource(id, parts[1].ifEmpty { null }, parts[2].ifEmpty { null }) to at
    }

    private companion object {
        const val LAST_SOURCE_TTL_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val REFRESH_EVERY_MILLIS = 10L * 60 * 1000
    }
}

/** The extension, server and quality a show last played from. */
data class LastPlayedSource(val sourceId: Long, val serverName: String?, val label: String?)
