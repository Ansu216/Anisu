@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.details

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.ansu.anime.data.repository.TrailerRepository
import com.ansu.anime.data.repository.TrailerStream
import okhttp3.OkHttpClient
import com.ansu.anime.data.repository.Trailer
import com.ansu.anime.ui.theme.AnsuColors

/** Horizontal row of trailer thumbnails for the details page; tapping one hands it to [onPlay]. */
@Composable
fun TrailersRow(trailers: List<Trailer>, onPlay: (Trailer) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(trailers, key = { it.youtubeId }) { trailer ->
            Column(modifier = Modifier.width(240.dp).clickable { onPlay(trailer) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AnsuColors.BackgroundElevated),
                ) {
                    AsyncImage(
                        model = trailer.thumbnailUrl,
                        contentDescription = trailer.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
                Text(
                    text = trailer.title,
                    color = AnsuColors.TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Bottom sheet that plays [trailer] inside the app: the video's stream is taken from YouTube and played by the
 * app's own player (with its controls and a fullscreen button), so videos that refuse YouTube's embedded player
 * play all the same. While the stream is being looked up a spinner shows; if YouTube gives none, or the stream
 * fails to play, the sheet falls back to YouTube's embedded player, and its button opens the video in YouTube.
 */
@Composable
fun TrailerSheet(trailer: Trailer, trailerRepository: TrailerRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var stream by remember(trailer.youtubeId) { mutableStateOf<TrailerStream?>(null) }
    var useEmbedded by remember(trailer.youtubeId) { mutableStateOf(false) }
    LaunchedEffect(trailer.youtubeId) {
        val found = trailerRepository.resolveStream(trailer.youtubeId)
        if (found != null) stream = found else useEmbedded = true
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trailer", color = AnsuColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = trailer.title,
                        color = AnsuColors.TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trailer.watchUrl))) }
                }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in YouTube", tint = AnsuColors.TextPrimary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = AnsuColors.TextPrimary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val resolved = stream
                when {
                    useEmbedded -> YouTubePlayer(youtubeId = trailer.youtubeId, modifier = Modifier.fillMaxSize())
                    resolved != null -> NativeTrailerPlayer(
                        stream = resolved,
                        client = trailerRepository.httpClient,
                        // A stream that will not play (expired, refused) is replaced by the embedded player.
                        onFailed = {
                            stream = null
                            useEmbedded = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> CircularProgressIndicator(color = AnsuColors.Accent, strokeWidth = 3.dp)
                }
            }
        }
    }
}

/** Plays [stream] with the app's own player; its fullscreen button turns the video landscape in a full-window dialog. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun NativeTrailerPlayer(stream: TrailerStream, client: OkHttpClient, onFailed: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.trailerActivity() }
    var fullscreen by remember { mutableStateOf(false) }
    val latestOnFailed by rememberUpdatedState(onFailed)

    val player = remember(stream) {
        ExoPlayer.Builder(context).build().apply {
            val factory = OkHttpDataSource.Factory(client).setUserAgent(stream.userAgent)
            val progressive = ProgressiveMediaSource.Factory(factory)
            setMediaSource(
                when (stream) {
                    is TrailerStream.Hls -> HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(stream.url))
                    is TrailerStream.Progressive -> progressive.createMediaSource(MediaItem.fromUri(stream.url))
                    is TrailerStream.Merged -> MergingMediaSource(
                        progressive.createMediaSource(MediaItem.fromUri(stream.videoUrl)),
                        progressive.createMediaSource(MediaItem.fromUri(stream.audioUrl)),
                    )
                },
            )
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                latestOnFailed()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // The trailer stops when the app leaves the screen instead of playing on in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setFullscreenButtonClickListener { fullscreen = true }
            }
        },
        // The picture follows the player into the fullscreen dialog and back; a player draws into one view at a time.
        update = { view -> view.player = if (fullscreen) null else player },
        modifier = modifier,
    )

    if (fullscreen) {
        val fullscreenView = remember { PlayerView(context) }
        DisposableEffect(Unit) {
            val previous = activity?.requestedOrientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose { activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            DisposableEffect(fullscreenView) {
                onDispose { fullscreenView.player = null }
            }
            AndroidView(
                factory = {
                    fullscreenView.apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setFullscreenButtonState(true)
                        setFullscreenButtonClickListener { fullscreen = false }
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}

/** The custom (fullscreen) view the embedded player asked the window to show, so it can be taken down again. */
private class FullscreenHolder {
    var view: View? = null
    var callback: WebChromeClient.CustomViewCallback? = null
}

private fun Context.trailerActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(youtubeId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.trailerActivity() }
    val holder = remember { FullscreenHolder() }
    var fullscreen by remember { mutableStateOf(false) }

    val exitFullscreen = remember(activity) {
        {
            val view = holder.view
            if (view != null) {
                (view.parent as? ViewGroup)?.removeView(view)
                holder.view = null
                holder.callback?.onCustomViewHidden()
                holder.callback = null
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars()) }
            }
            fullscreen = false
        }
    }

    val webView = remember(youtubeId) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    val host = activity
                    val decor = host?.window?.decorView as? FrameLayout
                    if (host == null || decor == null) {
                        callback.onCustomViewHidden()
                        return
                    }
                    holder.view = view
                    holder.callback = callback
                    decor.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    WindowCompat.getInsetsController(host.window, decor).hide(WindowInsetsCompat.Type.systemBars())
                    fullscreen = true
                }

                override fun onHideCustomView() {
                    exitFullscreen()
                }
            }
            val html = """
                <!DOCTYPE html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style>
                </head><body>
                <iframe src="https://www.youtube.com/embed/$youtubeId?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=1"
                    allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe>
                </body></html>
            """.trimIndent()
            loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
        }
    }

    // Back leaves fullscreen first; only a second Back closes the sheet.
    BackHandler(enabled = fullscreen) { exitFullscreen() }

    DisposableEffect(webView) {
        onDispose {
            exitFullscreen()
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    AndroidView(factory = { webView }, modifier = modifier)
}
