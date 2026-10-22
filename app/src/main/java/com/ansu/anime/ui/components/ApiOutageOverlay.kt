package com.ansu.anime.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ansu.anime.core.net.ApiErrorKind
import com.ansu.anime.core.net.ApiOutage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the card quietly asks AniList whether it is back, while it is showing. */
private const val RECHECK_INTERVAL_MS = 30_000L

/**
 * The "AniList is down" notice: [ErrorGlassCard] in the middle of the screen over a dimmed scrim, popping in
 * with a small scale. [onShowingChange] tells the caller when it is up, so it can blur the screen behind it.
 *
 * It appears when the error handler reports an outage, checks again by itself every
 * [RECHECK_INTERVAL_MS], and fades away on its own once AniList answers (the outage then becomes
 * null). Dismissing it (the close button or a tap outside the card) hides it for that kind of outage only;
 * it comes back if the problem changes (say, from slow to offline) or returns after a recovery. Retry runs
 * [onRetry] right away and shows "Checking…" until it finishes.
 *
 * Give it the full screen as [modifier]; the card centres itself.
 */
@Composable
fun ApiOutageOverlay(
    outage: ApiOutage?,
    onRetry: suspend () -> Unit,
    modifier: Modifier = Modifier,
    onShowingChange: (Boolean) -> Unit = {},
) {
    var dismissedKind by rememberSaveable { mutableStateOf<String?>(null) }
    var retrying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Kept after the outage ends so the card has its text while it fades out.
    var lastShown by remember { mutableStateOf<ApiOutage?>(null) }
    if (outage != null) lastShown = outage

    LaunchedEffect(outage == null) {
        if (outage == null) dismissedKind = null
    }
    LaunchedEffect(outage != null) {
        while (outage != null) {
            delay(RECHECK_INTERVAL_MS)
            onRetry()
        }
    }

    val visible = outage != null && outage.kind.name != dismissedKind
    LaunchedEffect(visible) { onShowingChange(visible) }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(200)),
        ) {
            val shown = lastShown ?: return@AnimatedVisibility
            val text = outageText(shown)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The screen behind is blurred by the caller where the platform can; this dims it further.
                    .background(Color.Black.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.40f else 0.80f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        dismissedKind = shown.kind.name
                    }
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                ErrorGlassCard(
                    title = text.first,
                    message = text.second,
                    details = outageDetails(shown),
                    retryLabel = if (retrying) "Checking…" else "Try again",
                    retryEnabled = !retrying,
                    onRetry = {
                        scope.launch {
                            retrying = true
                            try {
                                onRetry()
                            } finally {
                                retrying = false
                            }
                        }
                    },
                    onDismiss = { dismissedKind = shown.kind.name },
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        // Taps on the card itself must not fall through to the dismiss-on-tap scrim.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .animateEnterExit(
                            enter = scaleIn(tween(280), initialScale = 0.92f) + fadeIn(tween(280)),
                            exit = scaleOut(tween(200), targetScale = 0.96f) + fadeOut(tween(200)),
                        ),
                )
            }
        }
    }
}

/** Title and message for each way AniList can be unreachable. */
private fun outageText(outage: ApiOutage): Pair<String, String> = when (outage.kind) {
    ApiErrorKind.OFFLINE -> "You're offline" to
        "Check your Wi-Fi or mobile data. We'll reconnect on our own as soon as you're back."
    ApiErrorKind.SERVER -> "AniList is down" to
        "AniList isn't responding right now, so titles, lists and schedules may not load. We'll keep checking."
    ApiErrorKind.RATE_LIMITED -> "AniList is busy" to
        "Too many requests are hitting AniList right now. Things will load again in a moment."
    else -> "Can't reach AniList" to
        "The connection to AniList is slow or keeps dropping. We'll keep trying."
}

/** The technical line shown under "Details": the HTTP code and the server's own message, when there are any. */
private fun outageDetails(outage: ApiOutage): String {
    val parts = listOfNotNull(
        outage.httpCode?.let { "HTTP $it" },
        outage.kind.name.lowercase().replace('_', ' '),
        outage.detail?.takeIf { it.isNotBlank() && !it.equals(outage.kind.name, ignoreCase = true) },
    )
    return parts.joinToString(" · ")
}
