package eu.kanade.tachiyomi.animesource.model

import kotlinx.serialization.json.JsonObject

/**
 * Extension API library 16/17: a group of videos that come from the same place, e.g. one server of a site.
 * With [lazy] set the videos are only fetched when this hoster is picked.
 */
open class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
    val memo: JsonObject = EmptyMemo,
) {
    @Transient
    @Volatile
    var status: State = State.IDLE

    enum class State { IDLE, LOADING, READY, ERROR }

    /** Library 16 constructor (no `memo`), kept so extensions built for 16 still link. */
    @Deprecated("Used only for compatibility with ext lib 16, do not use", level = DeprecationLevel.HIDDEN)
    constructor(
        hosterUrl: String = "",
        hosterName: String = "",
        videoList: List<Video>? = null,
        internalData: String = "",
        lazy: Boolean = false,
    ) : this(hosterUrl, hosterName, videoList, internalData, lazy, EmptyMemo)

    fun copy(
        hosterUrl: String = this.hosterUrl,
        hosterName: String = this.hosterName,
        videoList: List<Video>? = this.videoList,
        internalData: String = this.internalData,
        lazy: Boolean = this.lazy,
        memo: JsonObject = this.memo,
    ): Hoster = Hoster(hosterUrl, hosterName, videoList, internalData, lazy, memo)

    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"

        /** Wraps already-known videos in a single hoster (what library 12-15 sources return). */
        fun List<Video>.toHosterList(): List<Hoster> =
            listOf(Hoster(hosterUrl = "", hosterName = NO_HOSTER_LIST, videoList = this, memo = EmptyMemo))
    }
}
