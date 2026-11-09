package eu.kanade.tachiyomi.animesource.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SEpisode : Serializable {
    var url: String
    var name: String
    var date_upload: Long
    var episode_number: Float
    var scanlator: String?
    var summary: String?
    var preview_url: String?
    var fillermark: Boolean

    /** Library 17: source-specific data the app never shows. */
    var memo: JsonObject

    fun copyFrom(other: SEpisode) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        episode_number = other.episode_number
        scanlator = other.scanlator
        summary = other.summary
        preview_url = other.preview_url
        fillermark = other.fillermark
        memo = other.memo
    }

    companion object {
        fun create(): SEpisode = SEpisodeImpl()
    }
}

class SEpisodeImpl : SEpisode {
    override lateinit var url: String
    override lateinit var name: String
    override var date_upload: Long = 0
    override var episode_number: Float = -1f
    override var scanlator: String? = null
    override var summary: String? = null
    override var preview_url: String? = null
    override var fillermark: Boolean = false
    override var memo: JsonObject = EmptyMemo
}
