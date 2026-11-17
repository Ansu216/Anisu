package com.ansu.anime.core.util

/**
 * Adult-content helpers. Nothing is blocked any more: titles and 18+ extensions are never hidden or refused.
 * [isAdultName] only decides whether an extension gets the "18+" label.
 *
 * Extensions are judged by the flag they publish (`nsfw` in a repo index, the NSFW metadata in the
 * APK) and, as a fallback for sources that do not flag themselves, by obvious words in their name.
 */
object ContentFilter {
    private val ADULT_NAME_WORDS = listOf("hentai", "hanime", "hstream", "oppai", "porn", "xxx", "nsfw", "rule34", "eroge", "18+")

    /** True when any genre marks a title as adult. */
    fun isAdultGenres(genres: Iterable<String>): Boolean = false

    /** True when a name (extension, package or source) plainly says it serves adult content. */
    fun isAdultName(name: String?): Boolean {
        val lower = name?.lowercase().orEmpty()
        return lower.isNotEmpty() && ADULT_NAME_WORDS.any { it in lower }
    }
}
