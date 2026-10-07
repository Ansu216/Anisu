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

    private val _subtitleSize = MutableStateFlow(prefs.getInt(KEY_SUBTITLE_SIZE, DEFAULT_SUBTITLE_SIZE).coerceIn(SUBTITLE_SIZE_RANGE))

    /** Subtitle text size in sp (player side panel > Subtitles). */
    val subtitleSize: StateFlow<Int> = _subtitleSize

    fun setSubtitleSize(value: Int) {
        val clean = value.coerceIn(SUBTITLE_SIZE_RANGE)
        _subtitleSize.value = clean
        prefs.edit().putInt(KEY_SUBTITLE_SIZE, clean).apply()
    }

    private val _subtitleHeight = MutableStateFlow(prefs.getInt(KEY_SUBTITLE_HEIGHT, DEFAULT_SUBTITLE_HEIGHT).coerceIn(SUBTITLE_HEIGHT_RANGE))

    /** How far the subtitles sit above the bottom edge of the video, in percent of the video height. */
    val subtitleHeight: StateFlow<Int> = _subtitleHeight

    fun setSubtitleHeight(value: Int) {
        val clean = value.coerceIn(SUBTITLE_HEIGHT_RANGE)
        _subtitleHeight.value = clean
        prefs.edit().putInt(KEY_SUBTITLE_HEIGHT, clean).apply()
    }

    private val _subtitleColor = MutableStateFlow(prefs.getInt(KEY_SUBTITLE_COLOR, DEFAULT_SUBTITLE_COLOR))

    /** Subtitle text colour (ARGB). */
    val subtitleColor: StateFlow<Int> = _subtitleColor

    fun setSubtitleColor(value: Int) {
        _subtitleColor.value = value
        prefs.edit().putInt(KEY_SUBTITLE_COLOR, value).apply()
    }

    private val _subtitleBgColor = MutableStateFlow(prefs.getInt(KEY_SUBTITLE_BG_COLOR, DEFAULT_SUBTITLE_BG_COLOR))

    /** Colour behind the subtitle text (ARGB; how see-through it is comes from [subtitleBgOpacity]). */
    val subtitleBgColor: StateFlow<Int> = _subtitleBgColor

    fun setSubtitleBgColor(value: Int) {
        _subtitleBgColor.value = value
        prefs.edit().putInt(KEY_SUBTITLE_BG_COLOR, value).apply()
    }

    private val _subtitleBgOpacity = MutableStateFlow(prefs.getInt(KEY_SUBTITLE_BG_OPACITY, DEFAULT_SUBTITLE_BG_OPACITY).coerceIn(0, 100))

    /** Density of the subtitle background in percent: 0 = none, 100 = solid. */
    val subtitleBgOpacity: StateFlow<Int> = _subtitleBgOpacity

    fun setSubtitleBgOpacity(value: Int) {
        val clean = value.coerceIn(0, 100)
        _subtitleBgOpacity.value = clean
        prefs.edit().putInt(KEY_SUBTITLE_BG_OPACITY, clean).apply()
    }

    /** Puts size, height, colours and background back to their defaults. */
    fun resetSubtitleStyle() {
        setSubtitleSize(DEFAULT_SUBTITLE_SIZE)
        setSubtitleHeight(DEFAULT_SUBTITLE_HEIGHT)
        setSubtitleColor(DEFAULT_SUBTITLE_COLOR)
        setSubtitleBgColor(DEFAULT_SUBTITLE_BG_COLOR)
        setSubtitleBgOpacity(DEFAULT_SUBTITLE_BG_OPACITY)
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
        val SUBTITLE_SIZE_RANGE = 10..40
        val SUBTITLE_HEIGHT_RANGE = 0..40
        const val DEFAULT_SUBTITLE_SIZE = 18
        const val DEFAULT_SUBTITLE_HEIGHT = 8
        const val DEFAULT_SUBTITLE_COLOR = 0xFFFFFFFF.toInt()
        const val DEFAULT_SUBTITLE_BG_COLOR = 0xFF000000.toInt()
        const val DEFAULT_SUBTITLE_BG_OPACITY = 50
        const val DEFAULT_SKIP_SECONDS = 15 // the buttons were fixed at 15s before this was a setting

        private const val KEY_DOUBLE_TAP = "double_tap_seek"
        private const val KEY_BRIGHTNESS = "brightness_gesture"
        private const val KEY_VOLUME = "volume_gesture"
        private const val KEY_SKIP_SECONDS = "skip_seconds"
        private const val KEY_SOURCE_PRIORITY = "source_priority"
        private const val KEY_SUBTITLE_SIZE = "subtitle_size"
        private const val KEY_SUBTITLE_HEIGHT = "subtitle_height"
        private const val KEY_SUBTITLE_COLOR = "subtitle_color"
        private const val KEY_SUBTITLE_BG_COLOR = "subtitle_bg_color"
        private const val KEY_SUBTITLE_BG_OPACITY = "subtitle_bg_opacity"
    }
}
