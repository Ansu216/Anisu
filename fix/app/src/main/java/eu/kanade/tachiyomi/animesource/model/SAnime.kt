package eu.kanade.tachiyomi.animesource.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

/** Aniyomi extension API (library versions 12-17): the anime a source lists. */
interface SAnime : Serializable {
    var url: String
    var title: String
    var artist: String?
    var author: String?
    var description: String?
    var genre: String?
    var status: Int
    var thumbnail_url: String?
    var background_url: String?
    var update_strategy: AnimeUpdateStrategy
    var initialized: Boolean
    var fetch_type: FetchType
    var season_number: Double

    /** Library 17: source-specific data the app never shows. */
    var memo: JsonObject

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    fun copy(): SAnime = create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre
        it.status = status
        it.thumbnail_url = thumbnail_url
        it.background_url = background_url
        it.update_strategy = update_strategy
        it.initialized = initialized
        it.fetch_type = fetch_type
        it.season_number = season_number
        it.memo = memo
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6
        const val UPCOMING = 7

        fun create(): SAnime = SAnimeImpl()
    }
}

class SAnimeImpl : SAnime {
    override lateinit var url: String
    override lateinit var title: String
    override var artist: String? = null
    override var author: String? = null
    override var description: String? = null
    override var genre: String? = null
    override var status: Int = 0
    override var thumbnail_url: String? = null
    override var background_url: String? = null
    override var update_strategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE
    override var initialized: Boolean = false
    override var fetch_type: FetchType = FetchType.Episodes
    override var season_number: Double = -1.0
    override var memo: JsonObject = EmptyMemo
}

enum class AnimeUpdateStrategy { ALWAYS_UPDATE, ONLY_FETCH_ONCE }

enum class FetchType { Seasons, Episodes }
