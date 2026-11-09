package eu.kanade.tachiyomi.animesource

import com.ansu.anime.extension.aniyomi.awaitFirst
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import rx.Observable

/**
 * Aniyomi extension API (library versions 12-17), re-implemented so extension APKs built for
 * Aniyomi/Keiyoushi find the classes they were compiled against inside Ansu.
 *
 * Library 17 added the combined "update" calls and related anime. Library 16 replaced "episode -> videos" with "episode -> hosters -> videos". Both generations live
 * here side by side; every member an extension may or may not implement has a safe default.
 */
interface AnimeSource {
    val id: Long

    val name: String

    val lang: String
        get() = ""

    suspend fun getAnimeDetails(anime: SAnime): SAnime = fetchAnimeDetails(anime).awaitFirst()

    suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = fetchEpisodeList(anime).awaitFirst()

    /**
     * Library 17: details and/or episodes in one call. The default builds the result from the older
     * [getAnimeDetails] / [getEpisodeList], so an extension built for an earlier library still answers it.
     */
    suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate = SAnimeEpisodeUpdate(
        if (fetchDetails) getAnimeDetails(anime) else anime,
        if (fetchEpisodes) getEpisodeList(anime) else episodes,
    )

    /** Library 17: details and/or seasons in one call; the default uses [getAnimeDetails] / [getSeasonList]. */
    suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = SAnimeSeasonUpdate(
        if (fetchDetails) getAnimeDetails(anime) else anime,
        if (fetchSeasons) getSeasonList(anime) else seasons,
    )

    /** Library 17: whether [getRelatedAnimeList] may be called. */
    val supportsRelatedAnime: Boolean
        get() = false

    /** Library 17: anime related to [anime], grouped by label. */
    suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> = emptyList()

    /** Library 16: the seasons of an anime whose `fetch_type` is Seasons. */
    suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

    /** Library 16: the places an episode can be watched from. */
    suspend fun getHosterList(episode: SEpisode): List<Hoster> = getVideoList(episode).toHosterList()

    /** Library 16: the videos of one hoster. */
    suspend fun getVideoList(hoster: Hoster): List<Video> = hoster.videoList.orEmpty()

    /** Library 12-15: the videos of an episode. */
    suspend fun getVideoList(episode: SEpisode): List<Video> = fetchVideoList(episode).awaitFirst()

    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> = throw IllegalStateException("Not used")

    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> = throw IllegalStateException("Not used")

    fun fetchVideoList(episode: SEpisode): Observable<List<Video>> = throw IllegalStateException("Not used")
}
