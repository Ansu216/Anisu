package com.ansu.anime.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansu.anime.addon.AddonManager
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
import com.ansu.anime.extension.BUILT_IN_SOURCE_ID
import com.ansu.anime.extension.ExtensionManager
import com.ansu.anime.extension.findEpisodesAcrossSources
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val error: String? = null,
) {
    /** The heart to draw: AniList's when signed in, the on-device one otherwise. */
    val liked: Boolean get() = if (isLoggedIn) isFavourite else localFavourite

    /** The list to show as selected: AniList's when signed in, the on-device one otherwise. */
    val shownListStatus: String? get() = if (isLoggedIn) remoteListStatus else listStatus
}

class DetailsViewModel(
    private val extensionManager: ExtensionManager,
    private val addonManager: AddonManager,
    private val aniListRepository: AniListRepository,
    private val episodeMetadataRepository: EpisodeMetadataRepository,
    private val localListRepository: LocalListRepository,
    private val artworkRepository: ArtworkRepository,
    selectionHolder: SelectionHolder,
) : ViewModel() {

    val anime: StateFlow<SAnime?> = selectionHolder.currentAnime

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState

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
            viewModelScope.launch {
                val episodesDeferred = async { runCatching { loadEpisodes(current) }.getOrDefault(emptyList()) }
                val metaDeferred = async {
                    current.anilistId?.let { id -> episodeMetadataRepository.getEpisodeMeta(id) }.orEmpty()
                }
                val detailsDeferred = async {
                    current.anilistId?.let { id -> aniListRepository.getMediaDetails(id) }
                }
                val meta = metaDeferred.await()
                // Real sources occasionally repeat an episode or hand back a blank id. Compose keys the
                // episode list by that id, and a duplicate would crash the page as soon as it scrolled,
                // so give every episode a unique id and drop the repeats here.
                val loaded = episodesDeferred.await()
                val episodes = loaded.map { episode ->
                    val number = episode.episodeNumber.takeIf { it % 1f == 0f }?.toInt()
                    val info = number?.let { meta[it] } ?: return@map episode
                    episode.copy(
                        name = info.title ?: episode.name,
                        thumbnailUrl = episode.thumbnailUrl ?: info.thumbnailUrl,
                        description = info.overview ?: episode.description,
                        airDate = episode.airDate ?: info.airDate,
                    )
                }
                val details = try {
                    detailsDeferred.await()
                } catch (e: Exception) {
                    null
                }
                // Saved shows made before My Space showed year/format get them filled in here.
                if (details != null) {
                    runCatching { localListRepository.backfillDetails(details.id, details.format, details.year, details.title) }
                }
                val uniqueEpisodes = episodes
                    .mapIndexed { index, episode ->
                        if (episode.id.isBlank()) episode.copy(id = "${current.id}-ep-${episode.episodeNumber}-$index") else episode
                    }
                    .distinctBy { it.id }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    episodes = uniqueEpisodes,
                    aniListDetails = details,
                    isFavourite = details?.isFavourite ?: false,
                    remoteListStatus = details?.listStatus,
                    error = if (episodes.isEmpty() && details == null) "Couldn't load this title" else null,
                )
            }
        }
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

    private suspend fun loadEpisodes(anime: SAnime): List<SEpisode> = coroutineScope {
        when (val origin = anime.origin) {
            is MediaOrigin.Extension -> {
                // Search EVERY installed source for this title (not only for AniList-only ones), so the player
                // can offer streams from all of them. A title opened from a real source is folded in too.
                val ownEpisodes = if (origin.sourceId != BUILT_IN_SOURCE_ID) {
                    runCatching { extensionManager.getSource(origin.sourceId)?.getEpisodeList(anime).orEmpty() }
                        .getOrDefault(emptyList())
                        .map { it.copy(sourceId = it.sourceId ?: origin.sourceId) }
                } else {
                    emptyList()
                }
                val episodes = runCatching {
                    extensionManager.findEpisodesAcrossSources(
                        anime, origin.sourceId, ownEpisodes,
                        extraTitles = anime.anilistId?.let { aniListRepository.getAllTitles(it) }.orEmpty(),
                    )
                }.getOrDefault(emptyList()).ifEmpty { ownEpisodes }
                // Extensions may not list movies as episodes; create a synthetic root episode so playback
                // can query getVideoList(). The id is the anime's url, which real extensions use to fetch videos.
                if (episodes.isEmpty()) {
                    listOf(SEpisode(id = origin.urlPath, name = anime.title, episodeNumber = 1f))
                } else {
                    episodes
                }
            }

            is MediaOrigin.Addon -> {
                val addon = addonManager.installedAddons.first().firstOrNull { it.id == origin.addonId } ?: return@coroutineScope emptyList()
                val meta = addonManager.getMeta(addon, origin.type, origin.stremioId)
                val videos = meta?.videos.orEmpty()
                if (videos.isEmpty()) {
                    // A movie (or a catalog entry with no episode list) is one playable item whose video id is its own id.
                    listOf(SEpisode(id = origin.stremioId, name = meta?.name ?: anime.title, episodeNumber = 1f))
                } else {
                    videos.map { video ->
                        SEpisode(
                            id = video.id,
                            name = video.title ?: "Episode ${video.episode ?: "?"}",
                            episodeNumber = (video.episode ?: 0).toFloat(),
                            thumbnailUrl = video.thumbnail,
                        )
                    }
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
}
