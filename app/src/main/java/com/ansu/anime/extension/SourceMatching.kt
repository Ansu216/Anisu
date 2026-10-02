package com.ansu.anime.extension

import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.extension.api.AnimeCatalogueSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

/** Id of the built-in demo source, which AniList-only titles (trending, lists, search) point at until they are matched. */
internal const val BUILT_IN_SOURCE_ID = 1L

private const val MIN_MATCH_SCORE = 0.6
private const val SOURCE_TIMEOUT_MS = 25_000L

private class SourceMatch(val score: Double, val episodes: List<SEpisode>)

/**
 * Looks [anime] up by title in every switched-on installed source and returns the episodes of the
 * best match (each episode records which source it came from). Empty when nothing matches well enough.
 */
suspend fun ExtensionManager.findEpisodesByTitle(anime: SAnime): List<SEpisode> = coroutineScope {
    val candidates = allSources().filter { it.id != BUILT_IN_SOURCE_ID }
    if (candidates.isEmpty()) return@coroutineScope emptyList()
    val queries = listOf(anime.title, anime.title.substringBefore(':').trim())
        .filter { it.length >= 2 }
        .distinct()
    val matches = candidates
        .map { source -> async { runCatching { withTimeout(SOURCE_TIMEOUT_MS) { matchIn(source, anime.title, queries) } }.getOrNull() } }
        .awaitAll()
        .filterNotNull()
        .sortedByDescending { it.score }
    val primary = matches.firstOrNull() ?: return@coroutineScope emptyList()
    val others = matches.drop(1)
    // Attach the same-numbered episode from every other matching source, so one source lacking
    // (or failing on) a title no longer hides the sources that have it.
    primary.episodes.map { episode ->
        val alternates = others.mapNotNull { match ->
            match.episodes.firstOrNull { it.episodeNumber == episode.episodeNumber }
        }
        if (alternates.isEmpty()) episode else episode.copy(alternates = alternates)
    }
}

private suspend fun matchIn(source: AnimeCatalogueSource, title: String, queries: List<String>): SourceMatch? {
    for (query in queries) {
        val results = source.getSearchAnime(1, query, emptyList()).animes
        val best = results.maxByOrNull { similarity(it.title, title) } ?: continue
        val score = similarity(best.title, title)
        if (score < MIN_MATCH_SCORE) continue
        val episodes = source.getEpisodeList(best)
        if (episodes.isNotEmpty()) return SourceMatch(score, episodes)
    }
    return null
}

private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

private fun similarity(a: String, b: String): Double {
    val left = normalize(a)
    val right = normalize(b)
    if (left.isEmpty() || right.isEmpty()) return 0.0
    if (left == right) return 1.0
    val shorter = minOf(left.length, right.length)
    val longer = maxOf(left.length, right.length)
    if ((left.contains(right) || right.contains(left)) && shorter.toDouble() / longer >= 0.7) return 0.8
    val leftWords = a.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
    val rightWords = b.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
    val union = (leftWords + rightWords).size
    return if (union == 0) 0.0 else leftWords.intersect(rightWords).size.toDouble() / union
}
