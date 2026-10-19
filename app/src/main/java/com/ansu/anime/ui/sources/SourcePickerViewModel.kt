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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** How long one extension may take to list its streams before the picker keeps what it already has. */
private const val LIST_TIMEOUT_MS = 60_000L

data class SourcePickerState(
    val anime: SAnime? = null,
    val episode: SEpisode? = null,
    val isLoading: Boolean = true,
    /** Every stream found so far, in the person's source-priority order. Grows while the extensions answer. */
    val sources: List<PlayableSource> = emptyList(),
    val error: String? = null,
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
) : ViewModel() {

    private val _state = MutableStateFlow(
        SourcePickerState(anime = selectionHolder.currentAnime.value, episode = selectionHolder.currentEpisode.value),
    )
    val state: StateFlow<SourcePickerState> = _state

    private var job: Job? = null

    init {
        load()
    }

    /** The refresh button: forgets what was found and asks every extension again. */
    fun refresh() = load()

    private fun load() {
        val anime = _state.value.anime
        val episode = _state.value.episode
        if (anime == null || episode == null) {
            _state.value = _state.value.copy(isLoading = false, error = "Nothing to play")
            return
        }
        job?.cancel()
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
            _state.value = _state.value.copy(
                isLoading = false,
                error = if (collected.isEmpty()) "No playable sources found" else null,
            )
        }
    }

    private suspend fun resolve(anime: SAnime, episode: SEpisode, onBatch: (List<PlayableSource>) -> Unit) {
        val origin = anime.origin as? MediaOrigin.Extension ?: return
        // An episode that came without its alternates is matched in every installed source first.
        val enriched = if (episode.alternates.isEmpty() && episode.id.isNotBlank()) {
            try {
                extensionManager.findEpisodesByTitle(
                    anime,
                    extraTitlesProvider = { anime.anilistId?.let { id -> aniListRepository.getAllTitles(id) }.orEmpty() },
                ).firstOrNull { it.episodeNumber == episode.episodeNumber } ?: episode
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                episode
            }
        } else {
            episode
        }
        val candidates = (listOf(enriched.copy(alternates = emptyList())) + enriched.alternates)
            .distinctBy { it.sourceId ?: origin.sourceId }
        coroutineScope {
            candidates.map { candidate ->
                async {
                    val source = extensionManager.getSource(candidate.sourceId ?: origin.sourceId) ?: return@async
                    try {
                        withTimeout(LIST_TIMEOUT_MS) {
                            source.streamVideos(candidate, { line -> diagnostics.log(LogCategory.PLAYBACK, line) }) { videos ->
                                val mapped = videos.map { video ->
                                    PlayableSource(
                                        label = if (video.sourceLabel.isBlank() || video.quality.contains(video.sourceLabel, true)) {
                                            video.quality
                                        } else {
                                            "${video.sourceLabel} · ${video.quality}"
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
            }.awaitAll()
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
