package com.ansu.anime.data.notify

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One notification the app produced, kept so the bell on the home banner can list everything received so far. */
data class NotificationEntry(
    /** Identifies the event ("release:12", "episode:12:5"), so the same one is never listed twice. */
    val key: String,
    val anilistId: Int,
    val title: String,
    val message: String,
    val posterUrl: String?,
    val timeMillis: Long,
    val read: Boolean = false,
)

/**
 * The inbox behind the bell. Every release / new-episode event is written here when it is detected, whether or not
 * the system could show it as a notification (permission off, notifications blocked), so the list is complete.
 * Newest first, capped at [MAX_ENTRIES], stored as JSON in plain SharedPreferences.
 */
class NotificationHistory(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("ansu_notification_history", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<NotificationEntry>> = _entries

    /** Adds an entry unless that exact event is already listed. Returns whether it was new. */
    @Synchronized
    fun add(entry: NotificationEntry): Boolean {
        if (_entries.value.any { it.key == entry.key }) return false
        update((listOf(entry) + _entries.value).take(MAX_ENTRIES))
        return true
    }

    @Synchronized
    fun markAllRead() {
        if (_entries.value.none { !it.read }) return
        update(_entries.value.map { it.copy(read = true) })
    }

    @Synchronized
    fun clear() = update(emptyList())

    private fun update(list: List<NotificationEntry>) {
        _entries.value = list
        val array = JSONArray()
        list.forEach { e ->
            array.put(
                JSONObject()
                    .put("key", e.key)
                    .put("id", e.anilistId)
                    .put("title", e.title)
                    .put("message", e.message)
                    .put("poster", e.posterUrl ?: JSONObject.NULL)
                    .put("time", e.timeMillis)
                    .put("read", e.read),
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun load(): List<NotificationEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ENTRIES, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            NotificationEntry(
                key = o.getString("key"),
                anilistId = o.getInt("id"),
                title = o.getString("title"),
                message = o.getString("message"),
                posterUrl = if (o.isNull("poster")) null else o.getString("poster"),
                timeMillis = o.getLong("time"),
                read = o.optBoolean("read", false),
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 100
    }
}
