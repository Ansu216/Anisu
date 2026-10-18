package com.ansu.anime.data.download

import android.content.Context
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
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.model.Video
import com.ansu.anime.data.prefs.PlayerPrefs
import com.ansu.anime.extension.ExtensionManager
import com.ansu.anime.extension.findEpisodesByTitle
import com.ansu.anime.ui.player.PlayableSource
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = appContext.getSharedPreferences("ansu_downloads", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(DownloadEntry.serializer())

    private val entries = MutableStateFlow(loadEntries())
    private val preparing = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    private val media3States = MutableStateFlow<Map<String, Download>>(emptyMap())

    /** Every download, newest first, with where it stands. */
    val items: StateFlow<List<DownloadItem>> = combine(entries, preparing, media3States) { saved, prep, states ->
        saved.sortedByDescending { it.createdAt }.map { entry ->
            DownloadItem(entry, prep[entry.id] ?: states[entry.id]?.toStatus() ?: DownloadStatus(DownloadPhase.Queued))
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

    private val upstreamFactory: DataSource.Factory = ResolvingDataSource.Factory(
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
        ResolvingDataSource.Resolver { spec ->
            val extra = headersFor(spec.uri.host)
            if (extra.isNullOrEmpty()) spec else spec.withAdditionalHeaders(extra)
        },
    )

    /** Reads downloaded bytes first and goes to the network only for what is missing. Used to download and to play. */
    val dataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            media3States.value = media3States.value + (download.request.id to download)
            if (download.state == Download.STATE_FAILED) {
                diagnostics?.log(LogCategory.PLAYBACK, "Download failed (${download.failureReason}): ${download.request.id} ${finalException?.message.orEmpty()}")
            }
            startProgressPolling()
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            media3States.value = media3States.value - download.request.id
        }
    }

    /** One download manager for the whole app; the [AnisuDownloadService] hosts it so downloads outlive the screen. */
    val downloadManager: DownloadManager by lazy {
        DownloadManager(appContext, databaseProvider, cache, dataSourceFactory, Executors.newFixedThreadPool(2)).also {
            // One at a time: the request headers are looked up per host, and a single queue keeps them unambiguous.
            it.maxParallelDownloads = 1
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
                media3States.value = media3States.value + active.associateBy { it.request.id }
                delay(1_000L)
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
            media3States.value = media3States.value + saved.associateBy { it.request.id }
            if (saved.any { it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING }) startProgressPolling()
        }
    }

    // ---- the public API ------------------------------------------------------------------------------------

    /** Starts downloading [episode] of [anime]. Does nothing when it is already downloaded or in progress. */
    fun enqueue(anime: SAnime, episode: SEpisode) {
        val anilistId = anime.anilistId ?: anime.id.toIntOrNull() ?: return
        val id = downloadKey(anilistId, episode.episodeNumber)
        val existing = items.value.firstOrNull { it.entry.id == id }
        if (existing != null && existing.status.phase != DownloadPhase.Failed) return
        if (existing != null) remove(id)

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
        setPreparing(id, DownloadStatus(DownloadPhase.Preparing))
        scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) { prepare(stub, anime, episode) }
                saveEntry(prepared.first)
                try {
                    DownloadService.sendAddDownload(appContext, AnisuDownloadService::class.java, prepared.second, false)
                } catch (e: Exception) {
                    // The service could not be started (app in the background): the manager still downloads while the app lives.
                    downloadManager.addDownload(prepared.second, Download.STOP_REASON_NONE)
                }
                setPreparing(id, null)
                startProgressPolling()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                diagnostics?.log(LogCategory.PLAYBACK, "Download of ${anime.title} E${episode.episodeNumber} failed to start: ${e::class.simpleName}: ${e.message}", e)
                setPreparing(id, DownloadStatus(DownloadPhase.Failed, message = e.message ?: "Could not start the download"))
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
        entries.value = entries.value.filterNot { it.id == id }
        persist()
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

    fun entryFor(id: String): DownloadEntry? = entries.value.firstOrNull { it.id == id }

    // ---- preparing a download ------------------------------------------------------------------------------

    private suspend fun prepare(stub: DownloadEntry, anime: SAnime, episode: SEpisode): Pair<DownloadEntry, DownloadRequest> {
        val (video, label, sourceId) = findBestVideo(anime, episode) ?: throw IOException("No playable source found for this episode")
        hostHeaders[Uri.parse(video.url).host.orEmpty()] = video.headers
        lastHeaders = video.headers
        val mime = mimeOf(video.url, video.headers)

        // Every subtitle file the source lists, saved before the video so they are there when it finishes.
        val subtitles = downloadSubtitles(stub.id, video)
        diagnostics?.log(LogCategory.PLAYBACK, "Download: ${anime.title} E${episode.episodeNumber} from $label, ${subtitles.size} subtitle file(s)")

        val request = buildRequest(stub.id, video.url, mime)
        val entry = stub.copy(
            sourceId = sourceId,
            sourceLabel = label,
            url = video.url,
            headers = video.headers,
            subtitles = subtitles,
        )
        return entry to request
    }

    private data class Found(val video: Video, val label: String, val sourceId: Long?)

    /** Asks every source that has the episode and picks the stream the player would start with. */
    private suspend fun findBestVideo(anime: SAnime, episode: SEpisode): Found? {
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
            val chosen = pickStart(pool, null, playerPrefs.preferredQuality.value, playerPrefs.preferredLanguage.value) ?: pool.first()
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
            )
        }
        return null
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
                    okHttpClient.newCall(request).execute().use { response ->
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
    private suspend fun buildRequest(id: String, url: String, mime: String?): DownloadRequest {
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
                                selectTracks(helper)
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
    private fun selectTracks(helper: DownloadHelper) {
        if (helper.periodCount == 0) return
        val wanted = playerPrefs.preferredQuality.value.takeIf { it != PlayerPrefs.QUALITY_ANY } ?: 1080
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
    }

    /** Saves every external subtitle file the stream lists; one that cannot be fetched is skipped. */
    private suspend fun downloadSubtitles(id: String, video: Video): List<DownloadedSubtitle> = withContext(Dispatchers.IO) {
        val dir = File(appContext.filesDir, "downloads/subs/${safeName(id)}").apply { mkdirs() }
        val saved = mutableListOf<DownloadedSubtitle>()
        video.subtitleTracks.distinctBy { it.url }.forEachIndexed { index, track ->
            runCatching {
                val request = Request.Builder().url(track.url)
                    .apply { video.headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bytes = response.body?.bytes() ?: return@use
                    if (bytes.isEmpty()) return@use
                    val ext = subtitleExtension(track.url)
                    val file = File(dir, "${index}_${safeName(track.lang.ifBlank { "sub" })}.$ext")
                    file.writeBytes(bytes)
                    saved += DownloadedSubtitle(track.lang.ifBlank { "Subtitles" }, file.absolutePath)
                }
            }
        }
        saved
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
            Download.STATE_FAILED -> DownloadStatus(DownloadPhase.Failed, percent, bytesDownloaded, "The download failed")
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
        const val MAX_RESOLVE_ATTEMPTS = 5
    }
}
