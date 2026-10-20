package com.ansu.anime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ansu.anime.core.net.ApiErrorKind
import com.ansu.anime.core.net.ApiOutage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the card quietly asks AniList whether it is back, while it is showing. */
private const val RECHECK_INTERVAL_MS = 30_000L

/**
 * The "AniList is down" notice: [ErrorGlassCard] sliding in from the top over whichever screen is open.
 *
 * It appears when the error handler reports an outage, checks again by itself every
 * [RECHECK_INTERVAL_MS], and slides away on its own once AniList answers (the outage then becomes
 * null). Dismissing it hides it for that kind of outage only; it comes back if the problem changes
 * (say, from slow to offline) or returns after a recovery. Retry runs [onRetry] right away and
 * shows "Checking…" until it finishes.
 */
@Composable
fun ApiOutageOverlay(
    outage: ApiOutage?,
    onRetry: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dismissedKind by rememberSaveable { mutableStateOf<String?>(null) }
    var retrying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Kept after the outage ends so the card has its text while it slides out.
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
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(280)) { -it } + fadeIn(tween(280)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(220)),
    ) {
        val shown = lastShown ?: return@AnimatedVisibility
        val text = outageText(shown)
        ErrorGlassCard(
            title = text.first,
            message = text.second,
            details = outageDetails(shown),
            retryLabel = if (retrying) "Checking…" else "Retry",
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
        )
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
