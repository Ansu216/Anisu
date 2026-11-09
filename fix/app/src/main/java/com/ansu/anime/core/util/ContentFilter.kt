package com.ansu.anime.core.util

/**
 * The app's built-in adult-content filter. It is always on and has no setting: hentai titles never
 * appear in Search, Home or Details, and 18+ extensions are never listed, installed or loaded, so
 * their sources can neither be searched nor used to stream.
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
