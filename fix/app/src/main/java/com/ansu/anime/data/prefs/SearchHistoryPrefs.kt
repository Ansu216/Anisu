package com.ansu.anime.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * What the user has searched for, newest first, kept in plain SharedPreferences as a small JSON array.
 * A search counts once: repeating one moves it back to the top instead of adding a copy.
 */
class SearchHistoryPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("ansu_search_history", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())

    /** Newest first, at most [MAX_ITEMS] long. */
    val items: StateFlow<List<String>> = _items

    fun add(query: String) {
        val text = query.trim()
        if (text.length < MIN_LENGTH) return
        update(listOf(text) + _items.value.filterNot { it.equals(text, ignoreCase = true) })
    }

    fun remove(query: String) = update(_items.value.filterNot { it == query })

    fun clear() = update(emptyList())

    private fun update(list: List<String>) {
        val trimmed = list.take(MAX_ITEMS)
        _items.value = trimmed
        prefs.edit().putString(KEY_ITEMS, JSONArray(trimmed).toString()).apply()
    }

    private fun load(): List<String> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }.filter { it.isNotBlank() }.take(MAX_ITEMS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 30

        /** The search screen treats anything shorter as "no text", so it is not worth remembering either. */
        const val MIN_LENGTH = 2
    }
}
