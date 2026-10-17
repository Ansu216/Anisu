package com.ansu.anime.data.notify

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the user asked to be told about, persisted in plain SharedPreferences.
 *
 *  - [followedIds]: shows the user tapped the bell / "Notify me" on. An unreleased one is announced the moment it
 *    releases; one that is airing gets a notification per new episode.
 *  - [newEpisodes]: also announce new episodes of everything on the Watching list, without following each show.
 *  - [enabled]: the master switch for all of it.
 *
 * The worker also keeps, per show, the last status and the last episode it already announced, so a notification is
 * only ever sent once.
 */
class NotificationPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("ansu_notifications", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    private val _newEpisodes = MutableStateFlow(prefs.getBoolean(KEY_NEW_EPISODES, true))
    val newEpisodes: StateFlow<Boolean> = _newEpisodes

    fun setNewEpisodes(value: Boolean) {
        _newEpisodes.value = value
        prefs.edit().putBoolean(KEY_NEW_EPISODES, value).apply()
    }

    private val _followedIds = MutableStateFlow(
        prefs.getStringSet(KEY_FOLLOWED, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
    )
    val followedIds: StateFlow<Set<Int>> = _followedIds

    /**
     * Follows or unfollows [anilistId] and returns whether it is followed now. [status] (AniList's release status,
     * when the screen already knows it) is stored so a release that happens before the first background check is
     * still noticed.
     */
    fun toggleFollow(anilistId: Int, status: String? = null): Boolean {
        val following = anilistId !in _followedIds.value
        val updated = if (following) _followedIds.value + anilistId else _followedIds.value - anilistId
        _followedIds.value = updated
        val editor = prefs.edit().putStringSet(KEY_FOLLOWED, updated.map { it.toString() }.toSet())
        if (following) {
            if (status != null) editor.putString(statusKey(anilistId), status)
        } else {
            editor.remove(statusKey(anilistId)).remove(episodeKey(anilistId))
        }
        editor.apply()
        return following
    }

    fun lastStatus(anilistId: Int): String? = prefs.getString(statusKey(anilistId), null)

    fun setLastStatus(anilistId: Int, status: String) {
        prefs.edit().putString(statusKey(anilistId), status).apply()
    }

    /** The newest episode already announced (or seen as the starting point) for a show; null before the first check. */
    fun lastEpisode(anilistId: Int): Int? = prefs.getInt(episodeKey(anilistId), -1).takeIf { it >= 0 }

    fun setLastEpisode(anilistId: Int, episode: Int) {
        prefs.edit().putInt(episodeKey(anilistId), episode).apply()
    }

    private fun statusKey(id: Int) = "status_$id"
    private fun episodeKey(id: Int) = "episode_$id"

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_NEW_EPISODES = "new_episodes"
        const val KEY_FOLLOWED = "followed_ids"
    }
}
