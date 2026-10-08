package com.ansu.anime.data.repository

import com.ansu.anime.core.net.ApiErrorHandler
import com.ansu.anime.core.net.httpApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
import java.util.concurrent.ConcurrentHashMap

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
data class AnimeArtwork(
    val logoUrl: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val bannerUrl: String? = null,
    val entryCoverUrl: String? = null,
    val season: Int? = null,
    /** TMDB's English-language title logo; null without a TMDB key or when TMDB has no English one. */
    val englishLogoUrl: String? = null,
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
 * The hero picture for one details page, chosen per entry instead of per title.
 *
 * A first season (or a standalone show) keeps the sharp series backdrop. Later seasons, movies, OVAs
 * and specials are different AniList entries of the same series, and the series-wide backdrop would
 * be identical for all of them, so they use art of their own entry first: AniList's banner for that
 * entry, then the entry's own wide cover, and finally its poster. The series backdrop is not used
 * for them at all.
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
        entryBanner ?: artwork.entryCoverUrl ?: poster
    } else {
        artwork.backdropUrl ?: entryBanner ?: artwork.bannerUrl ?: artwork.entryCoverUrl ?: poster
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
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<Int, AnimeArtwork>()

    suspend fun get(anilistId: Int): AnimeArtwork {
        cache[anilistId]?.let { return it }
        return try {
            fetch(anilistId).also { cache[anilistId] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Optional extra data: log it, but never interrupt the user over a missing logo.
            errors.report("Loading artwork", e, quiet = true)
            AnimeArtwork()
        }
    }

    private suspend fun fetch(anilistId: Int): AnimeArtwork = coroutineScope {
        val cover = async { fetchEntryCover(anilistId) }
        val base = fetchAniZip(anilistId)
        val englishLogo = base.tmdbId?.let { fetchTmdbEnglishLogo(it, base.tmdbIsMovie) }
        base.copy(entryCoverUrl = cover.await(), englishLogoUrl = englishLogo)
    }

    /**
     * The best English-language logo TMDB has for [tmdbId]; null with no key, no English logo or any
     * failure (the UI then shows the plain English title). SVG logos are skipped, Coil cannot draw them here.
     */
    private suspend fun fetchTmdbEnglishLogo(tmdbId: Int, isMovie: Boolean): String? = withContext(Dispatchers.IO) {
        if (tmdbApiKey.isBlank()) return@withContext null
        try {
            val kind = if (isMovie) "movie" else "tv"
            val builder = "https://api.themoviedb.org/3/$kind/$tmdbId/images".toHttpUrl().newBuilder()
                .addQueryParameter("include_image_language", "en")
            // A long "eyJ..." value is TMDB's v4 read token; the short hex one is the v3 key.
            val isToken = tmdbApiKey.startsWith("eyJ")
            if (!isToken) builder.addQueryParameter("api_key", tmdbApiKey)
            val request = Request.Builder().url(builder.build())
                .apply { if (isToken) header("Authorization", "Bearer $tmdbApiKey") }
                .build()
            val text = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                response.body.string()
            }
            if (text.isBlank()) return@withContext null
            val logos = json.parseToJsonElement(text).jsonObject["logos"] as? JsonArray ?: return@withContext null
            val best = logos.asSequence()
                .mapNotNull { it as? JsonObject }
                .filter { (it["iso_639_1"] as? JsonPrimitive)?.contentOrNull == "en" }
                .filter { (it["file_path"] as? JsonPrimitive)?.contentOrNull?.endsWith(".svg", ignoreCase = true) == false }
                .maxByOrNull { (it["vote_average"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0 }
                ?: return@withContext null
            (best["file_path"] as? JsonPrimitive)?.contentOrNull?.let { "https://image.tmdb.org/t/p/w500$it" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // Optional extra: without it the UI falls back to the plain English title.
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
            val text = client.newCall(request).execute().use { response ->
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
        val text = client.newCall(request).execute().use { response ->
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
        val tmdbId = (mappings?.get("themoviedb_id") as? JsonPrimitive)?.intOrNull
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
