package com.ansu.anime.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.model.SubtitleTrack
import com.ansu.anime.core.model.Video
import com.ansu.anime.data.prefs.PlayerPrefs
import com.ansu.anime.extension.ExtensionManager
import com.ansu.anime.extension.findEpisodesByTitle
import com.ansu.anime.ui.player.PlayableSource
import com.ansu.anime.ui.player.kindChip
import com.ansu.anime.ui.player.pickStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Episode downloads for offline viewing, kept on the device.
 *
 * An episode is downloaded in four steps: the best stream is found the way the player would pick it (the
 * Preferred stream settings and the source priority), every subtitle file the source offers is saved next to
 * it, the stream is downloaded by Media3's download manager (an HLS or DASH stream keeps one video quality and
 * every audio and subtitle track it carries), and the show and episode are remembered so the Downloads screen
 * can list and play it without a connection.
 */
@OptIn(UnstableApi::class)
class DownloadsRepository(
    context: Context,
    private val extensionManager: ExtensionManager,
    private val aniListRepository: AniListRepository,
    private val playerPrefs: PlayerPrefs,
    private val okHttpClient: OkHttpClient,
    private val diagnostics: Diagnostics?,
    /** The shared streaming client (HTTP/2, pooled connections); the stock HTTP source is used when it is null. */
    private val streamClient: OkHttpClient? = null,
) {
    private val appContext = context.applicationContext

    /** For the small requests made while preparing (stream type, subtitle files): each call ends by itself after 15 s. */
    private val quickClient: OkHttpClient = okHttpClient.newBuilder().callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()

    /** For reading a stream's playlist directly: slow hosts get 30 s per try instead of 10. */
    private val patientClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = appContext.getSharedPreferences("ansu_downloads", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(DownloadEntry.serializer())

    private val entries = MutableStateFlow(loadEntries())
    private val preparing = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    /**
     * Where each Media3 download stands, as plain values. Media3 keeps one [Download] object per download and
     * updates its progress in place, so storing the objects made every poll look like "no change" to the flow
     * and the Downloads screen never moved. A fresh [DownloadStatus] is copied out each time instead.
     */
    private val media3States = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())

    /** Prepared downloads waiting for a free slot, in the order they will start. Only touched on the main thread. */
    private val waitingIds = MutableStateFlow<List<String>>(emptyList())
    private val pendingRequests = LinkedHashMap<String, DownloadRequest>()

    /** Downloads handed to Media3 that have not finished or failed yet: they are what holds the download slots. */
    private val startedIds = HashSet<String>()

    /** Notes on downloads that failed and are being tried again ("Retrying in 15 s (try 2 of 3)"). */
    private val retryNotes = MutableStateFlow<Map<String, String>>(emptyMap())

    private val userPaused = MutableStateFlow(prefs.getBoolean(KEY_PAUSED, false))
    private val batteryLow = MutableStateFlow(false)

    /** Whether the whole queue is on hold, and why: Pause all, or a low battery with that setting on. */
    val pauseState: StateFlow<PauseState> = combine(userPaused, batteryLow, playerPrefs.downloadPauseLowBattery) { user, low, guard ->
        when {
            user -> PauseState.User
            guard && low -> PauseState.Battery
            else -> PauseState.None
        }
    }.stateIn(scope, SharingStarted.Eagerly, if (userPaused.value) PauseState.User else PauseState.None)

    private val listedItems = combine(entries, preparing, media3States, waitingIds, retryNotes) { saved, prep, states, waiting, notes ->
        val all = saved.map { entry ->
            val place = waiting.indexOf(entry.id).let { if (it >= 0) it + 1 else 0 }
            val status = prep[entry.id] ?: when {
                place > 0 -> DownloadStatus(DownloadPhase.Queued, message = "Waiting in the queue · #$place", queuePosition = place)
                else -> {
                    val media = states[entry.id]
                    val note = notes[entry.id]
                    when {
                        media == null -> DownloadStatus(DownloadPhase.Queued)
                        media.phase == DownloadPhase.Failed && note != null -> DownloadStatus(DownloadPhase.Queued, media.percent, media.bytes, note)
                        media.phase == DownloadPhase.Failed -> media.copy(retryable = true)
                        else -> media
                    }
                }
            }
            DownloadItem(entry, if (status.phase == DownloadPhase.Failed) status.copy(retryable = true) else status)
        }
        // What is running or waiting comes first, in the order it will go; finished episodes follow, newest first.
        fun rank(item: DownloadItem): Int = when {
            item.status.phase == DownloadPhase.Downloading -> 0
            item.status.phase == DownloadPhase.Queued && item.status.queuePosition == 0 -> 1
            item.status.phase == DownloadPhase.Queued -> 1_000 + item.status.queuePosition
            item.status.phase == DownloadPhase.Preparing -> 100_000
            item.status.phase == DownloadPhase.Failed -> 200_000
            else -> 300_000
        }
        val (done, active) = all.partition { it.status.phase == DownloadPhase.Completed }
        active.sortedWith(compareBy({ rank(it) }, { it.entry.createdAt })) + done.sortedByDescending { it.entry.createdAt }
    }

    /** Every download with where it stands: running and waiting ones in queue order, then the finished ones. */
    val items: StateFlow<List<DownloadItem>> = combine(listedItems, pauseState) { list, pause ->
        if (pause == PauseState.None) {
            list
        } else {
            val why = if (pause == PauseState.Battery) "Paused: battery is low" else "Paused"
            list.map { item ->
                val phase = item.status.phase
                if (phase == DownloadPhase.Downloading || (phase == DownloadPhase.Queued && item.status.queuePosition == 0)) {
                    item.copy(status = item.status.copy(phase = DownloadPhase.Queued, message = why))
                } else {
                    item
                }
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // ---- storage -------------------------------------------------------------------------------------------

    private val databaseProvider = StandaloneDatabaseProvider(appContext)

    /** Downloaded bytes live here and are never evicted: only deleting a download frees them. */
    val cache: SimpleCache = SimpleCache(File(appContext.filesDir, "downloads/media"), NoOpCacheEvictor(), databaseProvider)

    /** Request headers each download's host needs (Referer, User-Agent...), added to every request to that host. */
    private val hostHeaders = ConcurrentHashMap<String, Map<String, String>>()

    @Volatile
    private var lastHeaders: Map<String, String> = emptyMap()

    private fun headersFor(host: String?): Map<String, String>? {
        if (host == null) return lastHeaders.takeIf { it.isNotEmpty() }
        hostHeaders[host]?.let { return it }
        // A CDN subdomain of the same site (cdn1.site.com for site.com) needs the same headers.
        val root = host.substringAfter('.', host)
        hostHeaders.entries.firstOrNull { it.key.substringAfter('.', it.key) == root }?.let { return it.value }
        return lastHeaders.takeIf { it.isNotEmpty() }
    }

    // DefaultDataSource (not the bare HTTP one) so local file:// URIs work too: a finished download's subtitle files
    // are read from app storage through this same factory, and an HTTP-only source silently failed to open them,
    // which is why a dubbed download played with no subtitles even though they had been saved.
    private val upstreamFactory: DataSource.Factory = ResolvingDataSource.Factory(
        androidx.media3.datasource.DefaultDataSource.Factory(
            appContext,
            // The shared OkHttp client: HTTP/2 and pooled connections make many small segment requests much cheaper.
            streamClient?.let { OkHttpDataSource.Factory(it) } ?: DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
        ),
        ResolvingDataSource.Resolver { spec ->
            val scheme = spec.uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return@Resolver spec
            val extra = headersFor(spec.uri.host)
            if (extra.isNullOrEmpty()) spec else spec.withAdditionalHeaders(extra)
        },
    )

    /** Reads downloaded bytes first and goes to the network only for what is missing. Used to download and to play. */
    val dataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** What went wrong with each failed download, as the Downloads screen words it. */
    private val failureMessages = ConcurrentHashMap<String, String>()

    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            val id = download.request.id
            if (download.state == Download.STATE_FAILED) {
                describeFailure(finalException)?.let { failureMessages[id] = it }
            } else {
                failureMessages.remove(id)
            }
            media3States.value = media3States.value + (id to download.toStatus())
            when (download.state) {
                Download.STATE_COMPLETED -> {
                    startedIds.remove(id)
                    autoAttempts.remove(id)
                    retryContexts.remove(id)
                    retryNotes.value = retryNotes.value - id
                }
                Download.STATE_FAILED -> {
                    startedIds.remove(id)
                    diagnostics?.log(LogCategory.PLAYBACK, "Download failed (${download.failureReason}): $id ${finalException?.message.orEmpty()}")
                    scheduleRetry(download, finalException)
                }
                else -> if (retryNotes.value.containsKey(id)) retryNotes.value = retryNotes.value - id
            }
            startProgressPolling()
            // A slot may have freed up: the next waiting download starts. Done from here (not through the service) so the
            // manager is never idle in between and the download service keeps running in the background.
            pump(fromListener = true)
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            media3States.value = media3States.value - download.request.id
            startedIds.remove(download.request.id)
            pump(fromListener = true)
        }
    }

    /** One download manager for the whole app; the [AnisuDownloadService] hosts it so downloads outlive the screen. */
    val downloadManager: DownloadManager by lazy {
        // The executor is what splits one episode into parallel parts: an HLS playlist's segments are fetched on
        // these threads, several at a time. With two threads (as before) an episode crawled at two segments at once.
        DownloadManager(appContext, databaseProvider, cache, dataSourceFactory, Executors.newFixedThreadPool(SEGMENT_THREADS)).also {
            // Request headers are looked up per host, so episodes from different sources can download side by side.
            it.maxParallelDownloads = playerPrefs.downloadParallel.value
            it.minRetryCount = MIN_RETRY_COUNT
            it.requirements = requirementsFor(playerPrefs.downloadWifiOnly.value)
            it.addListener(listener)
        }
    }

    private var pollJob: kotlinx.coroutines.Job? = null

    /** The manager only reports state changes, so progress is read from it once a second while something runs. */
    private fun startProgressPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                val active = downloadManager.currentDownloads
                if (active.isEmpty()) break
                media3States.value = media3States.value + active.associate { it.request.id to it.toStatus() }
                delay(500L)
            }
        }
    }

    init {
        // Pick up downloads from earlier sessions (finished ones included) and resume any that were interrupted.
        scope.launch {
            // The manager is created here, on the main thread, before anything touches it from another one.
            val manager = downloadManager
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val cursor = manager.downloadIndex.getDownloads()
                    cursor.use { c ->
                        buildList { while (c.moveToNext()) add(c.download) }
                    }
                }.getOrDefault(emptyList())
            }
            media3States.value = media3States.value + saved.associate { it.request.id to it.toStatus() }
            startedIds += saved.filter { it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_RESTARTING }.map { it.request.id }
            if (saved.any { it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING }) startProgressPolling()
        }
        watchSettings()
        watchBattery()
    }

    // ---- the public API ------------------------------------------------------------------------------------

    /**
     * Starts downloading [episode] of [anime]. Does nothing when it is already downloaded or in progress.
     * With [chosen] (a stream picked in the source picker) exactly that stream is downloaded; without it the stream
     * the player would start with is found and used.
     */
    fun enqueue(
        anime: SAnime,
        episode: SEpisode,
        chosen: PlayableSource? = null,
        siblings: List<PlayableSource> = emptyList(),
        /** Picture quality for this download only (360..1080, or [PlayerPrefs.QUALITY_ANY]); null follows the setting. */
        quality: Int? = null,
        /** [PlayerPrefs.LANGUAGE_SUB] or [PlayerPrefs.LANGUAGE_DUB] for this download only; null follows the Preferred language setting. */
        language: String? = null,
    ) {
        val anilistId = anime.anilistId ?: anime.id.toIntOrNull() ?: return
        val id = downloadKey(anilistId, episode.episodeNumber)
        val existing = items.value.firstOrNull { it.entry.id == id }
        if (existing != null && existing.status.phase != DownloadPhase.Failed) return
        if (existing != null) remove(id)

        // What a retry needs to ask the source for a fresh link later (kept while the app is open).
        retryContexts[id] = RetryContext(anime, episode, chosen, siblings, quality, language)
        autoAttempts.remove(id)

        val stub = DownloadEntry(
            id = id,
            anilistId = anilistId,
            animeTitle = anime.title,
            posterUrl = anime.posterUrl,
            bannerUrl = anime.bannerUrl,
            episodeId = episode.id,
            episodeName = episode.name,
            episodeNumber = episode.episodeNumber,
            createdAt = System.currentTimeMillis(),
        )
        saveEntry(stub)
        val startText = if (chosen != null) "Getting your chosen stream…" else "Finding the best stream…"
        setPreparing(id, DownloadStatus(DownloadPhase.Preparing, message = startText))
        DownloadNotifications.preparing(appContext, stub, startText)
        scope.launch {
            try {
                // Several episodes queued at once (Download next N) look their streams up two at a time, not all together.
                val prepared = withContext(Dispatchers.IO) {
                    prepareGate.withPermit {
                        prepare(stub, anime, episode, chosen, siblings, quality, language) { text ->
                            setPreparing(stub.id, DownloadStatus(DownloadPhase.Preparing, message = text))
                            DownloadNotifications.preparing(appContext, stub, text)
                        }
                    }
                }
                // Deleted while it was being prepared: nothing to start.
                if (entryFor(id) == null) {
                    DownloadNotifications.clearPreparing(appContext, id)
                    setPreparing(id, null)
                    return@launch
                }
                saveEntry(prepared.first)
                DownloadNotifications.clearPreparing(appContext, id)
                setPreparing(id, null)
                queue(prepared.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                diagnostics?.log(LogCategory.PLAYBACK, "Download of ${anime.title} E${episode.episodeNumber} failed to start: ${e::class.simpleName}: ${e.message}", e)
                DownloadNotifications.clearPreparing(appContext, id)
                DownloadNotifications.failed(appContext, stub, e.message)
                setPreparing(id, DownloadStatus(DownloadPhase.Failed, message = e.message ?: "Could not start the download", retryable = true))
            }
        }
    }

    /** Queues [episodes] of [anime] one after the other (the "Download next N" button). Episodes already saved are skipped. */
    fun enqueueMany(anime: SAnime, episodes: List<SEpisode>, quality: Int? = null, language: String? = null) {
        episodes.forEach { enqueue(anime, it, null, emptyList(), quality, language) }
    }

    // ---- the waiting queue ---------------------------------------------------------------------------------

    /** Puts a prepared download at the back of the queue; it starts when a download slot is free. */
    private fun queue(request: DownloadRequest) {
        pendingRequests[request.id] = request
        waitingIds.value = waitingIds.value.filter { it != request.id } + request.id
        pump(fromListener = false)
    }

    /** Puts a download at the front of the queue (a retry goes before everything that is still waiting). */
    private fun requeue(request: DownloadRequest) {
        startedIds.remove(request.id)
        pendingRequests[request.id] = request
        waitingIds.value = listOf(request.id) + waitingIds.value.filter { it != request.id }
        pump(fromListener = false)
    }

    /** Hands waiting downloads to the manager while there are free slots, and not at all while the queue is paused. */
    private fun pump(fromListener: Boolean) {
        if (pauseState.value != PauseState.None) return
        var slots = playerPrefs.downloadParallel.value - startedIds.size
        while (slots > 0 && waitingIds.value.isNotEmpty()) {
            val id = waitingIds.value.first()
            waitingIds.value = waitingIds.value.drop(1)
            val request = pendingRequests.remove(id) ?: continue
            startedIds += id
            if (fromListener || downloadManager.currentDownloads.isNotEmpty()) {
                // The service is already running (or just was): add straight to the manager.
                downloadManager.addDownload(request)
            } else {
                try {
                    DownloadService.sendAddDownload(appContext, AnisuDownloadService::class.java, request, false)
                } catch (e: Exception) {
                    // The service could not be started (app in the background): the manager still downloads while the app lives.
                    downloadManager.addDownload(request, Download.STOP_REASON_NONE)
                }
            }
            slots--
        }
        startProgressPolling()
    }

    /** Moves a waiting download one place earlier in the queue. */
    fun moveUp(id: String) {
        val list = waitingIds.value.toMutableList()
        val i = list.indexOf(id)
        if (i > 0) {
            java.util.Collections.swap(list, i, i - 1)
            waitingIds.value = list
        }
    }

    /** Moves a waiting download one place later in the queue. */
    fun moveDown(id: String) {
        val list = waitingIds.value.toMutableList()
        val i = list.indexOf(id)
        if (i >= 0 && i < list.lastIndex) {
            java.util.Collections.swap(list, i, i + 1)
            waitingIds.value = list
        }
    }

    /** Makes a waiting download the next one to start. */
    fun moveToTop(id: String) {
        if (id in waitingIds.value) waitingIds.value = listOf(id) + waitingIds.value.filter { it != id }
    }

    /** Pause all / Resume all. A low battery can hold the queue as well; see [pauseState]. */
    fun setPausedAll(paused: Boolean) {
        userPaused.value = paused
        prefs.edit().putBoolean(KEY_PAUSED, paused).apply()
    }

    private var appliedPause = false

    private fun applyPause() {
        val paused = pauseState.value != PauseState.None
        if (paused == appliedPause) return
        appliedPause = paused
        if (paused) {
            downloadManager.pauseDownloads()
        } else {
            try {
                DownloadService.sendResumeDownloads(appContext, AnisuDownloadService::class.java, false)
            } catch (e: Exception) {
                downloadManager.resumeDownloads()
            }
            pump(fromListener = false)
        }
    }

    private fun requirementsFor(wifiOnly: Boolean): Requirements =
        Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

    /** Applies the download settings now and whenever they change. */
    private fun watchSettings() {
        scope.launch { playerPrefs.downloadWifiOnly.collect { downloadManager.requirements = requirementsFor(it) } }
        scope.launch {
            playerPrefs.downloadParallel.collect {
                downloadManager.maxParallelDownloads = it
                pump(fromListener = false)
            }
        }
        scope.launch { pauseState.collect { applyPause() } }
    }

    /** Follows the battery level, so the "pause on low battery" setting can hold the queue. */
    private fun watchBattery() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = updateBattery(intent)
        }
        updateBattery(appContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
    }

    private fun updateBattery(intent: Intent?) {
        if (intent == null) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
        batteryLow.value = percent <= LOW_BATTERY_PERCENT && !charging
    }

    // ---- retrying ------------------------------------------------------------------------------------------

    private data class RetryContext(
        val anime: SAnime,
        val episode: SEpisode,
        val chosen: PlayableSource?,
        val siblings: List<PlayableSource>,
        val quality: Int?,
        val language: String? = null,
    )

    private val retryContexts = ConcurrentHashMap<String, RetryContext>()
    private val autoAttempts = ConcurrentHashMap<String, Int>()

    /** The HTTP status a failure came from, or null when it was not an HTTP error (timeout, reset connection...). */
    private fun httpCodeOf(error: Throwable?): Int? {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
            val next = cause.cause
            cause = if (next === cause) null else next
        }
        return null
    }

    /** True while a failed download is waiting for its next automatic try (the service then skips the failure notification). */
    fun isRetrying(id: String): Boolean = retryNotes.value.containsKey(id)

    /**
     * A download failed: it is tried again after a growing pause (5 s, 15 s, 45 s). A refused or missing link (401, 403,
     * 404, 410) is the usual sign of an expired address, so a fresh one is asked for from the source; a busy or broken
     * server (429, 5xx) and plain network trouble try the same link again, which keeps the part already saved.
     */
    private fun scheduleRetry(download: Download, error: Exception?) {
        val id = download.request.id
        if (entryFor(id) == null) return
        val tries = autoAttempts[id] ?: 0
        if (tries >= RETRY_BACKOFF_MS.size) {
            retryNotes.value = retryNotes.value - id
            return
        }
        val code = httpCodeOf(error)
        val freshLink = code == 401 || code == 403 || code == 404 || code == 410
        val waitMs = RETRY_BACKOFF_MS[tries]
        autoAttempts[id] = tries + 1
        retryNotes.value = retryNotes.value + (id to "Retrying in ${waitMs / 1000} s (try ${tries + 1} of ${RETRY_BACKOFF_MS.size})…")
        diagnostics?.log(LogCategory.PLAYBACK, "Download $id: retry ${tries + 1} in ${waitMs / 1000} s (${code?.let { "HTTP $it" } ?: error?.let { it::class.simpleName } ?: "unknown"}, ${if (freshLink) "fresh link" else "same link"})")
        scope.launch {
            delay(waitMs)
            if (entryFor(id) == null) return@launch
            if (freshLink && retryContexts.containsKey(id)) {
                retryNotes.value = retryNotes.value - id
                reResolve(id)
            } else {
                retryNotes.value = retryNotes.value - id
                requeue(download.request)
            }
        }
    }

    /** Asks the source for a fresh link to the same episode and queues it in front; what was already saved is kept when the path matches. */
    private fun reResolve(id: String) {
        val context = retryContexts[id] ?: return
        val entry = entryFor(id) ?: return
        setPreparing(id, DownloadStatus(DownloadPhase.Preparing, message = "Getting a fresh link…"))
        scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    prepareGate.withPermit {
                        prepare(entry, context.anime, context.episode, context.chosen, context.siblings, context.quality, context.language) { text ->
                            setPreparing(id, DownloadStatus(DownloadPhase.Preparing, message = text))
                        }
                    }
                }
                if (entryFor(id) == null) {
                    setPreparing(id, null)
                    return@launch
                }
                saveEntry(prepared.first)
                setPreparing(id, null)
                requeue(prepared.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                diagnostics?.log(LogCategory.PLAYBACK, "Download $id: could not get a fresh link: ${e::class.simpleName}: ${e.message}", e)
                setPreparing(id, DownloadStatus(DownloadPhase.Failed, message = e.message ?: "Could not get a fresh link", retryable = true))
            }
        }
    }

    /** The Retry button: starts a failed download again, asking the source for a fresh link when it can. */
    fun retry(id: String) {
        autoAttempts.remove(id)
        retryNotes.value = retryNotes.value - id
        if (retryContexts.containsKey(id)) {
            reResolve(id)
            return
        }
        // After the app was restarted the show is no longer known here, so the saved request is simply started again.
        scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { downloadManager.downloadIndex.getDownload(id) }.getOrNull() }
            if (saved != null) {
                requeue(saved.request)
            } else {
                setPreparing(id, DownloadStatus(DownloadPhase.Failed, message = "Delete this download and start it again from the episode.", retryable = false))
            }
        }
    }

    /** Deletes a download: its video, its subtitle files and its entry. */
    fun remove(id: String) {
        entries.value.firstOrNull { it.id == id }?.let { entry ->
            entry.subtitles.forEach { runCatching { File(it.path).delete() } }
            runCatching { File(appContext.filesDir, "downloads/subs/${safeName(id)}").deleteRecursively() }
        }
        runCatching { downloadManager.removeDownload(id) }
        media3States.value = media3States.value - id
        setPreparing(id, null)
        waitingIds.value = waitingIds.value.filter { it != id }
        pendingRequests.remove(id)
        startedIds.remove(id)
        retryContexts.remove(id)
        autoAttempts.remove(id)
        retryNotes.value = retryNotes.value - id
        entries.value = entries.value.filterNot { it.id == id }
        persist()
        pump(fromListener = false)
    }

    /** What the player needs to play a finished download: its stream (only the downloaded tracks) and local subtitles. */
    suspend fun offlineMediaItem(id: String): MediaItem? = withContext(Dispatchers.IO) {
        val entry = entries.value.firstOrNull { it.id == id } ?: return@withContext null
        val download = runCatching { downloadManager.downloadIndex.getDownload(id) }.getOrNull() ?: return@withContext null
        val subtitles = entry.subtitles.filter { File(it.path).exists() }.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(sub.path)))
                .setMimeType(subtitleMime(sub.path))
                .setLanguage(sub.lang)
                .setLabel(sub.lang)
                .build()
        }
        download.request.toMediaItem().buildUpon().setSubtitleConfigurations(subtitles).build()
    }

    /**
     * What plays a download: the saved video comes through the cache, while the saved subtitle files (local
     * `file:` addresses) are read straight from disk instead of being pushed through the cache layer as well.
     */
    val offlineDataSourceFactory: DataSource.Factory =
        androidx.media3.datasource.DefaultDataSource.Factory(appContext, dataSourceFactory)

    fun entryFor(id: String): DownloadEntry? = entries.value.firstOrNull { it.id == id }

    // ---- preparing a download ------------------------------------------------------------------------------

    /** At most two episodes look up their stream at a time. */
    private val prepareGate = Semaphore(2)

    /**
     * The quality a download is aimed at: the one chosen for this download, else the Downloads quality setting, else
     * the streaming quality. Returns a value from [PlayerPrefs.QUALITY_OPTIONS] (0 = Any).
     */
    private fun qualityFor(choice: Int?): Int =
        choice ?: playerPrefs.downloadQuality.value.takeIf { it > 0 } ?: playerPrefs.preferredQuality.value

    /** The tallest picture a download may keep; "Any" keeps up to 1080p, as before. */
    private fun heightFor(quality: Int): Int = quality.takeIf { it != PlayerPrefs.QUALITY_ANY } ?: 1080


    private suspend fun prepare(
        stub: DownloadEntry,
        anime: SAnime,
        episode: SEpisode,
        chosen: PlayableSource?,
        siblings: List<PlayableSource>,
        qualityChoice: Int?,
        languageChoice: String?,
        onStage: (String) -> Unit,
    ): Pair<DownloadEntry, DownloadRequest> {
        val quality = qualityFor(qualityChoice)
        val found = (if (chosen != null) resolveChosen(chosen) else findBestVideo(anime, episode, quality, languageChoice ?: playerPrefs.preferredLanguage.value)) ?: throw IOException(if (chosen != null) "The stream you picked did not respond. Try another source." else "No playable source found for this episode")
        val label = found.label
        val sourceId = found.sourceId
        val pool = if (chosen != null) siblings else found.pool
        // A dub often lists no subtitle files of its own (they belong to the sub stream), so the same extension's
        // sub streams are asked for theirs. Without this a dubbed episode was saved with no subtitles at all.
        onStage("Looking for subtitles…")
        val resolvedVideo = withSiblingSubtitles(found.video, found.picked ?: chosen, pool)
        // Extensions sometimes hand back links that are not files at all (a magnet or torrent), and request headers
        // that would break a resumed download. Both are dealt with here, before anything is saved.
        checkDownloadable(resolvedVideo.url)
        val video = resolvedVideo.copy(headers = sanitizeHeaders(resolvedVideo.headers))
        hostHeaders[Uri.parse(video.url).host.orEmpty()] = video.headers
        lastHeaders = video.headers

        onStage("Checking the stream…")
        val mime = stepTimeout(MIME_TIMEOUT_MS, "Checking the stream") { mimeOf(video.url, video.headers) }

        // Every subtitle file the source lists, saved before the video so they are there when it finishes.
        // A slow subtitle host must not hold the video back: whatever could not be fetched in time is skipped.
        onStage("Saving subtitles…")
        val savedSubtitles = java.util.Collections.synchronizedList(mutableListOf<DownloadedSubtitle>())
        withTimeoutOrNull(SUBTITLE_TIMEOUT_MS) { downloadSubtitles(stub.id, video, savedSubtitles) }
        // Kept in the order the source listed them, whichever finished first.
        val order = video.subtitleTracks.distinctBy { it.url }.mapIndexed { i, t -> "${i}_${safeName(t.lang.ifBlank { "sub" })}." to i }
        val subtitles = synchronized(savedSubtitles) { savedSubtitles.toList() }
            .sortedBy { sub -> order.firstOrNull { (prefix, _) -> File(sub.path).name.startsWith(prefix) }?.second ?: Int.MAX_VALUE }
        diagnostics?.log(LogCategory.PLAYBACK, "Download: ${anime.title} E${episode.episodeNumber} from $label, ${subtitles.size} subtitle file(s)")

        onStage("Reading the stream…")
        val request = stepTimeout(REQUEST_TIMEOUT_MS, "Reading the stream") { buildRequest(stub.id, video.url, mime, video.headers, heightFor(quality)) }
        val entry = stub.copy(
            sourceId = sourceId,
            sourceLabel = label,
            url = video.url,
            headers = video.headers,
            subtitles = subtitles,
        )
        return entry to request
    }

    /** Refuses what can never be downloaded, with a message that says why: torrents and anything that is not http(s). */
    private fun checkDownloadable(url: String) {
        val lower = url.trim().lowercase()
        val path = lower.substringBefore('?').substringBefore('#')
        when {
            lower.startsWith("magnet:") || lower.startsWith("torrent:") || path.endsWith(".torrent") ->
                throw IOException("This source gives a torrent link, which cannot be downloaded. Pick another source.")
            !lower.startsWith("http://") && !lower.startsWith("https://") ->
                throw IOException("This source's link cannot be downloaded. Pick another source.")
        }
    }

    /**
     * Headers an extension asks for, minus what must stay under the downloader's control. A Range header from the
     * extension would override the one a resumed download sends and corrupt or restart the file, and Accept-Encoding
     * would let the server compress the bytes, so neither is passed on. Blank entries are dropped.
     */
    private fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> =
        headers.mapNotNull { (key, value) ->
            val name = key.trim()
            val text = value.trim()
            if (name.isEmpty() || text.isEmpty() ||
                name.equals("Range", true) || name.equals("If-Range", true) || name.equals("Accept-Encoding", true)
            ) null else name to text
        }.toMap()

    /** Why a download failed, in words: the HTTP status the source answered with, or null when it is not an HTTP error. */
    fun describeFailure(error: Throwable?): String? {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                val code = cause.responseCode
                return when {
                    code == 401 || code == 403 -> "The source refused the download (HTTP $code). Its link may have expired: delete this download and start it again."
                    code == 404 || code == 410 -> "The file is gone from the source (HTTP $code). Try another source."
                    code == 429 -> "The source is limiting downloads (HTTP 429). Try again in a few minutes."
                    code >= 500 -> "The source's server had a problem (HTTP $code). Try again later."
                    else -> "The source answered with HTTP $code."
                }
            }
            val next = cause.cause
            cause = if (next === cause) null else next
        }
        return null
    }

    /**
     * Runs one preparing step with a time limit. A timeout is turned into a plain failure: left as it is, a
     * timeout is a cancellation, which [enqueue] rethrows, and the download would sit on "Preparing" forever.
     */
    private suspend fun <T> stepTimeout(ms: Long, what: String, block: suspend () -> T): T =
        try {
            withTimeout(ms) { block() }
        } catch (e: TimeoutCancellationException) {
            throw IOException("$what took too long. Delete it and try another source.")
        }

    private data class Found(
        val video: Video,
        val label: String,
        val sourceId: Long?,
        /** The stream the video was resolved from (listed form). */
        val picked: PlayableSource? = null,
        /** Every stream the sources listed for the episode, for borrowing subtitles. */
        val pool: List<PlayableSource> = emptyList(),
    )

    /** Resolves the one stream the person picked in the source picker (listed streams are resolved only now). */
    private suspend fun resolveChosen(chosen: PlayableSource): Found? {
        val pending = chosen.resolve
        val ready: PlayableSource? = if (pending == null) {
            chosen
        } else {
            try {
                withTimeout(RESOLVE_TIMEOUT_MS) { pending() }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) null else throw e
            } catch (e: Throwable) {
                null
            }
        }
        if (ready == null || ready.url.isBlank()) return null
        val label = listOfNotNull(chosen.extensionName, chosen.serverName, chosen.label).distinct().joinToString(" · ")
        return Found(
            Video(
                url = ready.url,
                quality = ready.label,
                sourceLabel = chosen.extensionName.orEmpty(),
                headers = ready.headers,
                subtitleTracks = ready.subtitles.ifEmpty { chosen.subtitles },
                audioTracks = ready.audioTracks,
            ),
            label,
            chosen.sourceId,
            picked = chosen,
        )
    }

    /** Asks every source that has the episode and picks the stream the player would start with. */
    private suspend fun findBestVideo(anime: SAnime, episode: SEpisode, quality: Int, language: String): Found? {
        val origin = anime.origin as? MediaOrigin.Extension ?: return null
        // An episode that came without its alternates (Continue Watching) is matched in every source first.
        val enriched = if (episode.alternates.isEmpty() && episode.id.isNotBlank()) {
            runCatching {
                extensionManager.findEpisodesByTitle(
                    anime,
                    extraTitlesProvider = { anime.anilistId?.let { id -> aniListRepository.getAllTitles(id) }.orEmpty() },
                ).firstOrNull { it.episodeNumber == episode.episodeNumber }
            }.getOrNull() ?: episode
        } else {
            episode
        }
        val candidates = (listOf(enriched.copy(alternates = emptyList())) + enriched.alternates)
            .distinctBy { it.sourceId ?: origin.sourceId }
        val collected = java.util.Collections.synchronizedList(mutableListOf<PlayableSource>())
        coroutineScope {
            candidates.map { candidate ->
                async {
                    val source = extensionManager.getSource(candidate.sourceId ?: origin.sourceId) ?: return@async
                    try {
                        withTimeout(FIND_TIMEOUT_MS) {
                            source.streamVideos(candidate, { line -> diagnostics?.log(LogCategory.PLAYBACK, line) }) { videos ->
                                collected += videos.map { it.toPlayable().copy(sourceId = source.id, extensionName = source.name) }
                            }
                        }
                    } catch (e: CancellationException) {
                        if (e !is TimeoutCancellationException) throw e
                    } catch (e: Throwable) {
                        diagnostics?.log(LogCategory.PLAYBACK, "Download: ${source.name}: ${e::class.simpleName}: ${e.message}")
                    }
                }
            }.awaitAll()
        }

        val pool = collected.toMutableList().sortedBy { playerPrefs.rankOf(it.sourceId) }.toMutableList()
        var attempts = 0
        while (pool.isNotEmpty() && attempts < MAX_RESOLVE_ATTEMPTS) {
            attempts++
            val chosen = pickStart(pool, null, quality, language) ?: pool.first()
            pool.remove(chosen)
            val ready: PlayableSource? = chosen.resolve?.let { pending ->
                try {
                    withTimeout(RESOLVE_TIMEOUT_MS) { pending() }
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException) null else throw e
                } catch (e: Throwable) {
                    null
                }
            } ?: chosen.takeIf { it.resolve == null }
            if (ready == null || ready.url.isBlank()) continue
            val label = listOfNotNull(chosen.extensionName, chosen.serverName, chosen.label).distinct().joinToString(" · ")
            return Found(
                Video(
                    url = ready.url,
                    quality = ready.label,
                    sourceLabel = chosen.extensionName.orEmpty(),
                    headers = ready.headers,
                    subtitleTracks = ready.subtitles.ifEmpty { chosen.subtitles },
                    audioTracks = ready.audioTracks,
                ),
                label,
                chosen.sourceId,
                picked = chosen,
                pool = collected.toList(),
            )
        }
        return null
    }

    /** True when the stream is the dubbed version of the episode (label, server name or extension name says so). */
    private fun PlayableSource.isDubStream(): Boolean =
        kindChip() == "Dub" ||
            serverName?.contains("dub", ignoreCase = true) == true ||
            extensionName?.contains("dub", ignoreCase = true) == true ||
            audioTracks.any { it.lang.contains("dub", ignoreCase = true) }

    /**
     * Adds the subtitle files of the same extension's sub streams to a dub's own. Streams that already list their
     * subtitles are used as they are; otherwise up to three sub streams (the same server first) are resolved, and
     * the first one that has subtitles is used. Only a dub borrows, and a failure just leaves the video as it was.
     */
    private suspend fun withSiblingSubtitles(video: Video, picked: PlayableSource?, pool: List<PlayableSource>): Video {
        if (picked == null || !picked.isDubStream()) return video
        val donors = pool
            .filter { it !== picked && it.sourceId == picked.sourceId && !it.isDubStream() }
            .sortedBy { if (it.serverName == picked.serverName) 0 else 1 }
        if (donors.isEmpty()) return video
        val borrowed = mutableListOf<SubtitleTrack>()
        donors.firstOrNull { it.subtitles.isNotEmpty() }?.let { borrowed += it.subtitles }
        if (borrowed.isEmpty()) {
            for (donor in donors.filter { it.resolve != null }.take(3)) {
                val ready = try {
                    withTimeout(SIBLING_TIMEOUT_MS) { donor.resolve?.invoke() }
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException) null else throw e
                } catch (e: Throwable) {
                    null
                }
                if (ready != null && ready.subtitles.isNotEmpty()) {
                    borrowed += ready.subtitles
                    break
                }
            }
        }
        if (borrowed.isEmpty()) return video
        diagnostics?.log(LogCategory.PLAYBACK, "Download: dub stream has ${video.subtitleTracks.size} subtitle(s); borrowed ${borrowed.size} from the sub stream")
        return video.copy(subtitleTracks = (video.subtitleTracks + borrowed).distinctBy { it.url })
    }

    private fun Video.toPlayable(): PlayableSource = PlayableSource(
        label = if (sourceLabel.isBlank() || quality.contains(sourceLabel, true)) quality else "$sourceLabel · $quality",
        url = url,
        headers = headers,
        subtitles = subtitleTracks,
        audioTracks = audioTracks,
        resolve = resolve?.let { pending -> suspend { pending()?.toPlayable() } },
        serverName = serverName,
    )

    /** HLS, DASH or a plain file: from the address when it says so, else from the first bytes the server sends. */
    private suspend fun mimeOf(url: String, headers: Map<String, String>): String? {
        val lower = url.lowercase()
        val path = lower.substringBefore('?').substringBefore('#')
        return when {
            ".m3u8" in lower -> MimeTypes.APPLICATION_M3U8
            ".mpd" in lower -> MimeTypes.APPLICATION_MPD
            path.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            path.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            else -> withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(url)
                        .apply { headers.forEach { (k, v) -> header(k, v) } }
                        .header("Range", "bytes=0-1023")
                        .build()
                    quickClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val head = response.peekBody(1024).string().trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                        val type = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                        when {
                            head.startsWith("#EXTM3U") || "mpegurl" in type -> MimeTypes.APPLICATION_M3U8
                            head.contains("<MPD") || "dash+xml" in type -> MimeTypes.APPLICATION_MPD
                            type == "video/webm" -> MimeTypes.VIDEO_WEBM
                            type == "video/x-matroska" -> MimeTypes.VIDEO_MATROSKA
                            else -> MimeTypes.VIDEO_MP4
                        }
                    }
                }.getOrNull()
            }
        }
    }

    /**
     * The download request for [url]. An HLS or DASH stream is opened first so one video quality (the closest to
     * the Preferred quality) is chosen, and every audio and subtitle track the stream carries is kept with it;
     * downloading its whole manifest would fetch every quality. A plain file needs no choice.
     */
    /**
     * The request that downloads [url]. A plain file needs nothing more. A stream (HLS or DASH) is first read the way
     * the player reads it. If that fails or takes too long, an HLS stream is read directly: its playlist is fetched,
     * the picture quality is chosen from it, and the download starts from there. This is the path that works when the
     * player's own reader cannot get through a source.
     */
    private suspend fun buildRequest(id: String, url: String, mime: String?, headers: Map<String, String>, wantedHeight: Int): DownloadRequest {
        val adaptive = mime == MimeTypes.APPLICATION_M3U8 || mime == MimeTypes.APPLICATION_MPD
        if (!adaptive) return readWithHelper(id, url, mime, wantedHeight)

        var helperError: Throwable? = null
        try {
            return stepTimeout(HELPER_TIMEOUT_MS, "Reading the stream") { readWithHelper(id, url, mime, wantedHeight) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            helperError = e
        }
        diagnostics?.log(LogCategory.PLAYBACK, "Download: the player could not read the stream (${helperError?.message}); reading its playlist directly")
        val reason = describeFailure(helperError) ?: "Could not read the stream: ${helperError?.message ?: helperError?.let { it::class.simpleName }}"
        if (mime != MimeTypes.APPLICATION_M3U8) throw IOException(reason, helperError)
        return try {
            readHlsPlaylist(id, url, headers, wantedHeight)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            diagnostics?.log(LogCategory.PLAYBACK, "Download: reading the playlist directly failed too: ${e::class.simpleName}: ${e.message}")
            throw IOException(reason, e)
        }
    }

    /** Reads an HLS playlist with a plain request, picks the quality, and returns the request for it. */
    private suspend fun readHlsPlaylist(id: String, url: String, headers: Map<String, String>, wanted: Int): DownloadRequest {
        // Some hosts (the Vidstream ones) can take 10 s or more to answer the playlist request, so this read is
        // patient and tries twice: a slow answer is not a dead source.
        val text = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
            var last: IOException? = null
            for (attempt in 1..2) {
                try {
                    return@withContext patientClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("The source answered HTTP ${response.code} for the playlist")
                        response.body?.source()?.let { source -> source.request(512L * 1024L); source.buffer.clone().readUtf8() }.orEmpty()
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    last = e
                    diagnostics?.log(LogCategory.PLAYBACK, "Download: the playlist did not answer in time (attempt $attempt of 2)")
                }
            }
            throw last ?: IOException("The playlist could not be read")
        }
        if (!text.trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith("#EXTM3U")) throw IOException("The source did not return a playlist")
        val hls = DownloadRequest.Builder(id, Uri.parse(url)).setMimeType(MimeTypes.APPLICATION_M3U8)
        if (!text.contains("#EXT-X-STREAM-INF")) return hls.build() // already one quality: nothing to choose

        // A master playlist: every quality it lists, in order, with its height and bitrate.
        data class Variant(val height: Int, val bandwidth: Long, val uri: String)
        val lines = text.lines().map { it.trim() }
        val variants = mutableListOf<Variant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                var j = i + 1
                while (j < lines.size && (lines[j].isEmpty() || lines[j].startsWith("#"))) j++
                if (j < lines.size) {
                    val attrs = line.substringAfter(':')
                    val bandwidth = Regex("BANDWIDTH=(\\d+)").find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val height = Regex("RESOLUTION=\\d+x(\\d+)").find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val uri = runCatching { java.net.URI(url).resolve(lines[j]).toString() }.getOrDefault(lines[j])
                    variants += Variant(height, bandwidth, uri)
                    i = j
                }
            }
            i++
        }
        if (variants.isEmpty()) throw IOException("The playlist lists no qualities")

        // Audio that is a separate file cannot be kept by taking one picture quality alone, so the whole playlist is
        // downloaded then. It is larger, but it has sound.
        val separateAudio = lines.any { it.startsWith("#EXT-X-MEDIA") && "TYPE=AUDIO" in it && "URI=" in it }
        // Subtitles carried inside the stream (a subtitle rendition in the playlist) are the same: taking one picture
        // quality alone would leave them behind, which is how a dub that streams with subtitles downloaded without.
        val embeddedSubtitles = lines.any { it.startsWith("#EXT-X-MEDIA") && "TYPE=SUBTITLES" in it && "URI=" in it }
        if (separateAudio || embeddedSubtitles) {
            diagnostics?.log(LogCategory.PLAYBACK, "Download: whole playlist kept (separate audio: $separateAudio, embedded subtitles: $embeddedSubtitles)")
            return hls.build()
        }

        val withHeight = variants.filter { it.height > 0 }.sortedBy { it.height }
        val chosen = if (withHeight.isNotEmpty()) {
            withHeight.lastOrNull { it.height <= wanted } ?: withHeight.first()
        } else {
            variants.maxByOrNull { it.bandwidth } ?: variants.first()
        }
        return DownloadRequest.Builder(id, Uri.parse(chosen.uri)).setMimeType(MimeTypes.APPLICATION_M3U8).build()
    }

    private suspend fun readWithHelper(id: String, url: String, mime: String?, wantedHeight: Int): DownloadRequest {
        val adaptive = mime == MimeTypes.APPLICATION_M3U8 || mime == MimeTypes.APPLICATION_MPD
        if (!adaptive) {
            return DownloadRequest.Builder(id, Uri.parse(url)).apply { mime?.let { setMimeType(it) } }.build()
        }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<DownloadRequest> { cont ->
                val item = MediaItem.Builder().setUri(url).setMimeType(mime).build()
                val helper = DownloadHelper.forMediaItem(appContext, item, DefaultRenderersFactory(appContext), dataSourceFactory)
                var finished = false
                // The helper's callback interface changed shape between Media3 versions, so it is answered through a
                // proxy that reacts to the two calls by name.
                val handler = InvocationHandler { proxy, method, args ->
                    when (method.name) {
                        "onPrepared" -> if (!finished) {
                            finished = true
                            try {
                                selectTracks(helper, wantedHeight)
                                val request = helper.getDownloadRequest(id, null)
                                helper.release()
                                cont.resume(request)
                            } catch (t: Throwable) {
                                helper.release()
                                cont.resumeWithException(t)
                            }
                        }
                        "onPrepareError" -> if (!finished) {
                            finished = true
                            helper.release()
                            cont.resumeWithException((args?.getOrNull(1) as? Exception) ?: IOException("Could not read the stream"))
                        }
                        "hashCode" -> return@InvocationHandler System.identityHashCode(proxy)
                        "equals" -> return@InvocationHandler proxy === args?.getOrNull(0)
                        "toString" -> return@InvocationHandler "DownloadHelperCallback"
                    }
                    null
                }
                val callback = Proxy.newProxyInstance(
                    DownloadHelper.Callback::class.java.classLoader,
                    arrayOf(DownloadHelper.Callback::class.java),
                    handler,
                ) as DownloadHelper.Callback
                cont.invokeOnCancellation { helper.release() }
                helper.prepare(callback)
            }
        }
    }

    /** One video track near the preferred quality, plus every audio and subtitle track. */
    private fun selectTracks(helper: DownloadHelper, wanted: Int) {
        if (helper.periodCount == 0) return
        val tracks = helper.getTracks(0)
        helper.clearTrackSelections(0)
        val allTypes = listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)
        for (group in tracks.groups) {
            if (group.type !in allTypes) continue
            val supported = (0 until group.length).filter { group.isTrackSupported(it) }
            if (supported.isEmpty()) continue
            val indices = if (group.type == C.TRACK_TYPE_VIDEO) {
                // The tallest picture that is not above the wanted one; when all are above it, the smallest of them.
                val byHeight = supported.sortedBy { group.getTrackFormat(it).height }
                listOf(byHeight.lastOrNull { group.getTrackFormat(it).height <= wanted } ?: byHeight.first())
            } else {
                supported
            }
            // One selection per group: a single selection keeps only one group of a type, and together they add up.
            val params = TrackSelectionParameters.Builder(appContext).apply {
                allTypes.filter { it != group.type }.forEach { setTrackTypeDisabled(it, true) }
                addOverride(TrackSelectionOverride(group.mediaTrackGroup, indices))
            }.build()
            helper.addTrackSelection(0, params)
        }
        val textGroups = tracks.groups.count { it.type == C.TRACK_TYPE_TEXT }
        diagnostics?.log(LogCategory.PLAYBACK, "Download: stream carries $textGroups embedded subtitle track group(s)")
    }

    /**
     * Saves every external subtitle file the stream lists, all at once. A subtitle host often refuses the video
     * host's Referer, so each file is tried with the stream's headers and then without them. Files that finish are
     * added to [sink] as they arrive, so a slow one never costs the others: whatever is in [sink] when the time
     * limit hits is kept.
     */
    private suspend fun downloadSubtitles(id: String, video: Video, sink: MutableList<DownloadedSubtitle>) = withContext(Dispatchers.IO) {
        val dir = File(appContext.filesDir, "downloads/subs/${safeName(id)}").apply { mkdirs() }
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        coroutineScope {
            video.subtitleTracks.distinctBy { it.url }.mapIndexed { index, track ->
                async {
                    gate.acquire()
                    try {
                        val bytes = fetchSubtitle(track.url, video.headers) ?: fetchSubtitle(track.url, emptyMap())
                        if (bytes == null) {
                            diagnostics?.log(LogCategory.PLAYBACK, "Download: subtitle '${track.lang}' could not be fetched (${track.url.substringBefore('?')})")
                        } else {
                            val file = File(dir, "${index}_${safeName(track.lang.ifBlank { "sub" })}.${subtitleExtension(track.url)}")
                            file.writeBytes(bytes)
                            sink += DownloadedSubtitle(track.lang.ifBlank { "Subtitles" }, file.absolutePath)
                        }
                    } finally {
                        gate.release()
                    }
                }
            }.awaitAll()
        }
    }

    /** The bytes of one subtitle file, or null when it cannot be had (error status, empty body, network failure). */
    private fun fetchSubtitle(url: String, headers: Map<String, String>): ByteArray? = try {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        quickClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.bytes()?.takeIf { it.isNotEmpty() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun subtitleExtension(url: String): String {
        val path = url.lowercase().substringBefore('?').substringBefore('#')
        return when {
            path.endsWith(".srt") -> "srt"
            path.endsWith(".ass") -> "ass"
            path.endsWith(".ssa") -> "ssa"
            path.endsWith(".ttml") || path.endsWith(".xml") -> "ttml"
            else -> "vtt"
        }
    }

    private fun subtitleMime(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "ttml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.TEXT_VTT
    }

    // ---- bookkeeping ---------------------------------------------------------------------------------------

    private fun Download.toStatus(): DownloadStatus {
        val percent = percentDownloaded.takeIf { it >= 0f } ?: -1f
        return when (state) {
            Download.STATE_COMPLETED -> DownloadStatus(DownloadPhase.Completed, 100f, bytesDownloaded)
            Download.STATE_DOWNLOADING -> DownloadStatus(DownloadPhase.Downloading, percent, bytesDownloaded)
            Download.STATE_FAILED -> DownloadStatus(DownloadPhase.Failed, percent, bytesDownloaded, failureMessages[request.id] ?: "The download failed")
            Download.STATE_STOPPED -> DownloadStatus(DownloadPhase.Queued, percent, bytesDownloaded)
            else -> DownloadStatus(DownloadPhase.Queued, percent, bytesDownloaded)
        }
    }

    private fun setPreparing(id: String, status: DownloadStatus?) {
        preparing.value = if (status == null) preparing.value - id else preparing.value + (id to status)
    }

    private fun saveEntry(entry: DownloadEntry) {
        entries.value = entries.value.filterNot { it.id == entry.id } + entry
        persist()
    }

    private fun persist() {
        prefs.edit().putString(KEY_ENTRIES, json.encodeToString(listSerializer, entries.value)).apply()
    }

    private fun loadEntries(): List<DownloadEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun safeName(value: String): String = value.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val FIND_TIMEOUT_MS = 25_000L
        const val RESOLVE_TIMEOUT_MS = 30_000L
        const val SIBLING_TIMEOUT_MS = 15_000L
        const val MIME_TIMEOUT_MS = 20_000L
        const val SUBTITLE_TIMEOUT_MS = 30_000L
        const val HELPER_TIMEOUT_MS = 20_000L
        const val REQUEST_TIMEOUT_MS = 110_000L
        const val MAX_RESOLVE_ATTEMPTS = 5
        const val KEY_PAUSED = "paused_all"

        /** Threads that fetch the parts (segments) of the downloads in parallel. */
        const val SEGMENT_THREADS = 10

        /** Media3 tries a failing download this many times itself before it reports the failure. */
        const val MIN_RETRY_COUNT = 6

        /** Pause before each automatic retry after Media3 gave up: three tries, each waiting longer. */
        val RETRY_BACKOFF_MS = longArrayOf(5_000L, 15_000L, 45_000L)

        /** The battery level (percent) at or under which "pause on low battery" holds the queue, when not charging. */
        const val LOW_BATTERY_PERCENT = 15
    }
}
