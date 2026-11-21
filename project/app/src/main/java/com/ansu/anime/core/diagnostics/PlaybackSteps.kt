package com.ansu.anime.core.diagnostics

/**
 * Turns one raw playback log line ("  AniLab: 2 hoster(s): Server #1, Server #2", "request failed: GET host/x -> ...")
 * into a short step a person can read ("AniLab: 2 servers found"). Null for lines that are only for the log
 * (casting, ids, URLs), so a live status never shows noise. Used by the buffering screen and the Extensions test.
 */
fun friendlyPlaybackStep(raw: String): String? {
    val line = raw.trim()
    if (line.isEmpty() || line.startsWith("Cast:")) return null
    return when {
        line.startsWith("Resolving sources for") -> "Looking for sources…"
        line.startsWith("Episode list loaded for") ->
            EPISODES.find(line)?.groupValues?.get(1)?.let { "Found $it episode${if (it == "1") "" else "s"}" }
        line.startsWith("Playing download") -> "Opening your download…"
        line.startsWith("Playing the stream picked") -> "Opening the stream you picked…"
        line.startsWith("Source selected") -> "Opening the stream…"
        line.startsWith("Starting with the stream picked by hand") -> "Using the stream you picked before…"
        line.startsWith("Quality is Any") -> "Starting on the first stream that answers…"
        line.startsWith("No playable sources found") -> "No playable sources found"
        line.startsWith("Failed to load sources") -> "Loading sources failed"
        line.startsWith("Player error") -> "The player hit an error"
        line.startsWith("Racing ") -> RACING.find(line)?.groupValues?.get(1)?.let { "Trying $it sources at once…" }
        line.startsWith("Skip times from the source") -> "Skip times came with the source"
        line.startsWith("Probed ") -> "Checking what kind of stream it is…"
        line.startsWith("Detected MIME type") -> when {
            line.contains("mpegurl", ignoreCase = true) -> "Reading the stream (HLS)…"
            line.contains("dash", ignoreCase = true) -> "Reading the stream (DASH)…"
            line.contains("mp4", ignoreCase = true) -> "Reading the stream (MP4)…"
            else -> "Reading the stream…"
        }
        line.startsWith("AniSkip:") -> SEGMENTS.find(line)?.groupValues?.get(1)?.takeIf { it != "0" }?.let { "Skip times: $it found" }
        line.startsWith("request failed:") -> requestFailure(line)
        else -> HOSTERS.find(line)?.let { "${it.groupValues[1]}: ${it.groupValues[2]} server${if (it.groupValues[2] == "1") "" else "s"} found" }
            ?: RESOLVED.find(line)?.let { "${it.groupValues[1]} · ${it.groupValues[2]}: ${it.groupValues[3]} stream${if (it.groupValues[3] == "1") "" else "s"}" }
            ?: NO_VIDEOS.find(line)?.let { "${it.groupValues[1]}: no streams" }
            ?: TIMEOUT.find(line)?.let { "${it.groupValues[1]}: took too long" }
            ?: ANSWERED_FIRST.find(line)?.let { "${it.groupValues[1]} answered first" }
            ?: RESOLVE_FAILED.find(line)?.let { "${it.groupValues[1]} failed, trying the next" }
            ?: SOURCES_FOUND.find(line)?.let { "${it.groupValues[1]} source${if (it.groupValues[1] == "1") "" else "s"} ready" }
            ?: NOT_READY.find(line)?.let { "${it.groupValues[1]} was too slow, trying the next" }
    }
}

private fun requestFailure(line: String): String? {
    val match = REQUEST_FAILED.find(line) ?: return null
    val host = match.groupValues[1]
    val why = match.groupValues[2]
    return when {
        // A request dropped because another source answered first, or the person left: not a problem.
        why.contains("Canceled", ignoreCase = true) -> null
        why.contains("UnknownHost", ignoreCase = true) -> "$host can't be found"
        why.contains("Timeout", ignoreCase = true) || why.contains("timed out", ignoreCase = true) -> "$host timed out"
        why.startsWith("HTTP 5") -> "$host is down (${why.trim()})"
        else -> "$host didn't answer"
    }
}

private val EPISODES = Regex(""": (\d+) episode""")
private val RACING = Regex("""Racing (\d+) sources""")
private val SEGMENTS = Regex("""(\d+) segment""")
private val REQUEST_FAILED = Regex("""request failed: GET ([^/\s]+)\S* -> (.*)""")
private val HOSTERS = Regex("""^(.+?): (\d+) hoster\(s\)""")
private val RESOLVED = Regex("""^(.+?) / (.+?): (\d+) video\(s\)""")
private val NO_VIDEOS = Regex("""^(.+?): the source returned no videos""")
private val TIMEOUT = Regex("""^(.+?): timeout""")
private val ANSWERED_FIRST = Regex("""^'(.+)' answered first""")
private val RESOLVE_FAILED = Regex("""^(?:Resolving )?'(.+?)'(?: failed| could not be resolved)""")
private val SOURCES_FOUND = Regex("""^(\d+) source\(s\) found""")
private val NOT_READY = Regex("""^'(.+?)' did not get ready""")
