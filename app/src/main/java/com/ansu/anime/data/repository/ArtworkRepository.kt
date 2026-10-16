package com.ansu.anime.data.repository

import com.ansu.anime.core.net.ApiErrorHandler
import com.ansu.anime.core.net.httpApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Extra artwork for one show. Every field is null when the service has nothing for it.
 * [logoUrl] is the title in its poster lettering (transparent PNG), [posterUrl] a portrait poster
 * that is usually sharper than AniList's cover, [backdropUrl] a 16:9 picture and [bannerUrl] a
 * very wide strip.
 *
 * [logoUrl] is whatever clearlogo ani.zip lists first (often Japanese); [englishLogoUrl] is the English
 * one from TMDB, only filled in when a TMDB key is configured.
 *
 * [backdropUrl] and [bannerUrl] come from ani.zip and describe the whole series, so every season of
 * a show gets the same picture. [entryCoverUrl] is a wide cover for this exact AniList entry (one
 * season, one movie) and [season] is the season number ani.zip maps the entry to; together they let
 * [pickHeroImage] give each season and movie its own banner.
 */
@Serializable
data class AnimeArtwork(
    val logoUrl: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val bannerUrl: String? = null,
    val entryCoverUrl: String? = null,
    val season: Int? = null,
    /** TMDB's English-language title logo; null without a TMDB key or when TMDB has no English one. */
    val englishLogoUrl: String? = null,
    /** TMDB's best textless 16:9 backdrop for the show (a series-wide picture for TV); null without a key or when TMDB has none. */
    val tmdbBackdropUrl: String? = null,
    /** TMDB's best portrait poster (textless when it has one, else English); series-wide for TV, so see [heroPosterFor]. */
    val tmdbPosterUrl: String? = null,
    /** TMDB id and kind from ani.zip's mapping, used to look [englishLogoUrl] up. */
    val tmdbId: Int? = null,
    val tmdbIsMovie: Boolean = false,
)

/**
 * The title logo for the chosen language. English shows only a real English logo (TMDB) and otherwise
 * null, so the plain English text title is drawn instead of a Japanese-lettered logo; Romaji keeps
 * ani.zip's logo as before.
 */
fun AnimeArtwork.logoFor(language: com.ansu.anime.data.prefs.TitleLanguage): String? = when (language) {
    com.ansu.anime.data.prefs.TitleLanguage.ENGLISH -> englishLogoUrl
    com.ansu.anime.data.prefs.TitleLanguage.ROMAJI -> logoUrl
}

private val SEASON_IN_TITLE = Regex("""(\d+)(?:st|nd|rd|th)\s+Season|Season\s+(\d+)|\bPart\s+(\d+)|\bCour\s+(\d+)""", RegexOption.IGNORE_CASE)

/**
 * The small chip under a details title: "Season N" for a second or later season, plus "Part N" when the
 * title names a part or cour, joined as "Season 2 · Part 2". A first season with no part, and every
 * movie, OVA, ONA, special or music entry, gets no chip (null). The season number is ani.zip's, else
 * the "Season N" / "2nd Season" in the title, else unknown (a later entry with no number shows no
 * season rather than a wrong one).
 */
fun seasonLabel(title: String, format: String?, hasPrequel: Boolean, aniZipSeason: Int?): String? {
    if (format != null && !format.uppercase().startsWith("TV")) return null
    val fromTitle = Regex("""(\d+)(?:st|nd|rd|th)\s+Season|Season\s+(\d+)""", RegexOption.IGNORE_CASE)
        .find(title)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
    val season = (aniZipSeason ?: fromTitle)?.takeIf { it > 1 }
    val part = Regex("""\b(?:Part|Cour)\s+(\d+)""", RegexOption.IGNORE_CASE)
        .find(title)?.groupValues?.get(1)?.toIntOrNull()
    return listOfNotNull(season?.let { "Season $it" }, part?.let { "Part $it" })
        .takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * TMDB's portrait picture for a Home hero slide, or null when it should not be used. A movie has a poster of
 * its own; a TMDB TV show has one for the whole series, so a later season, special or OVA (which would show
 * the first season's picture) returns null and the caller keeps AniList's cover for that entry.
 */
fun AnimeArtwork.heroPosterFor(title: String, format: String?): String? {
    val poster = tmdbPosterUrl ?: return null
    if (tmdbIsMovie) return poster
    val kind = format?.uppercase()
    if (kind != null && !kind.startsWith("TV")) return null
    val titleSeason = SEASON_IN_TITLE.findAll(title).any { match ->
        match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull()?.let { it > 1 } == true
    }
    return if ((season ?: 1) > 1 || titleSeason) null else poster
}

/**
 * The hero picture for one details page, chosen per entry instead of per title.
 *
 * TMDB's textless backdrop comes first, with AniList's banner as the fallback (then the other wide
 * pictures, and finally the poster). A TMDB movie has a backdrop of its own, but a TMDB TV show has one
 * for the whole series, which would be identical for every season, special and OVA, so for those entries
 * AniList's art of that exact entry stays first and the series backdrop is not used at all.
 */
fun pickHeroImage(
    title: String,
    format: String?,
    hasPrequel: Boolean,
    entryBanner: String?,
    poster: String?,
    artwork: AnimeArtwork,
): String? {
    val kind = format?.uppercase()
    val standaloneKind = kind != null && !kind.startsWith("TV")
    val laterSeason = hasPrequel || (artwork.season ?: 1) > 1 ||
        SEASON_IN_TITLE.findAll(title).any { match ->
            match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull()?.let { it > 1 } == true
        }
    return if (standaloneKind || laterSeason) {
        (if (artwork.tmdbIsMovie) artwork.tmdbBackdropUrl else null) ?: entryBanner ?: artwork.entryCoverUrl ?: poster
    } else {
        artwork.tmdbBackdropUrl ?: entryBanner ?: artwork.backdropUrl ?: artwork.bannerUrl ?: artwork.entryCoverUrl ?: poster
    }
}

/**
 * Looks up the title logo and horizontal backdrop of a show by AniList id, using the same ani.zip
 * mapping service as [EpisodeMetadataRepository]. Best-effort: any failure yields an empty
 * [AnimeArtwork], so the UI falls back to the plain text title and the existing banner/poster.
 * Successful answers (and "no mapping" 404s) are cached for the life of the process, so the home
 * carousel and the details page never ask twice for the same show; failures are not cached.
 */
class ArtworkRepository(
    private val client: OkHttpClient,
    private val errors: ApiErrorHandler,
    private val tmdbApiKey: String = "",
    /** Where answers are kept between launches (the app's files dir); null keeps them in memory only. */
    cacheDir: File? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<Int, AnimeArtwork>()

    // Short timeouts: these pictures are optional, so a slow host must never hold a page up for a minute.
    private val http: OkHttpClient = client.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    // Own scope, so a lookup that was started keeps going when the screen that asked leaves, and the next
    // screen (Home -> details) joins it instead of starting a second one.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<Int, Deferred<AnimeArtwork>>()
    private val limiter = Semaphore(6)
    private val dir: File? = cacheDir?.let { File(it, "artwork-cache") }?.also { it.mkdirs() }

    @Serializable
    private class Stored(val savedAt: Long, val hadKey: Boolean, val art: AnimeArtwork)

    /** Forgets every logo and backdrop answer, in memory and on disk, so the next page asks again. */
    fun clearCache() {
        cache.clear()
        dir?.listFiles()?.forEach { it.delete() }
    }

    /** Warms the cache for these shows (a few at a time) so their pages and hero slides open with art ready. */
    fun prefetch(anilistIds: Collection<Int>) {
        anilistIds.distinct().filter { !cache.containsKey(it) }.forEach { id ->
            scope.launch { limiter.withPermit { get(id) } }
        }
    }

    suspend fun get(anilistId: Int): AnimeArtwork {
        cache[anilistId]?.let { return it }
        readDisk(anilistId)?.let { cache[anilistId] = it; return it }
        val job = inFlight.computeIfAbsent(anilistId) { id ->
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    load(id)
                } finally {
                    inFlight.remove(id)
                }
            }
        }
        job.start()
        return job.await()
    }

    private suspend fun load(anilistId: Int): AnimeArtwork = try {
        val (art, persist) = fetch(anilistId)
        cache[anilistId] = art
        if (persist) writeDisk(anilistId, art)
        art
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Optional extra data: log it, but never interrupt the user over a missing logo. Not cached.
        errors.report("Loading artwork", e, quiet = true)
        AnimeArtwork()
    }

    private fun fileFor(id: Int): File? = dir?.let { File(it, "$id.json") }

    private suspend fun readDisk(id: Int): AnimeArtwork? = withContext(Dispatchers.IO) {
        val file = fileFor(id)?.takeIf { it.isFile } ?: return@withContext null
        try {
            val stored = json.decodeFromString<Stored>(file.readText())
            val fresh = System.currentTimeMillis() - stored.savedAt < ARTWORK_TTL_MILLIS
            // An answer made without a TMDB key must not hide the English logo once a key exists.
            if (fresh && stored.hadKey == tmdbApiKey.isNotBlank()) stored.art else null
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    private suspend fun writeDisk(id: Int, art: AnimeArtwork) = withContext(Dispatchers.IO) {
        val file = fileFor(id) ?: return@withContext
        try {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(Stored(System.currentTimeMillis(), tmdbApiKey.isNotBlank(), art)))
            tmp.renameTo(file)
        } catch (_: Exception) {
            // A cache that cannot be written just means the next launch asks again.
        }
    }

    /** The artwork plus whether it is complete enough to keep on disk (a failed TMDB call is not kept). */
    private suspend fun fetch(anilistId: Int): Pair<AnimeArtwork, Boolean> = coroutineScope {
        // Kitsu's cover is a nice-to-have and its API is slow, so it gets a few seconds and never stalls the rest.
        val cover = async { withTimeoutOrNull(4_000L) { fetchEntryCover(anilistId) } }
        val base = fetchAniZip(anilistId)
        val tmdb = base.tmdbId?.let { fetchTmdbImages(it, base.tmdbIsMovie) }
        val art = base.copy(entryCoverUrl = cover.await(), englishLogoUrl = tmdb?.logo, tmdbBackdropUrl = tmdb?.backdrop, tmdbPosterUrl = tmdb?.poster)
        art to (tmdbApiKey.isBlank() || base.tmdbId == null || tmdb != null)
    }

    private class TmdbImages(val logo: String?, val backdrop: String?, val poster: String?)

    /**
     * The best English logo and the best textless backdrop TMDB has for [tmdbId], from one request; null with
     * no key or on any failure (the UI then falls back to the plain title and AniList's art). SVG logos are
     * skipped, Coil cannot draw them here, and a backdrop with lettering on it is never used.
     */
    private suspend fun fetchTmdbImages(tmdbId: Int, isMovie: Boolean): TmdbImages? = withContext(Dispatchers.IO) {
        if (tmdbApiKey.isBlank()) return@withContext null
        try {
            val kind = if (isMovie) "movie" else "tv"
            val builder = "https://api.themoviedb.org/3/$kind/$tmdbId/images".toHttpUrl().newBuilder()
                .addQueryParameter("include_image_language", "en,null")
            // A long "eyJ..." value is TMDB's v4 read token; the short hex one is the v3 key.
            val isToken = tmdbApiKey.startsWith("eyJ")
            if (!isToken) builder.addQueryParameter("api_key", tmdbApiKey)
            val request = Request.Builder().url(builder.build())
                .apply { if (isToken) header("Authorization", "Bearer $tmdbApiKey") }
                .build()
            val text = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                response.body.string()
            }
            if (text.isBlank()) return@withContext null
            val root = json.parseToJsonElement(text).jsonObject

            fun JsonObject.language() = (this["iso_639_1"] as? JsonPrimitive)?.contentOrNull
            fun JsonObject.path() = (this["file_path"] as? JsonPrimitive)?.contentOrNull
            fun JsonObject.votes() = (this["vote_average"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0

            val logo = (root["logos"] as? JsonArray).orEmpty().asSequence()
                .mapNotNull { it as? JsonObject }
                .filter { it.language() == "en" && it.path()?.endsWith(".svg", ignoreCase = true) == false }
                .maxByOrNull { it.votes() }
                ?.path()?.let { "https://image.tmdb.org/t/p/w500$it" }
            // Textless backdrops have no language tag in TMDB.
            val backdrop = (root["backdrops"] as? JsonArray).orEmpty().asSequence()
                .mapNotNull { it as? JsonObject }
                .filter { it["iso_639_1"] is kotlinx.serialization.json.JsonNull || it["iso_639_1"] == null }
                .maxByOrNull { it.votes() }
                ?.path()?.let { "https://image.tmdb.org/t/p/w1280$it" }
            // TMDB has no portrait backdrops; its portrait pictures are posters. Textless ones first (the title is
            // drawn over the hero anyway), then the English poster.
            val posters = (root["posters"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val poster = (
                posters.filter { it["iso_639_1"] is kotlinx.serialization.json.JsonNull || it["iso_639_1"] == null }.maxByOrNull { it.votes() }
                    ?: posters.filter { it.language() == "en" }.maxByOrNull { it.votes() }
                )?.path()?.let { "https://image.tmdb.org/t/p/w780$it" }
            if (logo == null && backdrop == null && poster == null) null else TmdbImages(logo, backdrop, poster)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // Optional extra: without it the UI falls back to the plain title and AniList's art.
        }
    }

    /** Wide cover of this exact AniList entry from Kitsu's id mapping; null when Kitsu has none or fails. */
    private suspend fun fetchEntryCover(anilistId: Int): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://kitsu.io/api/edge/mappings".toHttpUrl().newBuilder()
                .addQueryParameter("filter[externalSite]", "anilist/anime")
                .addQueryParameter("filter[externalId]", anilistId.toString())
                .addQueryParameter("include", "item")
                .build()
            val request = Request.Builder().url(url).header("Accept", "application/vnd.api+json").build()
            val text = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                response.body.string()
            }
            if (text.isBlank()) return@withContext null
            val included = json.parseToJsonElement(text).jsonObject["included"] as? JsonArray ?: return@withContext null
            val attributes = included.asSequence()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { (it["type"] as? JsonPrimitive)?.contentOrNull == "anime" }
                ?.get("attributes") as? JsonObject ?: return@withContext null
            val cover = attributes["coverImage"] as? JsonObject ?: return@withContext null
            listOf("original", "large").firstNotNullOfOrNull { key ->
                (cover[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("http") }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // Optional extra: a missing Kitsu cover just means the next fallback is used.
        }
    }

    private suspend fun fetchAniZip(anilistId: Int): AnimeArtwork = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.ani.zip/mappings?anilist_id=$anilistId").build()
        val text = http.newCall(request).execute().use { response ->
            // ani.zip answers 404 for shows it has no mapping for; that is "no data", not a failure.
            if (response.code == 404) return@use ""
            if (!response.isSuccessful) throw httpApiException(response.code, response.header("Retry-After")?.toLongOrNull(), null)
            response.body.string()
        }
        if (text.isBlank()) return@withContext AnimeArtwork()
        val images = json.parseToJsonElement(text).jsonObject["images"] as? JsonArray
            ?: return@withContext AnimeArtwork()

        fun urlOf(vararg types: String): String? = images.asSequence()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { image ->
                val type = (image["coverType"] as? JsonPrimitive)?.contentOrNull?.lowercase()
                type != null && type in types
            }
            ?.let { (it["url"] as? JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.startsWith("http") }

        // Which season of the series this entry is; the series images below are shared by all of them.
        val seasonElement = (json.parseToJsonElement(text).jsonObject["mappings"] as? JsonObject)?.get("season")
        val season = when (seasonElement) {
            is JsonObject -> (seasonElement["tvdb"] as? JsonPrimitive)?.intOrNull
                ?: (seasonElement["tmdb"] as? JsonPrimitive)?.intOrNull
            is JsonPrimitive -> seasonElement.intOrNull
            else -> null
        }

        val mappings = json.parseToJsonElement(text).jsonObject["mappings"] as? JsonObject
        // ani.zip reports the TMDB id as a *string* ("37854"), not as a number. Reading it with
        // `intOrNull` alone therefore answered null for every show, so TMDB was never asked at all
        // and neither the English title logo nor the TMDB backdrop/poster ever appeared.
        val tmdbId = (mappings?.get("themoviedb_id") as? JsonPrimitive)
            ?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
        val isMovie = (mappings?.get("type") as? JsonPrimitive)?.contentOrNull.equals("MOVIE", ignoreCase = true)

        AnimeArtwork(
            tmdbId = tmdbId,
            tmdbIsMovie = isMovie,
            season = season,
            logoUrl = urlOf("clearlogo") ?: urlOf("logo"),
            posterUrl = urlOf("poster"),
            backdropUrl = urlOf("fanart"),
            bannerUrl = urlOf("banner"),
        )
    }
}

/** How long a stored answer is trusted: logos and backdrops of existing shows barely change. */
private const val ARTWORK_TTL_MILLIS = 14L * 24 * 60 * 60 * 1000
