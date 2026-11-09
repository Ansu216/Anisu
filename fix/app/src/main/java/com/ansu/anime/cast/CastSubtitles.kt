package com.ansu.anime.cast

import android.content.Context
import android.net.ConnectivityManager
import com.ansu.anime.core.model.SubtitleTrack
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Hands subtitles to the TV. A Chromecast only reads WebVTT, and only from an address that allows cross-origin
 * requests; extension subtitles are usually SRT or ASS behind a Referer check. So the phone fetches each subtitle
 * file itself, converts it to WebVTT and serves it on the local network for the receiver to pick up.
 */
class CastSubtitleServer : NanoHTTPD(0) {

    private class Entry(val url: String, val headers: Map<String, String>) {
        @Volatile
        var vtt: String? = null
    }

    private val entries = ConcurrentHashMap<Int, Entry>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Replaces the files on offer with [tracks]; returns the address of each one, in the same order. */
    fun offer(tracks: List<SubtitleTrack>, headers: Map<String, String>, host: String): List<String> {
        entries.clear()
        return tracks.mapIndexed { index, track ->
            entries[index] = Entry(track.url, headers)
            "http://$host:$listeningPort/subtitle/$index.vtt"
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val response = if (session.method == Method.OPTIONS) {
            newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
        } else {
            val index = session.uri.substringAfterLast('/').substringBefore('.').toIntOrNull()
            val entry = index?.let { entries[it] }
            val vtt = entry?.let(::vttFor)
            if (vtt == null) {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            } else {
                newFixedLengthResponse(Response.Status.OK, "text/vtt; charset=utf-8", vtt)
            }
        }
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Headers", "*")
        return response
    }

    private fun vttFor(entry: Entry): String? {
        entry.vtt?.let { return it }
        val converted = runCatching {
            val request = Request.Builder().url(entry.url)
                .apply { entry.headers.forEach { (name, value) -> header(name, value) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) toWebVtt(response.body.string()) else null
            }
        }.getOrNull()
        entry.vtt = converted
        return converted
    }
}

/** The phone's address on the Wi-Fi it shares with the TV; null when it has none. */
fun lanAddress(context: Context): String? {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val network = manager.activeNetwork ?: return null
    val properties = manager.getLinkProperties(network) ?: return null
    return properties.linkAddresses
        .map { it.address }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        ?.hostAddress
}

private val SrtTimestamp = Regex("""(\d{1,2}:\d{2}:\d{2}),(\d{3})""")
private val AssPosition = Regex("""\{\\an?\d\}""")
private val AssOverride = Regex("""\{[^}]*\}""")

/** Turns WebVTT (kept as is), SubRip or ASS/SSA subtitle text into WebVTT. ASS styling and positioning are dropped. */
fun toWebVtt(raw: String): String {
    val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
    return when {
        text.trimStart().startsWith("WEBVTT") -> text
        text.contains("[Events]", ignoreCase = true) -> assToVtt(text)
        else -> srtToVtt(text)
    }
}

private fun srtToVtt(text: String): String {
    val body = text.lines().joinToString("\n") { line ->
        if (line.contains("-->")) SrtTimestamp.replace(line) { "${it.groupValues[1]}.${it.groupValues[2]}" } else line
    }
    return "WEBVTT\n\n" + AssPosition.replace(body, "").trim() + "\n"
}

private class AssCue(val startMs: Long, val endMs: Long, val text: String)

private fun assToVtt(text: String): String {
    var inEvents = false
    var columns = emptyList<String>()
    val cues = mutableListOf<AssCue>()
    for (line in text.lines()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("[")) {
            inEvents = trimmed.equals("[Events]", ignoreCase = true)
            continue
        }
        if (!inEvents) continue
        if (trimmed.startsWith("Format:", ignoreCase = true)) {
            columns = trimmed.substringAfter(':').split(',').map { it.trim().lowercase(Locale.ROOT) }
            continue
        }
        if (!trimmed.startsWith("Dialogue:", ignoreCase = true) || columns.isEmpty()) continue
        val parts = trimmed.substringAfter(':').trimStart().split(',', limit = columns.size)
        val start = columns.indexOf("start").takeIf { it >= 0 && it < parts.size }?.let { assTime(parts[it]) } ?: continue
        val end = columns.indexOf("end").takeIf { it >= 0 && it < parts.size }?.let { assTime(parts[it]) } ?: continue
        val rawText = columns.indexOf("text").takeIf { it >= 0 && it < parts.size }?.let { parts[it] } ?: continue
        val cleaned = AssOverride.replace(rawText, "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
            .trim()
        if (cleaned.isNotEmpty()) cues += AssCue(start, end, cleaned)
    }
    val out = StringBuilder("WEBVTT\n\n")
    cues.sortedBy { it.startMs }.forEach { cue ->
        out.append(vttTime(cue.startMs)).append(" --> ").append(vttTime(cue.endMs)).append('\n')
        out.append(cue.text).append("\n\n")
    }
    return out.toString()
}

/** "0:01:02.50" (h:mm:ss.cc) as milliseconds; null when it is not a time. */
private fun assTime(value: String): Long? {
    val parts = value.trim().split(':')
    if (parts.size != 3) return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val seconds = parts[2].toDoubleOrNull() ?: return null
    return ((hours * 60 + minutes) * 60) * 1000 + (seconds * 1000).toLong()
}

private fun vttTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d:%02d.%03d", safe / 3_600_000, safe / 60_000 % 60, safe / 1000 % 60, safe % 1000)
}
