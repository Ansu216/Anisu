package com.ansu.anime.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings under Settings > Player and streaming. Plain SharedPreferences exposed as StateFlows so the
 * player and the settings screen stay in sync live.
 */
class PlayerPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("ansu_player", Context.MODE_PRIVATE)

    private val _doubleTapSeek = MutableStateFlow(prefs.getBoolean(KEY_DOUBLE_TAP, true))

    /** Double-tap the left/right side of the video to skip back/forward. */
    val doubleTapSeek: StateFlow<Boolean> = _doubleTapSeek

    fun setDoubleTapSeek(value: Boolean) {
        _doubleTapSeek.value = value
        prefs.edit().putBoolean(KEY_DOUBLE_TAP, value).apply()
    }

    private val _brightnessGesture = MutableStateFlow(prefs.getBoolean(KEY_BRIGHTNESS, true))

    /** Swipe up/down on the left half of the video to change brightness. */
    val brightnessGesture: StateFlow<Boolean> = _brightnessGesture

    fun setBrightnessGesture(value: Boolean) {
        _brightnessGesture.value = value
        prefs.edit().putBoolean(KEY_BRIGHTNESS, value).apply()
    }

    private val _volumeGesture = MutableStateFlow(prefs.getBoolean(KEY_VOLUME, true))

    /** Swipe up/down on the right half of the video to change volume. */
    val volumeGesture: StateFlow<Boolean> = _volumeGesture

    fun setVolumeGesture(value: Boolean) {
        _volumeGesture.value = value
        prefs.edit().putBoolean(KEY_VOLUME, value).apply()
    }

    private val _skipSeconds = MutableStateFlow(prefs.getInt(KEY_SKIP_SECONDS, DEFAULT_SKIP_SECONDS).let { if (it in SKIP_OPTIONS) it else DEFAULT_SKIP_SECONDS })

    /** Seconds jumped by a double tap and by the back/forward buttons on the controls. */
    val skipSeconds: StateFlow<Int> = _skipSeconds

    fun setSkipSeconds(value: Int) {
        if (value !in SKIP_OPTIONS) return
        _skipSeconds.value = value
        prefs.edit().putInt(KEY_SKIP_SECONDS, value).apply()
    }

    private val _sourcePriority = MutableStateFlow(
        prefs.getString(KEY_SOURCE_PRIORITY, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() },
    )

    /** Source ids in the order the person ranked them, most preferred first. Empty = no preference. */
    val sourcePriority: StateFlow<List<Long>> = _sourcePriority

    fun setSourcePriority(ids: List<Long>) {
        val clean = ids.distinct()
        _sourcePriority.value = clean
        prefs.edit().putString(KEY_SOURCE_PRIORITY, clean.joinToString(",")).apply()
    }

    /** Lower is better. Sources the person never ranked come after every ranked one. */
    fun rankOf(sourceId: Long?): Int {
        val list = _sourcePriority.value
        val index = if (sourceId == null) -1 else list.indexOf(sourceId)
        return if (index >= 0) index else list.size
    }

    companion object {
        val SKIP_OPTIONS = listOf(5, 10, 15)
        const val DEFAULT_SKIP_SECONDS = 15 // the buttons were fixed at 15s before this was a setting

        private const val KEY_DOUBLE_TAP = "double_tap_seek"
        private const val KEY_BRIGHTNESS = "brightness_gesture"
        private const val KEY_VOLUME = "volume_gesture"
        private const val KEY_SKIP_SECONDS = "skip_seconds"
        private const val KEY_SOURCE_PRIORITY = "source_priority"
    }
}
