package eu.kanade.tachiyomi.animesource

import com.ansu.anime.extension.aniyomi.awaitFirst
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import rx.Observable

interface AnimeCatalogueSource : AnimeSource {
    override val lang: String

    val supportsLatest: Boolean

    suspend fun getPopularAnime(page: Int): AnimesPage = fetchPopularAnime(page).awaitFirst()

    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        fetchSearchAnime(page, query, filters).awaitFirst()

    suspend fun getLatestUpdates(page: Int): AnimesPage = fetchLatestUpdates(page).awaitFirst()

    fun fetchPopularAnime(page: Int): Observable<AnimesPage> = throw IllegalStateException("Not used")

    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
        throw IllegalStateException("Not used")

    fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = throw IllegalStateException("Not used")

    fun getFilterList(): AnimeFilterList
}
