package eu.kanade.tachiyomi.network

/** Thrown when a request came back with a non-2xx status. */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
