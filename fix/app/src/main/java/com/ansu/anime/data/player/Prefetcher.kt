package com.ansu.anime.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.model.Video
import com.ansu.anime.data.prefs.PlayerPrefs
import com.ansu.anime.data.repository.ContinueWatchingRepository
import com.ansu.anime.extension.ExtensionManager
import com.ansu.anime.extension.findEpisodesByTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Warms up the show the person is most likely to resume next, as soon as the app opens.
 *
 * It resolves that show's streams through its own source and then buffers about a minute from where
 * the person left off into the shared [MediaCache], through a silent player instance. When they open
 * that episode, the player reads the warmed part from the cache instead of the network, so playback
 * starts already filled. It runs only when the "Pre-load" switch is on, one show at a time, and
 * releases its temporary player as soon as the target is buffered. Every failure is swallowed and
 * logged: a warm-up that cannot run must never affect the app.
 */
@OptIn(UnstableApi::class)
class Prefetcher(
    private val context: Context,
    private val continueWatching: ContinueWatchingRepository,
    private val extensionManager: ExtensionManager,
    private val aniListRepository: AniListRepository?,
    private val mediaCache: MediaCache,
    private val playerPrefs: PlayerPrefs,
    private val diagnostics: Diagnostics?,
    private val streamClient: okhttp3.OkHttpClient? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var running = false

    /** Starts the warm-up if the switch is on and a run is not already in flight. */
    fun prefetchOnLaunch() {
        if (running || !playerPrefs.prefetchEnabled.value) return
        running = true
        scope.launch {
            try {
                // Home's first requests and the start-up animation own the first seconds. Warming used to start
                // with them, so a source search and a temporary ExoPlayer competed with the screen the person
                // was looking at; starting once the app has settled costs nothing and keeps the launch smooth.
                delay(START_DELAY_MS)
                warmUpLastWatched()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                diagnostics?.log(LogCategory.PLAYBACK, "Pre-load failed: ${e::class.simpleName}: ${e.message}")
            } finally {
                running = false
            }
        }
    }

    private suspend fun warmUpLastWatched() {
        // Resolving sources talks to the extensions and the network, so it stays off the main thread.
        val plan = withContext(Dispatchers.IO) { resolveResumeTarget() } ?: return
        withContext(Dispatchers.Main) { buffer(plan.first, plan.second) }
    }

    /** The stream to warm and the position to warm it from, or null when there is nothing to resume. */
    private suspend fun resolveResumeTarget(): Pair<Video, Long>? {
        // The extensions are scanned in the background at launch; wait for that before looking one up, or the
        // warm-up would find no source and silently do nothing on a cold start.
        extensionManager.awaitLoaded()
        val entry = continueWatching.entries.first().firstOrNull() ?: return null
        val sourceId = entry.originExtensionSourceId ?: return null
        val source = extensionManager.getSource(sourceId) ?: return null
        val anime = SAnime(
            id = entry.anilistId.toString(),
            title = entry.title,
            posterUrl = entry.posterUrl,
            bannerUrl = entry.bannerUrl,
            anilistId = entry.anilistId,
            origin = MediaOrigin.Extension(sourceId = sourceId, urlPath = entry.anilistId.toString()),
        )
        val wanted = SEpisode(
            id = entry.episodeId,
            name = entry.episodeName,
            episodeNumber = entry.episodeNumber,
            sourceId = sourceId,
        )
        val episodes = runCatching {
            extensionManager.findEpisodesByTitle(
                anime,
                extraTitlesProvider = { anime.anilistId?.let { id -> aniListRepository?.getAllTitles(id) }.orEmpty() },
            )
        }.getOrDefault(emptyList())
        val episode = episodes.firstOrNull { it.episodeNumber == entry.episodeNumber } ?: wanted

        // Warm the source this show last played from (kept 3 days), the one playback will start with.
        val remembered = continueWatching.lastSourceFor(entry.anilistId)
        val candidates = listOf(episode.copy(alternates = emptyList())) + episode.alternates
        val chosen = remembered?.let { r -> candidates.firstOrNull { (it.sourceId ?: sourceId) == r.sourceId } } ?: episode
        val chosenSource = extensionManager.getSource(chosen.sourceId ?: sourceId) ?: source

        var listed: Video? = null
        runCatching {
            chosenSource.streamVideos(chosen, {}) { videos -> if (listed == null) listed = videos.firstOrNull() }
        }
        val first = listed ?: return null
        // A source that lists the video without resolving it (Aniyomi library 16) has to be resolved now.
        val resolved = runCatching { first.resolve?.invoke() }.getOrNull() ?: first
        if (resolved.url.isBlank()) return null
        diagnostics?.log(LogCategory.PLAYBACK, "Pre-load: warming ${anime.title} E${entry.episodeNumber}")
        return resolved to entry.positionSeconds * 1000L
    }

    /** Buffers [TARGET_AHEAD_MS] of [video] into the cache through a silent, temporary player. */
    private suspend fun buffer(video: Video, startMs: Long) {
        val upstream = mediaCache.upstreamWithHeaders(video.headers)
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(mediaCache.dataSourceFactory(upstream)))
            .build()
        try {
            val item = MediaItem.Builder().setUri(video.url).build()
            player.setMediaItem(item, if (startMs > 5_000L) startMs else C.TIME_UNSET)
            // Silent: it only loads, it never plays or makes a sound.
            player.playWhenReady = false
            player.prepare()
            withTimeoutOrNull(TIMEOUT_MS) {
                while (player.bufferedPosition - player.currentPosition < TARGET_AHEAD_MS) {
                    if (player.playbackState == Player.STATE_ENDED || player.playerError != null) break
                    delay(500L)
                }
            }
            val ahead = ((player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)) / 1000L
            diagnostics?.log(LogCategory.PLAYBACK, "Pre-load: buffered ${ahead}s ahead")
        } finally {
            player.release()
        }
    }

    private companion object {
        /** Wait for the launch and the first screen before the warm-up starts. */
        const val START_DELAY_MS = 4_000L
        /** How far ahead to warm; the person gets roughly a minute of playback from the cache. */
        const val TARGET_AHEAD_MS = 60_000L
        /** A cap so a slow or dead source cannot keep the temporary player alive forever. */
        const val TIMEOUT_MS = 120_000L
    }
}
