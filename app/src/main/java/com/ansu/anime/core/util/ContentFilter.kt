package com.ansu.anime.core.util

/**
 * The app's built-in adult-content filter. For titles it is always on and has no setting: hentai never
 * appears in Search, Home or Details (mature but not adult series such as Berserk are fine). 18+ extensions
 * are hidden by default and only listed, installed and loaded while the Extensions screen's NSFW checkbox
 * is on; even then their results are matched to AniList titles, which this filter still screens.
 *
 * Extensions are judged by the flag they publish (`nsfw` in a repo index, the NSFW metadata in the
 * APK) and, as a fallback for sources that do not flag themselves, by obvious words in their name.
 */
object ContentFilter {
    private val ADULT_GENRES = setOf("hentai", "erotica", "adult", "18+", "nsfw", "porn")
    private val ADULT_NAME_WORDS = listOf("hentai", "hanime", "hstream", "oppai", "porn", "xxx", "nsfw", "rule34", "eroge", "18+")

    /** True when any genre marks a title as adult. */
    fun isAdultGenres(genres: Iterable<String>): Boolean =
        genres.any { it.trim().lowercase() in ADULT_GENRES }

    /** True when a name (extension, package or source) plainly says it serves adult content. */
    fun isAdultName(name: String?): Boolean {
        val lower = name?.lowercase().orEmpty()
        return lower.isNotEmpty() && ADULT_NAME_WORDS.any { it in lower }
    }
}
