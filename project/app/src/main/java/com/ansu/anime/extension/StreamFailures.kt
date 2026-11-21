package com.ansu.anime.extension

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers, for a few minutes and for this run of the app only, which source and server just failed to stream.
 * Automatic source picking and the fall-through to the next source skip those first, so a dead server is not
 * tried again on every episode while it is still down. A stream that plays clears its mark again.
 */
object StreamFailures {
    private const val TTL_MS = 5L * 60_000L
    private val failedAt = ConcurrentHashMap<String, Long>()

    private fun key(sourceId: Long?, server: String?) = "${sourceId ?: -1L}|${server.orEmpty()}"

    fun mark(sourceId: Long?, server: String?) {
        failedAt[key(sourceId, server)] = System.currentTimeMillis()
    }

    fun clear(sourceId: Long?, server: String?) {
        failedAt.remove(key(sourceId, server))
    }

    fun isRecentlyFailed(sourceId: Long?, server: String?): Boolean {
        val k = key(sourceId, server)
        val at = failedAt[k] ?: return false
        if (System.currentTimeMillis() - at > TTL_MS) {
            failedAt.remove(k)
            return false
        }
        return true
    }
}
