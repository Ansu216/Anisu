package com.ansu.anime.extension

import android.content.Context

/**
 * Remembers the outcome of the last Extensions test per source, so the list can show which sources stream and which
 * do not without testing again. Stored as `ok|time|note` under the source id.
 */
class SourceHealth(context: Context) {
    private val prefs = context.getSharedPreferences("source_health", Context.MODE_PRIVATE)

    fun record(sourceId: Long, ok: Boolean, note: String?) {
        val clean = note.orEmpty().replace('|', '/').replace('\n', ' ').take(160)
        prefs.edit().putString(sourceId.toString(), "${if (ok) 1 else 0}|${System.currentTimeMillis()}|$clean").apply()
    }

    /** True/false from the last test, null when the source was never tested. */
    fun isWorking(sourceId: Long): Boolean? =
        prefs.getString(sourceId.toString(), null)?.substringBefore('|')?.let { it == "1" }

    /** Short text for the sources list, e.g. "Streams OK · 3d ago" or "Streaming failed · 2h ago"; null if untested. */
    fun label(sourceId: Long): String? {
        val parts = prefs.getString(sourceId.toString(), null)?.split('|', limit = 3) ?: return null
        if (parts.size < 2) return null
        val ok = parts[0] == "1"
        val ageMs = (System.currentTimeMillis() - (parts[1].toLongOrNull() ?: return null)).coerceAtLeast(0)
        val age = when {
            ageMs < 3_600_000 -> "${ageMs / 60_000}m ago"
            ageMs < 86_400_000 -> "${ageMs / 3_600_000}h ago"
            else -> "${ageMs / 86_400_000}d ago"
        }
        return (if (ok) "Streams OK" else "Streaming failed") + " · $age"
    }
}
