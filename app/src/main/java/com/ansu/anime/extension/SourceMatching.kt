package com.ansu.anime.extension

import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.extension.api.AnimeCatalogueSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** Id of the built-in demo source, which AniList-only titles (trending, lists, search) point at until they are matched. */
internal const val BUILT_IN_SOURCE_ID = 1L

private const val MIN_MATCH_SCORE = 0.5

/** A title this similar, with episodes, is accepted straight away without waiting for the other searches. */
private const val GOOD_MATCH_SCORE = 0.8
private const val SOURCE_TIMEOUT_MS = 12_000L

/**
 * Episode lists remembered in memory and on disk, so reopening a title (even after the app was closed)
 * shows the last known list at once while a fresh one loads in the background.
 */
object EpisodeListCache {
    private class Entry(val episodes: List<SEpisode>, val at: Long)

    private const val MAX_FILES = 80
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private val map = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    @Volatile
    private var dir: java.io.File? = null

    /** Call once at start-up with a writable cache folder. Without it the cache stays in memory only. */
    fun attach(cacheDir: java.io.File) {
        dir = java.io.File(cacheDir, "episode-lists").also { it.mkdirs() }
    }

    fun ageMs(key: String): Long = map[key]?.let { System.currentTimeMillis() - it.at } ?: Long.MAX_VALUE

    /** Memory first, then disk. */
    suspend fun get(key: String): List<SEpisode>? {
        map[key]?.let { return it.episodes }
        val folder = dir ?: return null
        val entry = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readEntry(folder, key) } ?: return null
        map[key] = entry
        return entry.episodes
    }

    suspend fun save(key: String, episodes: List<SEpisode>) {
        if (episodes.isEmpty()) return
        val entry = Entry(episodes, System.currentTimeMillis())
        map[key] = entry
        val folder = dir ?: return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val root = org.json.JSONObject()
                root.put("at", entry.at)
                root.put("eps", org.json.JSONArray().also { arr -> episodes.forEach { arr.put(episodeToJson(it)) } })
                fileFor(folder, key).writeText(root.toString())
                prune(folder)
            }
        }
    }

    private fun fileFor(folder: java.io.File, key: String): java.io.File {
        val safe = key.filter { it.isLetterOrDigit() || it == '-' }.take(40)
        return java.io.File(folder, "$safe-${key.hashCode()}.json")
    }

    private fun readEntry(folder: java.io.File, key: String): Entry? = runCatching {
        val file = fileFor(folder, key)
        if (!file.exists()) return@runCatching null
        val root = org.json.JSONObject(file.readText())
        val at = root.getLong("at")
        if (System.currentTimeMillis() - at > MAX_AGE_MS) {
            file.delete()
            return@runCatching null
        }
        val arr = root.getJSONArray("eps")
        val episodes = List(arr.length()) { episodeFromJson(arr.getJSONObject(it)) }
        if (episodes.isEmpty()) null else Entry(episodes, at)
    }.getOrNull()

    private fun prune(folder: java.io.File) {
        val files = folder.listFiles()?.sortedBy { it.lastModified() } ?: return
        val tooOld = files.filter { System.currentTimeMillis() - it.lastModified() > MAX_AGE_MS }
        tooOld.forEach { it.delete() }
        val rest = files - tooOld.toSet()
        if (rest.size > MAX_FILES) rest.take(rest.size - MAX_FILES).forEach { it.delete() }
    }

    private fun episodeToJson(e: SEpisode): org.json.JSONObject {
        val o = org.json.JSONObject()
        o.put("id", e.id)
        o.put("name", e.name)
        o.put("n", e.episodeNumber.toDouble())
        o.put("d", e.dateUpload)
        e.thumbnailUrl?.let { o.put("t", it) }
        e.description?.let { o.put("ds", it) }
        e.airDate?.let { o.put("a", it) }
        e.sourceId?.let { o.put("s", it) }
        if (e.alternates.isNotEmpty()) {
            o.put("alt", org.json.JSONArray().also { arr -> e.alternates.forEach { arr.put(episodeToJson(it)) } })
        }
        return o
    }

    private fun episodeFromJson(o: org.json.JSONObject): SEpisode = SEpisode(
        id = o.getString("id"),
        name = o.getString("name"),
        episodeNumber = o.getDouble("n").toFloat(),
        thumbnailUrl = if (o.has("t")) o.getString("t") else null,
        dateUpload = o.optLong("d", 0L),
        description = if (o.has("ds")) o.getString("ds") else null,
        airDate = if (o.has("a")) o.getString("a") else null,
        sourceId = if (o.has("s")) o.getLong("s") else null,
        alternates = o.optJSONArray("alt")?.let { arr -> List(arr.length()) { episodeFromJson(arr.getJSONObject(it)) } }.orEmpty(),
    )
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

private class QueryResult(val match: SourceMatch, val hasEpisodes: Boolean)

/**
 * Searches one source with every title variant at the same time. The first variant that finds a
 * well-matching title WITH episodes wins and the rest are cancelled; if none does, the best-scoring
 * result is used once all of them have finished.
 */
private suspend fun matchIn(source: AnimeCatalogueSource, titles: List<String>, queries: List<String>): SourceMatch? = coroutineScope {
    if (queries.isEmpty()) return@coroutineScope null
    val winner = kotlinx.coroutines.CompletableDeferred<SourceMatch?>()
    val results = mutableListOf<QueryResult>()
    val jobs = queries.map { query ->
        launch {
            val result = matchForQuery(source, titles, query)
            if (result != null) {
                synchronized(results) { results += result }
                if (result.hasEpisodes && result.match.score >= GOOD_MATCH_SCORE) winner.complete(result.match)
            }
        }
    }
    launch {
        jobs.joinAll()
        val best = synchronized(results) {
            results.filter { it.hasEpisodes }.maxByOrNull { it.match.score } ?: results.maxByOrNull { it.match.score }
        }
        winner.complete(best?.match)
    }
    val result = winner.await()
    coroutineContext.cancelChildren()
    result
}

private suspend fun matchForQuery(source: AnimeCatalogueSource, titles: List<String>, query: String): QueryResult? {
    val results = try {
        source.getSearchAnime(1, query, emptyList()).animes
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        return null
    }
    // Try the few best-looking results rather than only the top one; the top hit is often a
    // different season or a dub entry with no episodes.
    val ranked = results
        .map { it to titles.maxOf { t -> similarity(it.title, t) } }
        .filter { it.second >= MIN_MATCH_SCORE }
        .sortedByDescending { it.second }
        .take(3)
    if (ranked.isEmpty()) return null
    return coroutineScope {
        // Fetch all candidates' episode lists at once, but still prefer the best-scoring one that has episodes.
        val fetches = ranked.map { (candidate, _) ->
            async {
                try {
                    source.getEpisodeList(candidate)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    emptyList()
                }
            }
        }
        var fallback: SourceMatch? = null
        for ((index, fetch) in fetches.withIndex()) {
            val (candidate, score) = ranked[index]
            val episodes = fetch.await()
            if (episodes.isNotEmpty()) {
                fetches.forEach { it.cancel() }
                return@coroutineScope QueryResult(SourceMatch(source, score, episodes), true)
            }
            if (fallback == null) {
                // Movies / single-item entries: some sources return no episode list. Keep the entry playable.
                val single = SEpisode(
                    id = (candidate.origin as? com.ansu.anime.core.model.MediaOrigin.Extension)?.urlPath ?: candidate.id,
                    name = candidate.title,
                    episodeNumber = 1f,
                )
                fallback = SourceMatch(source, score, listOf(single))
            }
        }
        fallback?.let { QueryResult(it, false) }
    }
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
