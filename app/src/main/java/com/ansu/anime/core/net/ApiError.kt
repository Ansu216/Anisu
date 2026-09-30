package com.ansu.anime.core.net

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** What went wrong with a network/API call, coarse enough to pick a message and a retry policy. */
enum class ApiErrorKind {
    /** No connection at all (no DNS, connection refused, no route). */
    OFFLINE,

    /** The connection broke part-way (reset, TLS failure, other I/O error). */
    NETWORK,
    TIMEOUT,

    /** HTTP 429. */
    RATE_LIMITED,

    /** HTTP 5xx. */
    SERVER,

    /** HTTP 401/403, e.g. an expired AniList token. */
    UNAUTHORIZED,
    NOT_FOUND,

    /** Other HTTP 4xx, including GraphQL validation errors. */
    BAD_REQUEST,

    /** The server answered, but not with something we could read. */
    PARSE,
    UNKNOWN,
}

/** The single exception type the network layer throws; everything else is converted with [toApiException]. */
class ApiException(
    val kind: ApiErrorKind,
    val httpCode: Int? = null,
    /** Seconds the server asked us to wait (`Retry-After`), when it said. */
    val retryAfterSeconds: Long? = null,
    detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: kind.name, cause) {

    /** Short, user-facing sentence for a snackbar or an inline error. */
    val userMessage: String
        get() = when (kind) {
            ApiErrorKind.OFFLINE -> "You're offline. Check your connection and try again."
            ApiErrorKind.NETWORK -> "Network problem. Please try again."
            ApiErrorKind.TIMEOUT -> "The server took too long to respond. Please try again."
            ApiErrorKind.RATE_LIMITED -> "Too many requests. Wait a moment and try again."
            ApiErrorKind.SERVER -> "The server is having trouble right now. Try again shortly."
            ApiErrorKind.UNAUTHORIZED -> "Your AniList session is no longer valid. Sign in again from My Space."
            ApiErrorKind.NOT_FOUND -> "That title couldn't be found."
            ApiErrorKind.BAD_REQUEST -> "The server rejected that request."
            ApiErrorKind.PARSE -> "The server sent a response we couldn't read."
            ApiErrorKind.UNKNOWN -> "Something went wrong. Please try again."
        }

    /**
     * Whether repeating the same request straight away can plausibly succeed. A rate limit is only
     * worth waiting out when the server asks for a short wait; everything not listed will fail the
     * same way again.
     */
    fun isRetryable(): Boolean = when (kind) {
        ApiErrorKind.RATE_LIMITED -> (retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS) <= MAX_AUTO_WAIT_SECONDS
        ApiErrorKind.SERVER, ApiErrorKind.TIMEOUT, ApiErrorKind.NETWORK -> true
        else -> false
    }

    companion object {
        const val DEFAULT_RETRY_AFTER_SECONDS = 2L
        const val MAX_AUTO_WAIT_SECONDS = 10L
    }
}

/** Maps an HTTP status (plus the optional `Retry-After` header and server message) to an [ApiException]. */
fun httpApiException(code: Int, retryAfterSeconds: Long?, detail: String?): ApiException {
    val kind = when {
        code == 429 -> ApiErrorKind.RATE_LIMITED
        code == 401 || code == 403 -> ApiErrorKind.UNAUTHORIZED
        code == 404 -> ApiErrorKind.NOT_FOUND
        code in 500..599 -> ApiErrorKind.SERVER
        code in 400..499 -> ApiErrorKind.BAD_REQUEST
        else -> ApiErrorKind.UNKNOWN
    }
    return ApiException(kind, httpCode = code, retryAfterSeconds = retryAfterSeconds, detail = detail)
}

/** Turns anything a network call can throw into an [ApiException]. */
fun Throwable.toApiException(): ApiException = when (this) {
    is ApiException -> this
    is UnknownHostException, is ConnectException, is NoRouteToHostException ->
        ApiException(ApiErrorKind.OFFLINE, detail = message, cause = this)
    is SocketTimeoutException -> ApiException(ApiErrorKind.TIMEOUT, detail = message, cause = this)
    is IOException -> ApiException(ApiErrorKind.NETWORK, detail = message, cause = this)
    is SerializationException, is IllegalArgumentException ->
        ApiException(ApiErrorKind.PARSE, detail = message, cause = this)
    else -> ApiException(ApiErrorKind.UNKNOWN, detail = message, cause = this)
}
