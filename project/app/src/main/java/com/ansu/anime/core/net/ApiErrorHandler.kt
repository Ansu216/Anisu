package com.ansu.anime.core.net

import android.os.SystemClock
import android.util.Log
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * AniList as a whole not answering: what the outage card shows. [kind] is why it looks down
 * (offline, unreachable, timing out, HTTP 5xx or rate limited); [httpCode] and [detail] go in the
 * card's "Details" line.
 */
data class ApiOutage(val kind: ApiErrorKind, val httpCode: Int?, val detail: String?) {
    /** The device has no connection, as opposed to AniList itself being down. */
    val isOffline: Boolean get() = kind == ApiErrorKind.OFFLINE
}

/**
 * The one place API failures end up. Repositories call [report] instead of swallowing an exception:
 * it is logged, converted to an [ApiException] and, unless the call was best-effort, published on
 * [events] so the UI (a snackbar in `MainActivity`) can tell the user why something did not load.
 *
 * A screen that fails several requests at once would otherwise stack identical snackbars, so the same
 * [ApiErrorKind] is only published again after [REPEAT_WINDOW_MS].
 */
class ApiErrorHandler(private val diagnostics: Diagnostics? = null) {

    private val _events = MutableSharedFlow<ApiException>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<ApiException> = _events

    private val _outage = MutableStateFlow<ApiOutage?>(null)

    /** Non-null while AniList looks down; cleared by the next real answer from it. */
    val outage: StateFlow<ApiOutage?> = _outage

    private var lastKind: ApiErrorKind? = null
    private var lastAt = 0L
    private var downStreak = 0

    /**
     * Logs [error] for [what] and returns it as an [ApiException]. Pass [quiet] = true for optional
     * data (episode thumbnails, background progress uploads) that should never interrupt the user.
     */
    fun report(what: String, error: Throwable, quiet: Boolean = false, tracksOutage: Boolean = false): ApiException {
        val apiError = error.toApiException()
        if (tracksOutage) trackOutage(apiError)
        Log.w(TAG, "$what failed: ${apiError.kind}${apiError.httpCode?.let { " (HTTP $it)" }.orEmpty()} ${apiError.message.orEmpty()}")
        diagnostics?.log(
            LogCategory.NETWORK,
            "$what failed: ${apiError.kind}${apiError.httpCode?.let { " (HTTP $it)" }.orEmpty()} ${apiError.message.orEmpty()}",
            if (apiError.kind == ApiErrorKind.UNKNOWN || apiError.kind == ApiErrorKind.PARSE) error else null,
        )
        if (!quiet && shouldPublish(apiError.kind)) _events.tryEmit(apiError)
        return apiError
    }

    /**
     * AniList is called down after [OUTAGE_STREAK] failed calls in a row of a "can't get an answer"
     * kind; the API client has already retried each of those, so two is a confident reading. An
     * error the server sent back on purpose (404, a rejected request) proves it is up, so it
     * resets the count.
     */
    @Synchronized
    private fun trackOutage(error: ApiException) {
        when {
            error.kind in OUTAGE_KINDS -> {
                downStreak++
                if (downStreak >= OUTAGE_STREAK) _outage.value = ApiOutage(error.kind, error.httpCode, error.message)
            }
            // A 401/403, an unreadable page or a rejected request can be an expired session or a bad query,
            // but it is also what AniList sends while it is down. It proves nothing either way here, so it
            // neither counts nor clears; the repository checks with a token-free request ([confirmOutage]).
            error.kind in AMBIGUOUS_KINDS -> Unit
            else -> {
                downStreak = 0
                _outage.value = null
            }
        }
    }

    /** True for failures that may be AniList being down rather than a real problem with the request. */
    fun isAmbiguous(error: ApiException): Boolean = error.kind in AMBIGUOUS_KINDS

    /** A token-free request to AniList also failed, so it really is down: show the outage card at once. */
    @Synchronized
    fun confirmOutage(error: ApiException) {
        downStreak = OUTAGE_STREAK
        _outage.value = ApiOutage(error.kind, error.httpCode, error.message)
    }

    /** AniList answered something: it is up, so any outage ends. */
    @Synchronized
    fun reportServiceUp() {
        downStreak = 0
        if (_outage.value != null) _outage.value = null
    }

    @Synchronized
    private fun shouldPublish(kind: ApiErrorKind): Boolean {
        val now = SystemClock.elapsedRealtime()
        val window = if (kind == ApiErrorKind.RATE_LIMITED) RATE_LIMIT_REPEAT_WINDOW_MS else REPEAT_WINDOW_MS
        if (kind == lastKind && now - lastAt < window) return false
        lastKind = kind
        lastAt = now
        return true
    }

    private companion object {
        const val TAG = "ApiErrorHandler"
        const val REPEAT_WINDOW_MS = 10_000L
        const val RATE_LIMIT_REPEAT_WINDOW_MS = 60_000L
        const val OUTAGE_STREAK = 2
        val AMBIGUOUS_KINDS = setOf(
            ApiErrorKind.UNAUTHORIZED, ApiErrorKind.BAD_REQUEST, ApiErrorKind.PARSE, ApiErrorKind.UNKNOWN,
        )
        val OUTAGE_KINDS = setOf(
            ApiErrorKind.OFFLINE, ApiErrorKind.NETWORK, ApiErrorKind.TIMEOUT,
            ApiErrorKind.SERVER, ApiErrorKind.RATE_LIMITED,
        )
    }
}
