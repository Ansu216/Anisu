package com.ansu.anime.data.repository

import com.ansu.anime.core.net.ApiErrorHandler
import com.ansu.anime.core.net.httpApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Extra artwork for one show. Every field is null when the service has nothing for it.
 * [logoUrl] is the title in its poster lettering (transparent PNG), [posterUrl] a portrait poster
 * that is usually sharper than AniList's cover, [backdropUrl] a 16:9 picture and [bannerUrl] a
 * very wide strip.
 */
data class AnimeArtwork(
    val logoUrl: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val bannerUrl: String? = null,
)

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

    private suspend fun fetch(anilistId: Int): AnimeArtwork = withContext(Dispatchers.IO) {
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

        AnimeArtwork(
            logoUrl = urlOf("clearlogo", "logo"),
            posterUrl = urlOf("poster"),
            backdropUrl = urlOf("fanart"),
            bannerUrl = urlOf("banner"),
        )
    }
}
