package com.ansu.anime.ui.search

import com.ansu.anime.core.model.SAnime
import kotlin.math.max
import kotlin.math.min

/**
 * Typo-tolerant title matching for the Search screen.
 *
 * AniList only matches what it can find on its own, so this ranks the titles already in hand (the current results
 * and every title the session has shown) by how close they are to what was typed. A word with a missing or swapped
 * letter still counts, and so does a word that is only part typed ("naru" finds "Naruto").
 */
object TitleMatcher {
    /** A title scoring below this is not close enough to suggest. */
    const val MIN_SCORE = 0.6f

    fun normalize(text: String): String =
        text.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /** 0..1: the average of how well each typed word matches its closest word in the title. */
    fun score(query: String, title: String): Float {
        val queryWords = normalize(query).split(' ').filter { it.isNotEmpty() }
        val titleWords = normalize(title).split(' ').filter { it.isNotEmpty() }
        if (queryWords.isEmpty() || titleWords.isEmpty()) return 0f
        var total = 0f
        for (typed in queryWords) {
            total += titleWords.maxOf { wordScore(typed, it) }
        }
        return total / queryWords.size
    }

    private fun wordScore(typed: String, word: String): Float {
        if (word.startsWith(typed)) return 1f
        // Very short words must match exactly, otherwise a two-letter fragment would match almost any title.
        if (typed.length <= 2) return 0f
        val whole = 1f - editDistance(typed, word).toFloat() / max(typed.length, word.length)
        val prefix = 1f - editDistance(typed, word.take(typed.length)).toFloat() / typed.length
        return max(whole, prefix)
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** The items whose title is close enough, best match first. Equal scores keep their original order. */
    fun <T> rank(query: String, items: Collection<T>, title: (T) -> String): List<T> =
        items.map { it to score(query, title(it)) }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }
            .map { it.first }
}

/** Titles only, for callers that have [SAnime] values in hand. */
fun rankAnime(query: String, items: Collection<SAnime>): List<SAnime> =
    TitleMatcher.rank(query, items) { it.title }
