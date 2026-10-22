package com.ansu.anime.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansu.anime.anilist.AniListMediaDetails
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.util.SelectionHolder
import com.ansu.anime.data.repository.AnimeArtwork
import com.ansu.anime.data.repository.ArtworkRepository
import com.ansu.anime.data.repository.EpisodeMetadataRepository
import com.ansu.anime.data.repository.LocalListRepository
import com.ansu.anime.data.repository.Trailer
import com.ansu.anime.data.repository.TrailerRepository
import com.ansu.anime.extension.BUILT_IN_SOURCE_ID
import com.ansu.anime.extension.ExtensionManager
import com.ansu.anime.extension.EpisodeListCache
import com.ansu.anime.extension.findEpisodesByTitle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren

data class DetailsUiState(
    val isLoading: Boolean = true,
    val episodes: List<SEpisode> = emptyList(),
    val aniListDetails: AniListMediaDetails? = null,
    /** Title logo and 16:9 backdrop for the hero; arrives independently of the episode list. */
    val artwork: AnimeArtwork = AnimeArtwork(),
    /** True once the artwork lookup has finished (or there is nothing to look up). */
    val artworkLoaded: Boolean = false,
    val isFavourite: Boolean = false,
    /** Local (signed-out) library state for this show. */
    val localFavourite: Boolean = false,
    val listStatus: String? = null,
    /** AniList list status for this show while signed in. */
    val remoteListStatus: String? = null,
    val isLoggedIn: Boolean = false,
    /** Promo videos from Jikan; the screen falls back to AniList's own trailer when this stays empty. */
    val trailers: List<Trailer> = emptyList(),
    val error: String? = null,
) {
    /** The heart to draw: AniList's when signed in, the on-device one otherwise. */
    val liked: Boolean get() = if (isLoggedIn) isFavourite else localFavourite

    /** The list to show as selected: AniList's when signed in, the on-device one otherwise. */
    val shownListStatus: String? get() = if (isLoggedIn) remoteListStatus else listStatus
}

class DetailsViewModel(
    private val extensionManager: ExtensionManager,
    private val aniListRepository: AniListRepository,
    private val episodeMetadataRepository: EpisodeMetadataRepository,
    private val localListRepository: LocalListRepository,
    private val artworkRepository: ArtworkRepository,
    private val trailerRepository: TrailerRepository,
    private val diagnostics: com.ansu.anime.core.diagnostics.Diagnostics,
    selectionHolder: SelectionHolder,
) : ViewModel() {

    val anime: StateFlow<SAnime?> = selectionHolder.currentAnime

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState

    // Declared before init{}: init launches coroutines that run right away (Main.immediate) and use these. Declared
    // below it they are still null at that point, and a cached episode list crashed the page on the second open.
    private var rawEpisodes: List<SEpisode> = emptyList()
    private var episodeMeta: Map<Int, com.ansu.anime.data.repository.EpisodeMeta> = emptyMap()

    init {
        val current = anime.value
        if (current == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "Nothing selected")
        } else {
            observeLocalLibrary(current)
            if (current.anilistId == null) _uiState.value = _uiState.value.copy(artworkLoaded = true)
            current.anilistId?.let { id ->
                viewModelScope.launch {
                    val artwork = artworkRepository.get(id)
                    _uiState.value = _uiState.value.copy(artwork = artwork, artworkLoaded = true)
                }
            }
            val openedAt = android.os.SystemClock.elapsedRealtime()
            // Each piece is published the moment it arrives instead of waiting for the slowest one (the
            // cross-source episode search), so the header, synopsis and stats show up straight away.
            viewModelScope.launch {
                current.anilistId?.let { id ->
                    episodeMeta = runCatching { episodeMetadataRepository.getEpisodeMeta(id) }.getOrDefault(emptyMap())
                    publishEpisodes(current)
                }
            }
            viewModelScope.launch {
                val details = current.anilistId?.let { id -> runCatching { aniListRepository.getMediaDetails(id) }.getOrNull() }
                // Saved shows made before My Space showed year/format get them filled in here.
                if (details != null) {
                    runCatching { localListRepository.backfillDetails(details.id, details.format, details.year, details.title) }
                }
                _uiState.value = _uiState.value.copy(
                    aniListDetails = details,
                    isFavourite = details?.isFavourite ?: false,
                    remoteListStatus = details?.listStatus,
                    error = if (_uiState.value.episodes.isEmpty() && !_uiState.value.isLoading && details == null) "Couldn't load this title" else _uiState.value.error,
                )
                // A show AniList knows lists AniList's own episodes: no source is searched to build the page.
                // Sources are asked only when an episode is played (the player matches them then); a quiet
                // warm-up below makes that match quick. When AniList cannot say how many episodes there are,
                // the sources' own lists are used as before.
                // The details answer also carries the MAL id and every title, so this page no longer spends a
                // second request on getMediaCore just to fill the trailers row or the title search.
                val malId = details?.malId
                    ?: current.anilistId?.let { id -> runCatching { aniListRepository.getMalId(id) }.getOrNull() }
                if (malId != null) {
                    val trailers = runCatching { trailerRepository.getAllTrailers(malId) }.getOrDefault(emptyList())
                    _uiState.value = _uiState.value.copy(trailers = trailers)
                }
                diagnostics.log(
                    com.ansu.anime.core.diagnostics.LogCategory.APP,
                    "Details \"${details?.title ?: current.title}\" opened in ${android.os.SystemClock.elapsedRealtime() - openedAt} ms — " +
                        aniListRepository.requestSummary(),
                )
                if (current.anilistId != null) {
                    val fromAniList = aniListEpisodes(current, details)
                    if (fromAniList.isNotEmpty()) {
                        rawEpisodes = fromAniList
                        publishEpisodes(current, finished = true)
                        warmSourceMatches(current, details?.titles.orEmpty())
                    } else {
                        loadSourceEpisodes(current)
                    }
                }
            }
            if (current.anilistId == null) viewModelScope.launch { loadSourceEpisodes(current) }
        }
    }

    /** One placeholder episode per episode AniList says exists (or has aired); titles and thumbnails are filled in by [publishEpisodes]. */
    private fun aniListEpisodes(current: SAnime, details: com.ansu.anime.anilist.AniListMediaDetails?): List<SEpisode> =
        aniListEpisodePlaceholders(current, details, episodeMeta)

    /** Quietly matches the installed sources in the background, so playing an episode finds its streams quickly. */
    private fun warmSourceMatches(current: SAnime, titles: List<String>) {
        viewModelScope.launch {
            runCatching {
                extensionManager.findEpisodesByTitle(
                    current,
                    // The titles came with the details answer; asking for them again was a second AniList request.
                    extraTitlesProvider = { titles },
                )
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }

    /** The sources' own episode lists (shows AniList does not know, or has no episode count for), cached on disk. */
    private suspend fun loadSourceEpisodes(current: SAnime) {
        val cacheKey = "ep-${current.anilistId ?: current.id}"
        // Show the last known list instantly (memory, then disk); re-fetch when it is older than 30 minutes.
        val cached = EpisodeListCache.get(cacheKey)
        if (cached != null) {
            rawEpisodes = cached
            publishEpisodes(current, finished = true)
            if (EpisodeListCache.ageMs(cacheKey) < 30 * 60_000L) return
        }
        runCatching {
            loadEpisodes(current) { partial ->
                // With a cached list on screen, wait for the full result so the list never shrinks mid-refresh.
                if (cached == null) {
                    rawEpisodes = partial
                    publishEpisodes(current, finished = false)
                }
            }
        }
            .onSuccess {
                rawEpisodes = it
                // A placeholder "episode 1" (nothing found) is not worth remembering.
                if (it.any { episode -> episode.sourceId != null }) EpisodeListCache.save(cacheKey, it)
            }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        publishEpisodes(current, finished = true)
    }

    /** Applies AniList episode info to the source episodes and shows the result. */
    private fun publishEpisodes(current: SAnime, finished: Boolean? = null) {
        val episodes = rawEpisodes.map { episode ->
            val number = episode.episodeNumber.takeIf { it % 1f == 0f }?.toInt()
            val info = number?.let { episodeMeta[it] } ?: return@map episode
            episode.copy(
                name = info.title ?: episode.name,
                thumbnailUrl = episode.thumbnailUrl ?: info.thumbnailUrl,
                description = info.overview ?: episode.description,
                airDate = episode.airDate ?: info.airDate,
            )
        }
        // Real sources occasionally repeat an episode or hand back a blank id. Compose keys the
        // episode list by that id, and a duplicate would crash the page as soon as it scrolled,
        // so give every episode a unique id and drop the repeats here.
        val uniqueEpisodes = episodes
            .mapIndexed { index, episode ->
                if (episode.id.isBlank()) episode.copy(id = "${current.id}-ep-${episode.episodeNumber}-$index") else episode
            }
            .distinctBy { it.id }
        val state = _uiState.value
        val stillLoading = if (finished == true) false else state.isLoading && uniqueEpisodes.isEmpty()
        _uiState.value = state.copy(
            isLoading = stillLoading,
            episodes = uniqueEpisodes,
            error = if (finished == true && uniqueEpisodes.isEmpty() && state.aniListDetails == null) "Couldn't load this title" else state.error,
        )
    }

    private fun observeLocalLibrary(current: SAnime) {
        viewModelScope.launch {
            aniListRepository.isLoggedIn.collect { loggedIn ->
                _uiState.value = _uiState.value.copy(isLoggedIn = loggedIn)
            }
        }
        val id = current.anilistId ?: return
        viewModelScope.launch {
            localListRepository.observe(id).collect { row ->
                _uiState.value = _uiState.value.copy(
                    localFavourite = row?.isFavourite ?: false,
                    listStatus = row?.status,
                )
            }
        }
    }

    private suspend fun loadEpisodes(anime: SAnime, onPartial: (List<SEpisode>) -> Unit): List<SEpisode> = coroutineScope {
        // Extensions are scanned in the background at launch, so a page opened immediately would otherwise find
        // no source at all (the built-in one is the only source registered before the scan finishes).
        extensionManager.awaitLoaded()
        when (val origin = anime.origin) {
            is MediaOrigin.Extension -> {
                // Search EVERY installed source for this title (not only for AniList-only ones), so the player
                // can offer streams from all of them. A title opened from a real source is folded in too.
                // The origin source's own list and the search across all sources run side by side; the own
                // list is shown as soon as it is in, and the merged list replaces it when the search ends.
                val ownDeferred = async {
                    if (origin.sourceId != BUILT_IN_SOURCE_ID) {
                        runCatching { extensionManager.getSource(origin.sourceId)?.getEpisodeList(anime).orEmpty() }
                            .getOrDefault(emptyList())
                            .map { it.copy(sourceId = it.sourceId ?: origin.sourceId) }
                    } else {
                        emptyList()
                    }
                }
                var ownSoFar: List<SEpisode> = emptyList()
                val foundDeferred = async {
                    runCatching {
                        // Alternate titles only improve matching: they are fetched while the search already runs, and
                        // a source that remembers its page for this show never waits for them.
                        extensionManager.findEpisodesByTitle(
                            anime,
                            onPartial = { partial ->
                                onPartial(com.ansu.anime.extension.foldOriginEpisodes(partial, origin.sourceId, ownSoFar))
                            },
                            extraTitlesProvider = { anime.anilistId?.let { id -> aniListRepository.getAllTitles(id) }.orEmpty() },
                        )
                    }.getOrDefault(emptyList())
                }
                val ownEpisodes = ownDeferred.await()
                ownSoFar = ownEpisodes
                if (ownEpisodes.isNotEmpty()) onPartial(ownEpisodes)
                val episodes = com.ansu.anime.extension.foldOriginEpisodes(foundDeferred.await(), origin.sourceId, ownEpisodes)
                    .ifEmpty { ownEpisodes }
                // Extensions may not list movies as episodes; create a synthetic root episode so playback
                // can query getVideoList(). The id is the anime's url, which real extensions use to fetch videos.
                if (episodes.isEmpty()) {
                    listOf(SEpisode(id = origin.urlPath, name = anime.title, episodeNumber = 1f))
                } else {
                    episodes
                }
            }
        }
    }

    fun toggleFavourite() {
        val id = anime.value?.anilistId ?: return
        if (!aniListRepository.isLoggedIn.value) {
            // Signed out: the heart lives on the device (the row flow above updates the UI).
            val current = anime.value ?: return
            viewModelScope.launch {
                localListRepository.toggleFavourite(current, _uiState.value.aniListDetails?.episodes, _uiState.value.aniListDetails?.format)
            }
            return
        }
        val optimistic = !_uiState.value.isFavourite
        _uiState.value = _uiState.value.copy(isFavourite = optimistic)
        viewModelScope.launch {
            val success = aniListRepository.toggleFavourite(id)
            if (!success) {
                // Revert if the call failed (e.g. not logged in) rather than leave a false heart state.
                _uiState.value = _uiState.value.copy(isFavourite = !optimistic)
            }
        }
    }

    /** Puts the show in a list (on AniList when signed in, on the device otherwise), or removes it when [status] is null. */
    fun setListStatus(status: String?) {
        val current = anime.value ?: return
        val id = current.anilistId ?: return
        if (!aniListRepository.isLoggedIn.value) {
            viewModelScope.launch {
                localListRepository.setStatus(current, status, _uiState.value.aniListDetails?.episodes, _uiState.value.aniListDetails?.format)
            }
            return
        }
        val previous = _uiState.value.remoteListStatus
        _uiState.value = _uiState.value.copy(remoteListStatus = status)
        viewModelScope.launch {
            if (!aniListRepository.setListStatus(id, status)) {
                // Revert rather than show a list the account does not actually have.
                _uiState.value = _uiState.value.copy(remoteListStatus = previous)
            }
        }
    }

    /**
     * CRITICAL: Clean up resources when ViewModel is destroyed
     * This prevents crashes from accessing stale caches when re-entering the same details screen
     */
    override fun onCleared() {
        super.onCleared()
        
        try {
            // Clear all cached data
            rawEpisodes = emptyList()
            episodeMeta = emptyMap()
            
            // Reset state
            _uiState.value = DetailsUiState(
                isLoading = false,
                error = null
            )
        } catch (e: Exception) {
            // Ignore if fields don't exist or cause issues
        }
        
        // Cancel all coroutines in this scope to prevent memory leaks
        viewModelScope.coroutineContext.cancelChildren()
    }
}


/**
 * One placeholder episode per episode AniList says exists (or has aired). Shared by the details page and by
 * the player, which builds the same list when it is opened straight from Continue Watching and no details
 * page has handed it one.
 */
internal fun aniListEpisodePlaceholders(
    current: SAnime,
    details: AniListMediaDetails?,
    episodeMeta: Map<Int, com.ansu.anime.data.repository.EpisodeMeta>,
): List<SEpisode> {
    val format = details?.format ?: current.format
    val total = details?.episodes ?: current.episodes
    val aired = details?.nextAiringEpisode?.let { it - 1 }
    val count = when {
        format.equals("MOVIE", ignoreCase = true) -> 1
        aired != null -> if (total != null) minOf(aired, total) else aired
        details?.status == "NOT_YET_RELEASED" -> 0
        total != null -> total
        else -> episodeMeta.keys.maxOrNull() ?: 0
    }
    return (1..count).map { number ->
        SEpisode(id = "${current.id}-al-$number", name = "Episode $number", episodeNumber = number.toFloat())
    }
}
