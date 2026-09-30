package com.ansu.anime.core.net

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The one place API failures end up. Repositories call [report] instead of swallowing an exception:
 * it is logged, converted to an [ApiException] and, unless the call was best-effort, published on
 * [events] so the UI (a snackbar in `MainActivity`) can tell the user why something did not load.
 *
 * A screen that fails several requests at once would otherwise stack identical snackbars, so the same
 * [ApiErrorKind] is only published again after [REPEAT_WINDOW_MS].
 */
class ApiErrorHandler {

    private val _events = MutableSharedFlow<ApiException>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<ApiException> = _events

    private var lastKind: ApiErrorKind? = null
    private var lastAt = 0L

    /**
     * Logs [error] for [what] and returns it as an [ApiException]. Pass [quiet] = true for optional
     * data (episode thumbnails, background progress uploads) that should never interrupt the user.
     */
    fun report(what: String, error: Throwable, quiet: Boolean = false): ApiException {
        val apiError = error.toApiException()
        Log.w(TAG, "$what failed: ${apiError.kind}${apiError.httpCode?.let { " (HTTP $it)" }.orEmpty()} ${apiError.message.orEmpty()}")
        if (!quiet && shouldPublish(apiError.kind)) _events.tryEmit(apiError)
        return apiError
    }

    @Synchronized
    private fun shouldPublish(kind: ApiErrorKind): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (kind == lastKind && now - lastAt < REPEAT_WINDOW_MS) return false
        lastKind = kind
        lastAt = now
        return true
    }

    private companion object {
        const val TAG = "ApiErrorHandler"
        const val REPEAT_WINDOW_MS = 10_000L
    }
}
