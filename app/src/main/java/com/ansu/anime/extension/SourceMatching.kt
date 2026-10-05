package com.ansu.anime.extension

import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.extension.api.AnimeCatalogueSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** Id of the built-in demo source, which AniList-only titles (trending, lists, search) point at until they are matched. */
internal const val BUILT_IN_SOURCE_ID = 1L

private const val MIN_MATCH_SCORE = 0.5
private const val SOURCE_TIMEOUT_MS = 12_000L

/** Process-wide episode list cache so reopening a title is instant (stale-while-revalidate). */
object EpisodeListCache {
    private class Entry(val episodes: List<SEpisode>, val at: Long)
    private val map = java.util.concurrent.ConcurrentHashMap<String, Entry>()
    fun get(key: String): List<SEpisode>? = map[key]?.episodes
    fun ageMs(key: String): Long = map[key]?.let { System.currentTimeMillis() - it.at } ?: Long.MAX_VALUE
    fun put(key: String, episodes: List<SEpisode>) { if (episodes.isNotEmpty()) map[key] = Entry(episodes, System.currentTimeMillis()) }
}

private class SourceMatch(val source: AnimeCatalogueSource, val score: Double, val episodes: List<SEpisode>)

/**
 * Looks [anime] up by title in EVERY switched-on installed source (in parallel) and returns one merged
 * episode list. Each episode carries the same-numbered episode from every other source that has the title
 * in [SEpisode.alternates], so the player can query all of them and list every stream.
 *
 * A source that fails, times out or finds nothing is skipped; it never hides the others.
 * Episode numbers are united across sources, so a source with a shorter list does not cut the others off.
 */
suspend fun ExtensionManager.findEpisodesByTitle(
    anime: SAnime,
    extraTitles: List<String> = emptyList(),
    onPartial: ((List<SEpisode>) -> Unit)? = null,
): List<SEpisode> = coroutineScope {
    val candidates = allSources().filter { it.id != BUILT_IN_SOURCE_ID }
    if (candidates.isEmpty()) return@coroutineScope emptyList()
    val allTitles = (listOf(anime.title) + extraTitles).distinct()
    val queries = allTitles.flatMap { titleQueries(it) }.distinct().take(5)
    val matches = mutableListOf<SourceMatch>()
    candidates
        .map { source ->
            async {
                val match = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                    try {
                        matchIn(source, allTitles, queries)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        android.util.Log.w("SourceMatching", "${source.name}: ${e::class.simpleName}: ${e.message}")
                        null
                    }
                }
                // Publish as each source finishes so the fastest one shows episodes straight away.
                if (match != null) synchronized(matches) {
                    matches += match
                    onPartial?.invoke(mergeMatches(matches.sortedByDescending { it.score }))
                }
                Unit
            }
        }
        .awaitAll()
    synchronized(matches) { mergeMatches(matches.sortedByDescending { it.score }) }
}

/**
 * Same as [findEpisodesByTitle] but also guarantees [origin] (the source the title was opened from) is
 * included even when a title search in that source would not find it again.
 */
suspend fun ExtensionManager.findEpisodesAcrossSources(
    anime: SAnime,
    originSourceId: Long,
    originEpisodes: List<SEpisode>,
    extraTitles: List<String> = emptyList(),
): List<SEpisode> {
    val found = runCatching { findEpisodesByTitle(anime, extraTitles) }.getOrDefault(emptyList())
    return foldOriginEpisodes(found, originSourceId, originEpisodes)
}

/** Folds the origin source's own episodes into [found] (the merged cross-source list) as alternates. */
fun foldOriginEpisodes(found: List<SEpisode>, originSourceId: Long, originEpisodes: List<SEpisode>): List<SEpisode> {
    if (originEpisodes.isEmpty()) return found
    if (found.isEmpty()) return originEpisodes.map { it.copy(sourceId = it.sourceId ?: originSourceId) }
    // Fold the origin's own episodes into the merged list as alternates where numbers line up.
    val tagged = originEpisodes.map { it.copy(sourceId = it.sourceId ?: originSourceId) }
    val alreadyHas = found.any { ep -> (listOf(ep) + ep.alternates).any { it.sourceId == originSourceId } }
    if (alreadyHas) return found
    return found.map { ep ->
        val extra = tagged.firstOrNull { it.episodeNumber == ep.episodeNumber }
        if (extra == null) ep else ep.copy(alternates = ep.alternates + extra)
    }
}

private fun mergeMatches(matches: List<SourceMatch>): List<SEpisode> {
    if (matches.isEmpty()) return emptyList()
    val byNumber = sortedMapOf<Float, MutableList<SEpisode>>()
    // Highest scoring source first so its name/thumbnail wins as the primary.
    for (match in matches) {
        for (episode in match.episodes) {
            val tagged = episode.copy(sourceId = match.source.id, alternates = emptyList())
            val list = byNumber.getOrPut(tagged.episodeNumber) { mutableListOf() }
            if (list.none { it.sourceId == tagged.sourceId }) list += tagged
        }
    }
    return byNumber.values.map { group ->
        val primary = group.first()
        val alternates = group.drop(1)
        if (alternates.isEmpty()) primary else primary.copy(alternates = alternates)
    }
}

private suspend fun matchIn(source: AnimeCatalogueSource, titles: List<String>, queries: List<String>): SourceMatch? {
    var bestOverall: SourceMatch? = null
    for (query in queries) {
        val results = try {
            source.getSearchAnime(1, query, emptyList()).animes
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            continue
        }
        // Try the few best-looking results rather than only the top one; the top hit is often a
        // different season or a dub entry with no episodes.
        val ranked = results
            .map { it to titles.maxOf { t -> similarity(it.title, t) } }
            .filter { it.second >= MIN_MATCH_SCORE }
            .sortedByDescending { it.second }
            .take(3)
        for ((candidate, score) in ranked) {
            val episodes = try {
                source.getEpisodeList(candidate)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            // Movies / single-item entries: some sources return no episode list. Keep the entry playable.
            val usable = episodes.ifEmpty {
                listOf(SEpisode(id = (candidate.origin as? com.ansu.anime.core.model.MediaOrigin.Extension)?.urlPath ?: candidate.id, name = candidate.title, episodeNumber = 1f))
            }
            val match = SourceMatch(source, score, usable)
            if (bestOverall == null || score > bestOverall.score) bestOverall = match
            if (episodes.isNotEmpty()) return match
        }
    }
    return bestOverall
}

/** Title variants to search with: full title, pre-colon part, season/part suffixes removed, bracketed text removed. */
private fun titleQueries(title: String): List<String> {
    val base = title.trim()
    val noBrackets = base.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ").replace(Regex("\\s+"), " ").trim()
    val noSeason = noBrackets
        .replace(Regex("(?i)\\b(season|part|cour)\\s*\\d+\\b"), " ")
        .replace(Regex("(?i)\\b\\d+(st|nd|rd|th)\\s+season\\b"), " ")
        .replace(Regex("\\s+"), " ").trim()
    val beforeColon = base.substringBefore(':').trim()
    val firstWords = noSeason.split(' ').take(3).joinToString(" ")
    return listOf(base, noBrackets, noSeason, beforeColon, firstWords)
        .filter { it.length >= 2 }
        .distinct()
}

private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

private fun similarity(a: String, b: String): Double {
    val left = normalize(a)
    val right = normalize(b)
    if (left.isEmpty() || right.isEmpty()) return 0.0
    if (left == right) return 1.0
    val shorter = minOf(left.length, right.length)
    val longer = maxOf(left.length, right.length)
    if ((left.contains(right) || right.contains(left)) && shorter.toDouble() / longer >= 0.6) return 0.8
    val leftWords = a.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
    val rightWords = b.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
    val union = (leftWords + rightWords).size
    return if (union == 0) 0.0 else leftWords.intersect(rightWords).size.toDouble() / union
}
