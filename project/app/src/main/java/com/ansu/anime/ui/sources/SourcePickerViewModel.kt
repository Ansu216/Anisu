package com.ansu.anime.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.util.SelectionHolder
import com.ansu.anime.data.prefs.PlayerPrefs
import com.ansu.anime.extension.ExtensionManager
import com.ansu.anime.extension.findEpisodesByTitle
import com.ansu.anime.ui.player.PlayableSource
import com.ansu.anime.ui.player.qualityChip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** How long one extension may take to list its streams before the picker keeps what it already has. */
private const val LIST_TIMEOUT_MS = 25_000L

/**
 * Streams found for an episode, kept for a few minutes so coming back to the picker (or opening it again after
 * a back-press) shows them at once. Short, because stream links expire. The refresh button skips it.
 */
private object PickerCache {
    private const val TTL_MS = 3 * 60_000L
    private const val MAX_ENTRIES = 20

    private class Entry(val at: Long, val sources: List<PlayableSource>)

    private val entries = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    fun get(key: String): List<PlayableSource>? {
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() - entry.at > TTL_MS) {
            entries.remove(key)
            return null
        }
        return entry.sources
    }

    fun put(key: String, sources: List<PlayableSource>) {
        if (entries.size >= MAX_ENTRIES) {
            entries.entries.minByOrNull { it.value.at }?.let { entries.remove(it.key) }
        }
        entries[key] = Entry(System.currentTimeMillis(), sources)
    }
}

data class SourcePickerState(
    val anime: SAnime? = null,
    val episode: SEpisode? = null,
    val isLoading: Boolean = true,
    /** Every stream found so far, in the person's source-priority order. Grows while the extensions answer. */
    val sources: List<PlayableSource> = emptyList(),
    val error: String? = null,
    /** Stream url -> its size label ("845 MB", "~1.2 GB"), filled in as each stream is measured. */
    val sizes: Map<String, String> = emptyMap(),
)

/**
 * Asks every installed extension that has the chosen episode for its streams, all at once, and publishes each
 * stream as soon as its extension answers - a slow or broken extension never hides the others.
 */
class SourcePickerViewModel(
    private val extensionManager: ExtensionManager,
    private val selectionHolder: SelectionHolder,
    private val playerPrefs: PlayerPrefs,
    private val aniListRepository: AniListRepository,
    private val diagnostics: Diagnostics,
    streamClient: OkHttpClient,
) : ViewModel() {

    private val sizeProbe = StreamSizeProbe(streamClient)
    private val sizeGate = Semaphore(4)
    private val sizeRequested = HashSet<String>()

    private val _state = MutableStateFlow(
        SourcePickerState(anime = selectionHolder.currentAnime.value, episode = selectionHolder.currentEpisode.value),
    )
    val state: StateFlow<SourcePickerState> = _state

    private var job: Job? = null

    init {
        load(force = false)
    }

    /** The refresh button: forgets what was found and asks every extension again. */
    fun refresh() = load(force = true)

    private fun cacheKey(anime: SAnime, episode: SEpisode) = "${anime.anilistId ?: anime.id}:${episode.episodeNumber}"

    private fun load(force: Boolean) {
        val anime = _state.value.anime
        val episode = _state.value.episode
        if (anime == null || episode == null) {
            _state.value = _state.value.copy(isLoading = false, error = "Nothing to play")
            return
        }
        job?.cancel()
        val key = cacheKey(anime, episode)
        if (!force) {
            val cached = PickerCache.get(key)
            if (cached != null) {
                _state.value = _state.value.copy(isLoading = false, sources = cached, error = null)
                probeSizes(cached)
                return
            }
        }
        _state.value = _state.value.copy(isLoading = true, sources = emptyList(), error = null)
        job = viewModelScope.launch {
            val collected = mutableListOf<PlayableSource>()
            try {
                resolve(anime, episode) { batch ->
                    val fresh = batch.filter { b -> b.url.isBlank() || collected.none { it.url == b.url } }
                    if (fresh.isNotEmpty()) {
                        collected += fresh
                        // Same order as the player's own list: the person's source priority first.
                        collected.sortWith(compareBy<PlayableSource> { playerPrefs.rankOf(it.sourceId) })
                        _state.value = _state.value.copy(sources = collected.toList())
                        probeSizes(fresh)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                diagnostics.log(LogCategory.PLAYBACK, "Source picker failed: ${e::class.simpleName}: ${e.message}", e)
                if (collected.isEmpty()) {
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load sources")
                    return@launch
                }
            }
            if (collected.isNotEmpty()) PickerCache.put(key, collected.toList())
            _state.value = _state.value.copy(
                isLoading = false,
                error = if (collected.isEmpty()) "No playable sources found" else null,
            )
        }
    }

    /**
     * Streams are requested from the episode's own source straight away. The title search that finds the same
     * episode in the other sources runs alongside, and each source it finds starts listing its streams the moment
     * it answers - nothing waits for the slowest source to finish searching.
     */
    private suspend fun resolve(anime: SAnime, episode: SEpisode, onBatch: (List<PlayableSource>) -> Unit) {
        val origin = anime.origin as? MediaOrigin.Extension ?: return
        coroutineScope {
            val started = HashSet<Long>()
            // One extension is asked once, whichever way (own episode, alternate, title search) it turns up first.
            fun startSource(candidate: SEpisode) {
                val sourceId = candidate.sourceId ?: origin.sourceId
                if (!synchronized(started) { started.add(sourceId) }) return
                launch { listStreams(sourceId, candidate, onBatch) }
            }
            startSource(episode.copy(alternates = emptyList()))
            episode.alternates.forEach { startSource(it) }
            // An episode that came without its alternates is matched in every installed source meanwhile.
            if (episode.alternates.isEmpty() && episode.id.isNotBlank()) {
                launch {
                    try {
                        extensionManager.findEpisodesByTitle(
                            anime,
                            extraTitlesProvider = { anime.anilistId?.let { id -> aniListRepository.getAllTitles(id) }.orEmpty() },
                            onPartial = { merged ->
                                merged.firstOrNull { it.episodeNumber == episode.episodeNumber }?.let { match ->
                                    startSource(match.copy(alternates = emptyList()))
                                    match.alternates.forEach { startSource(it) }
                                }
                            },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        diagnostics.log(LogCategory.PLAYBACK, "Source picker: title search failed: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun listStreams(sourceId: Long, candidate: SEpisode, onBatch: (List<PlayableSource>) -> Unit) {
        val source = extensionManager.getSource(sourceId) ?: return
        try {
            withTimeout(LIST_TIMEOUT_MS) {
                source.streamVideos(candidate, { line -> diagnostics.log(LogCategory.PLAYBACK, line) }) { videos ->
                    val mapped = videos.map { video ->
                        PlayableSource(
                            label = if (video.sourceLabel.isBlank() || video.quality.contains(video.sourceLabel, true)) {
                                video.quality
                            } else {
                                "${video.sourceLabel} \u00b7 ${video.quality}"
                            },
                            url = video.url,
                            headers = video.headers,
                            subtitles = video.subtitleTracks,
                            audioTracks = video.audioTracks,
                            resolve = video.resolve?.let { pending -> suspend { pending()?.toPlayableSource() } },
                            sourceId = source.id,
                            extensionName = source.name,
                            serverName = video.serverName,
                        )
                    }
                    if (mapped.isNotEmpty()) withContext(Dispatchers.Main) { onBatch(mapped) }
                }
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
        } catch (e: Throwable) {
            diagnostics.log(LogCategory.PLAYBACK, "  ${source.name}: ${e::class.simpleName}: ${e.message}", e)
        }
    }

    private fun com.ansu.anime.core.model.Video.toPlayableSource(): PlayableSource = PlayableSource(
        label = if (sourceLabel.isBlank() || quality.contains(sourceLabel, true)) quality else "$sourceLabel · $quality",
        url = url,
        headers = headers,
        subtitles = subtitleTracks,
        audioTracks = audioTracks,
        resolve = resolve?.let { pending -> suspend { pending()?.toPlayableSource() } },
        serverName = serverName,
    )

    /**
     * Measures every stream that already has a link (a stream the extension lists without resolving has none until it
     * is picked, so it shows no size), four at a time, and publishes each size as it arrives. Runs on the main
     * dispatcher between requests, so the state is only ever written from one thread.
     */
    private fun probeSizes(list: List<PlayableSource>) {
        list.forEach { source ->
            val url = source.url
            if (url.isBlank()) return@forEach
            sizeProbe.cached(url)?.let { size ->
                _state.update { it.copy(sizes = it.sizes + (url to size.label())) }
                return@forEach
            }
            if (!sizeRequested.add(url)) return@forEach
            val height = source.qualityChip()?.let { chip ->
                when (chip) { "4K" -> 2160; "2K" -> 1440; else -> chip.removeSuffix("p").toIntOrNull() }
            }
            viewModelScope.launch {
                val size = sizeGate.withPermit { sizeProbe.sizeOf(url, source.headers, height) }
                if (size != null) _state.update { it.copy(sizes = it.sizes + (url to size.label())) }
            }
        }
    }

    /** The person chose [source]: it is kept for this show's next episodes and handed to the player. */
    fun pick(source: PlayableSource) {
        val anime = _state.value.anime
        val sourceId = source.sourceId
        if (anime?.anilistId != null && sourceId != null) {
            playerPrefs.setManualPick(anime.anilistId, sourceId, source.serverName, source.label)
        }
        selectionHolder.pickedStream = SourcePick(sources = _state.value.sources, selected = source)
    }
}
