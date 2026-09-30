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
)

private val SEASON_IN_TITLE = Regex("""(\d+)(?:st|nd|rd|th)\s+Season|Season\s+(\d+)|\bPart\s+(\d+)|\bCour\s+(\d+)""", RegexOption.IGNORE_CASE)

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
        base.copy(entryCoverUrl = cover.await())
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
                response.body?.string().orEmpty()
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
            response.body?.string().orEmpty()
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

        AnimeArtwork(
            season = season,
            logoUrl = urlOf("clearlogo") ?: urlOf("logo"),
            posterUrl = urlOf("poster"),
            backdropUrl = urlOf("fanart"),
            bannerUrl = urlOf("banner"),
        )
    }
}
