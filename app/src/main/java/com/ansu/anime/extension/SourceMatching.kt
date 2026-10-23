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
private const val SOURCE_TIMEOUT_MS = 8_000L

/** Title variants searched at the same time in each source; the rest only run when these find nothing good. */
private const val FIRST_STAGE_QUERIES = 2

/** A source that timed out or errored is skipped for this long, so one dead site does not slow every title. */
private const val FAILURE_COOLDOWN_MS = 10 * 60_000L

private val recentFailures = java.util.concurrent.ConcurrentHashMap<Long, Long>()

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

    /** Forgets every saved episode list, in memory and on disk; the next details page asks the sources again. */
    suspend fun clearAll() {
        map.clear()
        val folder = dir ?: return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            folder.listFiles()?.forEach { it.delete() }
        }
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

/**
 * Which page of each source was the right one for a title, remembered on disk. Reopening a title then needs
 * one episode-list request per source instead of a whole search (several title variants and up to three list
 * fetches each). An entry is dropped when its page no longer lists episodes.
 */
internal object SourceMatchMemory {
    class Remembered(val animeId: String, val title: String, val urlPath: String, val score: Double, val at: Long)

    private const val MAX_ENTRIES = 400
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    private val map = java.util.concurrent.ConcurrentHashMap<String, Remembered>()

    @Volatile
    private var file: java.io.File? = null

    @Volatile
    private var loaded = false

    /** Call once at start-up with a writable cache folder. Without it matches are only remembered in memory. */
    fun attach(cacheDir: java.io.File) {
        file = java.io.File(cacheDir, "source-matches.json")
    }

    private fun key(titleKey: String, sourceId: Long) = "$titleKey:$sourceId"

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val f = file?.takeIf { it.exists() } ?: return@runCatching
                val root = org.json.JSONObject(f.readText())
                val now = System.currentTimeMillis()
                root.keys().forEach { k ->
                    val o = root.getJSONObject(k)
                    val at = o.getLong("at")
                    if (now - at <= MAX_AGE_MS) {
                        map[k] = Remembered(o.getString("id"), o.getString("t"), o.getString("p"), o.getDouble("s"), at)
                    }
                }
            }
            loaded = true
        }
    }

    /** The first call reads the file, so it runs off the main thread. */
    private suspend fun load() {
        if (!loaded) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ensureLoaded() }
    }

    suspend fun get(titleKey: String, sourceId: Long): Remembered? {
        load()
        return map[key(titleKey, sourceId)]
    }

    suspend fun remember(titleKey: String, sourceId: Long, candidate: SAnime, score: Double) {
        val origin = candidate.origin as? com.ansu.anime.core.model.MediaOrigin.Extension ?: return
        load()
        map[key(titleKey, sourceId)] =
            Remembered(candidate.id, candidate.title, origin.urlPath, score, System.currentTimeMillis())
        persist()
    }

    suspend fun forget(titleKey: String, sourceId: Long) {
        load()
        if (map.remove(key(titleKey, sourceId)) != null) persist()
    }

    private suspend fun persist(): Unit = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val f = file ?: return@withContext
        runCatching {
            if (map.size > MAX_ENTRIES) {
                map.entries.sortedBy { it.value.at }.take(map.size - MAX_ENTRIES).forEach { map.remove(it.key) }
            }
            val root = org.json.JSONObject()
            map.forEach { (k, v) ->
                root.put(k, org.json.JSONObject().put("id", v.animeId).put("t", v.title).put("p", v.urlPath).put("s", v.score).put("at", v.at))
            }
            f.writeText(root.toString())
        }
        Unit
    }
}

private class SourceMatch(
    val source: AnimeCatalogueSource,
    val score: Double,
    val episodes: List<SEpisode>,
    /** The page that matched, set only when it really listed episodes; this is what gets remembered. */
    val candidate: SAnime? = null,
)

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
    /**
     * Alternate titles fetched while the search already runs. Sources that remember their page for this show
     * never wait for it; the others wait up to a second for it before searching.
     */
    extraTitlesProvider: (suspend () -> List<String>)? = null,
): List<SEpisode> = coroutineScope {
    // The extensions are scanned in the background at launch; waiting here means a title opened right after
    // the first screen still finds every installed source instead of only the built-in one.
    awaitLoaded()
    val all = allSources().filter { it.id != BUILT_IN_SOURCE_ID }
    if (all.isEmpty()) return@coroutineScope emptyList()
    val now = System.currentTimeMillis()
    // Skip sources that failed a moment ago, unless that would leave nothing to ask.
    val candidates = all.filter { now - (recentFailures[it.id] ?: 0L) > FAILURE_COOLDOWN_MS }.ifEmpty { all }
    val titleKey = (anime.anilistId ?: anime.id).toString()
    val extrasDeferred = extraTitlesProvider?.let { provider ->
        async { runCatching { withTimeoutOrNull(1_000L) { provider() } }.getOrNull().orEmpty() }
    }
    val matches = mutableListOf<SourceMatch>()
    candidates
        .map { source ->
            async {
                // "Nothing found" is a normal answer; only an error or a timeout counts against the source.
                var answered = false
                val match = try {
                    rememberedMatch(source, titleKey)?.also { answered = true } ?: withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        val extras = extrasDeferred?.await() ?: extraTitles
                        val allTitles = (listOf(anime.title) + extras).distinct()
                        val queries = allTitles.flatMap { titleQueries(it) }.distinct().take(5)
                        matchIn(source, allTitles, queries).also { answered = true }
                    }?.also { found ->
                        found.candidate?.let { SourceMatchMemory.remember(titleKey, source.id, it, found.score) }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    android.util.Log.w("SourceMatching", "${source.name}: ${e::class.simpleName}: ${e.message}")
                    null
                }
                if (answered) recentFailures.remove(source.id) else recentFailures[source.id] = System.currentTimeMillis()
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
 * The episode list of the page this source matched for the show last time, fetched directly with no search.
 * Null when nothing is remembered, the page now lists no episodes (it is forgotten) or the request failed or
 * timed out (it is kept, and the caller falls back to a normal search).
 */
private suspend fun rememberedMatch(source: AnimeCatalogueSource, titleKey: String): SourceMatch? {
    val saved = SourceMatchMemory.get(titleKey, source.id) ?: return null
    val page = SAnime(
        id = saved.animeId,
        title = saved.title,
        origin = com.ansu.anime.core.model.MediaOrigin.Extension(source.id, saved.urlPath),
    )
    val episodes = try {
        withTimeoutOrNull(SOURCE_TIMEOUT_MS) { source.getEpisodeList(page) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    } ?: return null
    if (episodes.isEmpty()) {
        SourceMatchMemory.forget(titleKey, source.id)
        return null
    }
    return SourceMatch(source, saved.score, episodes, page)
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

private class StageResult(val good: SourceMatch?, val results: List<QueryResult>)

/**
 * Searches one source in two stages. The first [FIRST_STAGE_QUERIES] title variants run at the same time and
 * the first well-matching title WITH episodes wins at once (the rest are cancelled). Only when none did, the
 * remaining variants run too; the best-scoring result is then used.
 */
private suspend fun matchIn(source: AnimeCatalogueSource, titles: List<String>, queries: List<String>): SourceMatch? {
    if (queries.isEmpty()) return null
    val first = searchStage(source, titles, queries.take(FIRST_STAGE_QUERIES))
    first.good?.let { return it }
    val rest = queries.drop(FIRST_STAGE_QUERIES)
    val second = if (rest.isEmpty()) null else searchStage(source, titles, rest)
    second?.good?.let { return it }
    val results = first.results + second?.results.orEmpty()
    return (results.filter { it.hasEpisodes }.maxByOrNull { it.match.score } ?: results.maxByOrNull { it.match.score })?.match
}

private suspend fun searchStage(source: AnimeCatalogueSource, titles: List<String>, queries: List<String>): StageResult = coroutineScope {
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
        winner.complete(null)
    }
    val good = winner.await()
    coroutineContext.cancelChildren()
    StageResult(good, synchronized(results) { results.toList() })
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
                return@coroutineScope QueryResult(SourceMatch(source, score, episodes, candidate), true)
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
