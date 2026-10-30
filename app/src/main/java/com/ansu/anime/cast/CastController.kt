package com.ansu.anime.cast

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaRouter
import com.ansu.anime.core.model.SubtitleTrack
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What the player shows about the Chromecast: whether one is around, whether a session is up, and what it is playing. */
data class CastUiState(
    /** The Cast framework started, so the player's Cast button shows (tapping it opens the device picker, which searches). */
    val ready: Boolean = false,
    /** At least one Cast device was found on the network. */
    val available: Boolean = false,
    val connected: Boolean = false,
    val deviceName: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** The text tracks the TV reports for what it is playing (the ones sent from here and any inside the stream). */
    val subtitleTracks: List<CastTrackOption> = emptyList(),
    val activeSubtitleId: Long? = null,
    /** Every Cast device the network answered with, for the Casting panel. */
    val devices: List<CastDeviceOption> = emptyList(),
)

data class CastTrackOption(val id: Long, val label: String)

/** One TV the Casting panel lists: [selected] is the one the phone is connected to, [connecting] one it is connecting to. */
data class CastDeviceOption(
    val id: String,
    val name: String,
    val detail: String?,
    val selected: Boolean,
    val connecting: Boolean,
)

/** Where the TV was when the session ended, so the phone can carry on from there. */
data class CastResume(val positionMs: Long, val playing: Boolean)

/**
 * Chromecast support for the player. It owns the Cast session and the remote player on the TV; the local ExoPlayer
 * stays untouched and is simply kept paused while a session is up. Nothing here may throw on a phone without Google
 * Play services: the Cast framework is started asynchronously and a failure only leaves [CastUiState.ready] false.
 */
class CastController(private val context: Context) {

    private val _state = MutableStateFlow(CastUiState())
    val state: StateFlow<CastUiState> = _state

    private var castContext: CastContext? = null
    private var remote: RemoteMediaClient? = null
    private var released = false
    private var lastResume: CastResume? = null
    private var subtitleServer: CastSubtitleServer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var router: MediaRouter? = null
    // Keeps the Casting panel's device list live: a TV appearing, going away or changing state refreshes it.
    private val discoveryCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onProviderChanged(router: MediaRouter, provider: MediaRouter.ProviderInfo) = refreshDevices()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStarted(session: CastSession, sessionId: String) = bind(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = unbind()
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = bind(session)
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    private val castStateListener = CastStateListener { castState ->
        _state.update { it.copy(available = castState != CastState.NO_DEVICES_AVAILABLE) }
        refreshDevices()
    }

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = publishStatus()
    }

    private val progressListener = RemoteMediaClient.ProgressListener { progressMs, durationMs ->
        _state.update { it.copy(positionMs = progressMs, durationMs = durationMs.coerceAtLeast(0L)) }
        rememberResume()
    }

    /** Starts the Cast framework. Safe to call without Google Play services: it just leaves Cast unavailable. */
    fun attach() {
        runCatching {
            CastContext.getSharedInstance(context, initExecutor)
                .addOnSuccessListener { ctx -> if (!released) onContextReady(ctx) }
                .addOnFailureListener { _state.update { it.copy(ready = false, available = false) } }
        }
    }

    private fun onContextReady(ctx: CastContext) {
        castContext = ctx
        // The framework only looks for TVs by itself while a route button is on screen, and the player draws its own
        // icon, so it would never have searched. Asking the router for an active scan while the player is open is what
        // makes devices show up.
        ctx.mergedSelector?.let { selector ->
            runCatching {
                val mediaRouter = MediaRouter.getInstance(context)
                mediaRouter.addCallback(selector, discoveryCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
                router = mediaRouter
            }
        }
        ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        ctx.addCastStateListener(castStateListener)
        _state.update { it.copy(ready = true, available = ctx.castState != CastState.NO_DEVICES_AVAILABLE) }
        refreshDevices()
        ctx.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let(::bind)
    }

    private fun bind(session: CastSession) {
        detachRemote()
        val client = session.remoteMediaClient
        remote = client
        client?.registerCallback(mediaCallback)
        client?.addProgressListener(progressListener, PROGRESS_INTERVAL_MS)
        _state.update { it.copy(connected = true, deviceName = session.castDevice?.friendlyName) }
        refreshDevices()
    }

    private fun unbind() {
        detachRemote()
        stopSubtitleServer()
        _state.update { CastUiState(ready = it.ready, available = it.available, devices = it.devices) }
        refreshDevices()
    }

    /** Re-reads the Cast devices the router knows, so the Casting panel lists them with who is connected. */
    private fun refreshDevices() {
        val mediaRouter = router ?: return
        val selector = castContext?.mergedSelector ?: return
        val devices = runCatching {
            mediaRouter.routes
                .filter { it.isEnabled && it.matchesSelector(selector) }
                .map { route ->
                    val connecting = route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
                    CastDeviceOption(
                        id = route.id,
                        name = route.name,
                        detail = route.description?.takeIf { it.isNotBlank() && it != route.name },
                        selected = route.isSelected && !connecting,
                        connecting = connecting,
                    )
                }
                .sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
        _state.update { it.copy(devices = devices) }
    }

    /** Connects to the device with [id] from the Casting panel (the framework starts the session by itself). */
    fun connect(id: String) {
        val mediaRouter = router ?: return
        mediaRouter.routes.firstOrNull { it.id == id }?.let { runCatching { mediaRouter.selectRoute(it) } }
    }

    private fun detachRemote() {
        remote?.unregisterCallback(mediaCallback)
        remote?.removeProgressListener(progressListener)
        remote = null
    }

    private fun publishStatus() {
        val client = remote ?: return
        val playerState = client.playerState
        val tracks = client.mediaInfo?.mediaTracks.orEmpty()
            .filter { it.type == MediaTrack.TYPE_TEXT }
            .map { CastTrackOption(it.id, it.name?.takeIf(String::isNotBlank) ?: it.language ?: "Track ${it.id}") }
        val activeSubtitle = client.mediaStatus?.activeTrackIds?.firstOrNull { id -> tracks.any { it.id == id } }
        _state.update {
            it.copy(
                playing = playerState == MediaStatus.PLAYER_STATE_PLAYING,
                buffering = playerState == MediaStatus.PLAYER_STATE_BUFFERING || playerState == MediaStatus.PLAYER_STATE_LOADING,
                positionMs = client.approximateStreamPosition,
                durationMs = client.streamDuration.coerceAtLeast(0L),
                subtitleTracks = tracks,
                activeSubtitleId = activeSubtitle,
            )
        }
        rememberResume()
    }

    private fun rememberResume() {
        val current = _state.value
        if (current.connected) lastResume = CastResume(current.positionMs, current.playing)
    }

    /** Where the TV stopped, once, for the phone to resume from after a session ends. */
    fun takeResume(): CastResume? = lastResume.also { lastResume = null }

    /**
     * Sends a stream to the TV and starts it at [positionMs], with [subtitles] (fetched and converted by this phone,
     * see [CastSubtitleServer]). [onResult] gets false when the receiver refused the stream.
     */
    fun load(
        url: String,
        mimeType: String,
        headers: Map<String, String>,
        subtitles: List<SubtitleTrack>,
        title: String,
        subtitle: String?,
        imageUrl: String?,
        positionMs: Long,
        onResult: (Boolean) -> Unit,
    ) {
        if (remote == null) {
            onResult(false)
            return
        }
        // Starting the subtitle server touches the network, so it is done off the main thread.
        initExecutor.execute {
            val addresses = offerSubtitles(subtitles, headers)
            mainHandler.post { sendLoad(url, mimeType, subtitles, addresses, title, subtitle, imageUrl, positionMs, onResult) }
        }
    }

    private fun offerSubtitles(subtitles: List<SubtitleTrack>, headers: Map<String, String>): List<String> {
        if (subtitles.isEmpty()) return emptyList()
        val host = lanAddress(context) ?: return emptyList()
        val server = subtitleServer ?: CastSubtitleServer().also {
            runCatching { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) }.onFailure { return emptyList() }
            subtitleServer = it
        }
        return server.offer(subtitles, headers, host)
    }

    private fun sendLoad(
        url: String,
        mimeType: String,
        subtitles: List<SubtitleTrack>,
        addresses: List<String>,
        title: String,
        subtitle: String?,
        imageUrl: String?,
        positionMs: Long,
        onResult: (Boolean) -> Unit,
    ) {
        val client = remote
        if (client == null || released) {
            onResult(false)
            return
        }
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            if (!subtitle.isNullOrBlank()) putString(MediaMetadata.KEY_SUBTITLE, subtitle)
            if (!imageUrl.isNullOrBlank()) addImage(WebImage(Uri.parse(imageUrl)))
        }
        val tracks = addresses.mapIndexed { index, address ->
            MediaTrack.Builder(index + 1L, MediaTrack.TYPE_TEXT)
                .setContentId(address)
                .setContentType("text/vtt")
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setName(subtitles[index].lang)
                .build()
        }
        // Subtitles start on, in English when the source has it; the casting screen's Subtitles menu changes or turns them off.
        val first = subtitles.indexOfFirst { it.lang.contains("eng", ignoreCase = true) }.takeIf { it >= 0 } ?: 0
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mimeType)
            .setMetadata(metadata)
            .apply { if (tracks.isNotEmpty()) setMediaTracks(tracks) }
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .setCurrentTime(positionMs.coerceAtLeast(0L))
            .apply { if (tracks.isNotEmpty()) setActiveTrackIds(longArrayOf(first + 1L)) }
            .build()
        client.load(request).setResultCallback { result -> onResult(result.status.isSuccess) }
    }

    /** Turns the subtitle with [id] on, or all of them off when it is null; the audio track is left as it is. */
    fun selectSubtitle(id: Long?) {
        val client = remote ?: return
        val textIds = _state.value.subtitleTracks.map { it.id }.toSet()
        val kept = client.mediaStatus?.activeTrackIds?.filter { it !in textIds }.orEmpty()
        val ids = if (id == null) kept else kept + id
        client.setActiveMediaTracks(ids.toLongArray())
    }

    private fun stopSubtitleServer() {
        subtitleServer?.stop()
        subtitleServer = null
    }

    fun togglePlayPause() {
        val client = remote ?: return
        if (client.isPlaying) client.pause() else client.play()
    }

    fun seekTo(positionMs: Long) {
        remote?.seek(MediaSeekOptions.Builder().setPosition(positionMs.coerceAtLeast(0L)).build())
    }

    fun seekBy(deltaMs: Long) {
        val current = _state.value
        val limit = if (current.durationMs > 0L) current.durationMs else Long.MAX_VALUE
        seekTo((current.positionMs + deltaMs).coerceIn(0L, limit))
    }

    /** Ends the session; the TV stops playing and the phone gets the video back (see [takeResume]). */
    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    /** Leaving the player ends the session, so the TV does not keep playing something the phone no longer shows. */
    fun release() {
        released = true
        runCatching { router?.removeCallback(discoveryCallback) }
        router = null
        castContext?.let { ctx ->
            ctx.removeCastStateListener(castStateListener)
            ctx.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
            ctx.sessionManager.endCurrentSession(true)
        }
        detachRemote()
        stopSubtitleServer()
        castContext = null
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 1000L

        /** The Cast framework starts on a background thread; one shared thread is enough for every player screen. */
        val initExecutor by lazy { Executors.newSingleThreadExecutor() }
    }
}

/** True for a stream the TV can fetch itself: a plain http(s) address that is not on this phone. */
fun isCastableUrl(url: String): Boolean {
    val uri = Uri.parse(url)
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return host != "localhost" && !host.startsWith("127.") && host != "::1" && host != "[::1]"
}
