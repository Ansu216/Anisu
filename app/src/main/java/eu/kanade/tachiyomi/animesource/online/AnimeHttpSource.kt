package eu.kanade.tachiyomi.animesource.online

import com.ansu.anime.extension.aniyomi.awaitFirst
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.NotImplementedByExtensionException
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/** The base class most Aniyomi/Keiyoushi extension sources extend. */
abstract class AnimeHttpSource : AnimeCatalogueSource {

    protected val network: NetworkHelper by injectLazy()

    abstract val baseUrl: String

    open val versionId = 1

    override val id by lazy { generateId(name, lang, versionId) }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient
        get() = network.client

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { (bytes[it].toLong() and 0xff) shl (8 * (7 - it)) }.reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder(): Headers.Builder = Headers.Builder().add("User-Agent", network.defaultUserAgentProvider())

    override fun toString() = "$name (${lang.uppercase()})"

    // Popular

    override suspend fun getPopularAnime(page: Int): AnimesPage = fetchPopularAnime(page).awaitFirst()

    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
        client.newCall(popularAnimeRequest(page)).asObservableSuccess().map { popularAnimeParse(it) }

    protected abstract fun popularAnimeRequest(page: Int): Request

    protected abstract fun popularAnimeParse(response: Response): AnimesPage

    // Search

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        fetchSearchAnime(page, query, filters).awaitFirst()

    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
        Observable.defer { client.newCall(searchAnimeRequest(page, query, filters)).asObservableSuccess() }
            .map { searchAnimeParse(it) }

    protected abstract fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request

    protected abstract fun searchAnimeParse(response: Response): AnimesPage

    // Latest

    override suspend fun getLatestUpdates(page: Int): AnimesPage = fetchLatestUpdates(page).awaitFirst()

    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
        client.newCall(latestUpdatesRequest(page)).asObservableSuccess().map { latestUpdatesParse(it) }

    protected abstract fun latestUpdatesRequest(page: Int): Request

    protected abstract fun latestUpdatesParse(response: Response): AnimesPage

    // Details

    // The suspend members below run the request and the parsing on the caller's coroutine. They deliberately
    // avoid RxJava: RxJava 1 rethrows LinkageErrors (NoSuchMethodError, AbstractMethodError...) which an
    // extension built for another library version can raise, and that used to take the whole app down.

    override suspend fun getAnimeDetails(anime: SAnime): SAnime =
        client.newCall(animeDetailsRequest(anime)).awaitSuccess().use { animeDetailsParse(it) }.apply { initialized = true }

    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> =
        client.newCall(animeDetailsRequest(anime)).asObservableSuccess().map {
            animeDetailsParse(it).apply { initialized = true }
        }

    open fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    protected abstract fun animeDetailsParse(response: Response): SAnime

    // Episodes

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        if (anime.status == SAnime.LICENSED) throw Exception("Licensed - No episodes to show")
        return client.newCall(episodeListRequest(anime)).awaitSuccess().use { episodeListParse(it) }
    }

    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> =
        if (anime.status != SAnime.LICENSED) {
            client.newCall(episodeListRequest(anime)).asObservableSuccess().map { episodeListParse(it) }
        } else {
            Observable.error(Exception("Licensed - No episodes to show"))
        }

    protected open fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    protected abstract fun episodeListParse(response: Response): List<SEpisode>

    // Seasons (library 16)

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> =
        client.newCall(seasonListRequest(anime)).awaitSuccess().use { seasonListParse(it) }

    protected open fun seasonListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    protected open fun seasonListParse(response: Response): List<SAnime> =
        throw NotImplementedByExtensionException("seasonListParse")

    // Hosters and videos (library 16)

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val hosters = try {
            client.newCall(hosterListRequest(episode)).awaitSuccess().use { hosterListParse(it) }
        } catch (e: UnsupportedOperationException) {
            // A library 12-15 extension (or one that stubs hosterListParse): it only knows "episode -> videos".
            null
        }
        return hosters ?: getVideoList(episode).toHosterList()
    }

    protected open fun hosterListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url, headers)

    protected open fun hosterListParse(response: Response): List<Hoster> =
        throw NotImplementedByExtensionException("hosterListParse")

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        hoster.videoList?.let { return it.sortVideos() }
        return client.newCall(videoListRequest(hoster)).awaitSuccess().use { videoListParse(it, hoster) }.sortVideos()
    }

    protected open fun videoListRequest(hoster: Hoster): Request = GET(hoster.hosterUrl, headers)

    protected open fun videoListParse(response: Response, hoster: Hoster): List<Video> =
        throw NotImplementedByExtensionException("videoListParse(hoster)")

    // Videos (library 12-15)

    override suspend fun getVideoList(episode: SEpisode): List<Video> =
        client.newCall(videoListRequest(episode)).awaitSuccess().use { videoListParse(it) }.sortVideos()

    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> =
        client.newCall(videoListRequest(episode)).asObservableSuccess().map { videoListParse(it).sortVideos() }

    protected open fun videoListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url, headers)

    protected open fun videoListParse(response: Response): List<Video> =
        throw NotImplementedByExtensionException("videoListParse(episode)")

    open fun List<Video>.sort(): List<Video> = this

    private fun List<Video>.sortVideos(): List<Video> = with(this@AnimeHttpSource) { sort() }

    // Video url (for videos the source returned without a final `videoUrl`)

    /** Library 16: turns a video that still needs work (`initialized == false`) into a playable one. */
    open suspend fun resolveVideo(video: Video): Video? = video

    open suspend fun getVideoUrl(video: Video): String =
        client.newCall(videoUrlRequest(video)).awaitSuccess().use { videoUrlParse(it) }

    open fun fetchVideoUrl(video: Video): Observable<String> =
        client.newCall(videoUrlRequest(video)).asObservableSuccess().map { videoUrlParse(it) }

    protected open fun videoUrlRequest(video: Video): Request = GET(video.url.ifBlank { video.videoUrl }, headers)

    protected open fun videoUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Url helpers

    fun SAnime.setUrlWithoutDomain(orig: String) {
        url = getUrlWithoutDomain(orig)
    }

    fun SEpisode.setUrlWithoutDomain(orig: String) {
        url = getUrlWithoutDomain(orig)
    }

    private fun getUrlWithoutDomain(orig: String): String = try {
        val uri = URI(orig.replace(" ", "%20"))
        var out = uri.path
        if (uri.query != null) out += "?" + uri.query
        if (uri.fragment != null) out += "#" + uri.fragment
        out
    } catch (e: URISyntaxException) {
        orig
    }

    open fun getAnimeUrl(anime: SAnime): String = animeDetailsRequest(anime).url.toString()

    open fun getEpisodeUrl(episode: SEpisode): String = GET(baseUrl + episode.url, headers).url.toString()

    open fun prepareNewEpisode(episode: SEpisode, anime: SAnime) {}

    override fun getFilterList() = AnimeFilterList()
}
