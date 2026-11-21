package com.ansu.anime.ui.sources

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** How big a stream is. [estimated] is true for HLS, which has no single file: the size is worked out from its playlist. */
class StreamSize(val bytes: Long, val estimated: Boolean) {
    /** "845 MB", "1.2 GB", with a leading "~" when it is an estimate. */
    fun label(): String {
        val mb = bytes / 1_048_576.0
        val text = if (mb >= 1000) String.format(Locale.US, "%.1f GB", mb / 1024) else String.format(Locale.US, "%d MB", mb.roundToInt().coerceAtLeast(1))
        return if (estimated) "~$text" else text
    }
}

/**
 * Finds out how large a stream is without downloading it, for the manual source picker.
 *  - A plain video file: its Content-Length (a HEAD request, or a one-byte ranged GET when HEAD is refused). Exact.
 *  - HLS: a master playlist is read for the variant that matches the stream's resolution (its average bandwidth) and
 *    that variant's playlist for the running time; a media playlist is sized from its first segment's byte length times
 *    the number of segments. Both are estimates.
 * Anything that cannot be worked out (a stream that is not resolved yet, a blocked host, a timeout) gives null.
 */
class StreamSizeProbe(client: OkHttpClient) {
    private val client = client.newBuilder()
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** The answer already found for [url] this session, so reopening the picker shows it at once. */
    fun cached(url: String): StreamSize? = known[url]

    suspend fun sizeOf(url: String, headers: Map<String, String>, heightHint: Int?): StreamSize? {
        known[url]?.let { return it }
        val size = withContext(Dispatchers.IO) { runCatching { measure(url, headers, heightHint) }.getOrNull() }
        if (size != null) known[url] = size
        return size
    }

    private fun measure(url: String, headers: Map<String, String>, heightHint: Int?): StreamSize? {
        if (looksLikeHls(url)) return hlsSize(url, headers, heightHint)
        val head = head(url, headers)
        if (head.hls) return hlsSize(url, headers, heightHint)
        head.length?.let { return StreamSize(it, estimated = false) }
        return rangedLength(url, headers)?.let { StreamSize(it, estimated = false) }
    }

    private fun looksLikeHls(url: String) = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) || ".m3u8" in url

    private fun request(url: String, headers: Map<String, String>, extra: Request.Builder.() -> Unit = {}): Request =
        Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
            if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) header("User-Agent", USER_AGENT)
            extra()
        }.build()

    private class Head(val length: Long?, val hls: Boolean)

    private fun head(url: String, headers: Map<String, String>): Head =
        client.newCall(request(url, headers) { head() }).execute().use { r ->
            val type = r.header("Content-Type").orEmpty().lowercase()
            val hls = "mpegurl" in type || "m3u8" in type
            Head(if (r.isSuccessful) r.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 } else null, hls)
        }

    /** Total size from a one-byte ranged GET, for servers that refuse HEAD. */
    private fun rangedLength(url: String, headers: Map<String, String>): Long? =
        client.newCall(request(url, headers) { header("Range", "bytes=0-0") }).execute().use { r ->
            r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.takeIf { it > 0 }
                ?: if (r.code == 200) r.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 } else null
        }

    private fun fetchText(url: String, headers: Map<String, String>): String? =
        client.newCall(request(url, headers)).execute().use { r ->
            val body = r.body
            if (!r.isSuccessful || body == null || body.contentLength() > 2_000_000) null else body.string()
        }

    private class Variant(val url: String, val bandwidth: Long, val height: Int?)

    private fun hlsSize(url: String, headers: Map<String, String>, heightHint: Int?): StreamSize? {
        val text = fetchText(url, headers) ?: return null
        if (!text.contains("#EXTM3U")) return null
        if (text.contains("#EXT-X-STREAM-INF")) {
            val variants = variants(text, url).filter { it.bandwidth > 0 }
            val chosen = (if (heightHint != null && variants.any { it.height != null }) {
                variants.filter { it.height != null }.minByOrNull { abs(it.height!! - heightHint) }
            } else {
                variants.maxByOrNull { it.bandwidth }
            }) ?: return null
            val seconds = segments(fetchText(chosen.url, headers) ?: return null, chosen.url).sumOf { it.first.toDouble() }
            if (seconds <= 0) return null
            return StreamSize((chosen.bandwidth / 8.0 * seconds).toLong(), estimated = true)
        }
        val list = segments(text, url)
        val first = list.firstOrNull() ?: return null
        if (first.first <= 0f) return null
        val bytes = head(first.second, headers).length ?: rangedLength(first.second, headers) ?: return null
        val seconds = list.sumOf { it.first.toDouble() }
        return StreamSize((bytes / first.first * seconds).toLong(), estimated = true)
    }

    private fun variants(playlist: String, base: String): List<Variant> {
        val lines = playlist.lines().map { it.trim() }
        val out = mutableListOf<Variant>()
        lines.forEachIndexed { i, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
            val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@forEachIndexed
            val bandwidth = AVERAGE_BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull()
                ?: BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val height = RESOLUTION.find(line)?.groupValues?.get(1)?.toIntOrNull()
            out += Variant(resolve(base, uri), bandwidth, height)
        }
        return out
    }

    /** Every segment of a media playlist as (seconds, url). */
    private fun segments(playlist: String, base: String): List<Pair<Float, String>> {
        val lines = playlist.lines().map { it.trim() }
        val out = mutableListOf<Pair<Float, String>>()
        lines.forEachIndexed { i, line ->
            if (!line.startsWith("#EXTINF:")) return@forEachIndexed
            val seconds = line.removePrefix("#EXTINF:").substringBefore(',').trim().toFloatOrNull() ?: return@forEachIndexed
            val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@forEachIndexed
            out += seconds to resolve(base, uri)
        }
        return out
    }

    private fun resolve(base: String, ref: String): String =
        runCatching { java.net.URI(base).resolve(ref).toString() }.getOrDefault(ref)

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        val BANDWIDTH = Regex("""(?<![A-Z-])BANDWIDTH=(\d+)""")
        val AVERAGE_BANDWIDTH = Regex("""AVERAGE-BANDWIDTH=(\d+)""")
        val RESOLUTION = Regex("""RESOLUTION=\d+x(\d+)""")
        val known = ConcurrentHashMap<String, StreamSize>()
    }
}
