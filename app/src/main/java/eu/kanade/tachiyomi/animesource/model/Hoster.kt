package eu.kanade.tachiyomi.animesource.model

/**
 * Extension API library 16: a group of videos that come from the same place, e.g. one server of a site.
 * With [lazy] set the videos are only fetched when this hoster is picked.
 */
class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
) {
    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"

        /** Wraps already-known videos in a single hoster (what library 12-15 sources return). */
        fun List<Video>.toHosterList(): List<Hoster> =
            listOf(Hoster(hosterName = NO_HOSTER_LIST, videoList = this))
    }
}
