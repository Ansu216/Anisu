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

    private val _pipEnabled = MutableStateFlow(prefs.getBoolean(KEY_PIP, true))

    /** Picture-in-picture: leaving the app (Home) while a video plays shrinks it into a floating window. */
    val pipEnabled: StateFlow<Boolean> = _pipEnabled

    fun setPipEnabled(value: Boolean) {
        _pipEnabled.value = value
        prefs.edit().putBoolean(KEY_PIP, value).apply()
    }

    private val _preferredQuality = MutableStateFlow(prefs.getInt(KEY_PREFERRED_QUALITY, DEFAULT_QUALITY).let { if (it in QUALITY_OPTIONS) it else DEFAULT_QUALITY })

    /** The resolution (1080, 720, 480 or 360) a stream is picked at when the show starts playing. */
    val preferredQuality: StateFlow<Int> = _preferredQuality

    fun setPreferredQuality(value: Int) {
        if (value !in QUALITY_OPTIONS) return
        _preferredQuality.value = value
        prefs.edit().putInt(KEY_PREFERRED_QUALITY, value).apply()
    }

    private val _preferredLanguage = MutableStateFlow(prefs.getString(KEY_PREFERRED_LANGUAGE, LANGUAGE_SUB).let { if (it == LANGUAGE_DUB) LANGUAGE_DUB else LANGUAGE_SUB })

    /** [LANGUAGE_SUB] or [LANGUAGE_DUB]: which version of an episode is picked when the show starts playing. */
    val preferredLanguage: StateFlow<String> = _preferredLanguage

    fun setPreferredLanguage(value: String) {
        if (value != LANGUAGE_SUB && value != LANGUAGE_DUB) return
        _preferredLanguage.value = value
        prefs.edit().putString(KEY_PREFERRED_LANGUAGE, value).apply()
    }

    /**
     * The stream the person chose by hand in the Sources panel for a show, kept for 3 days like the last played
     * source. It beats the preferred quality and language when the next episode picks its stream.
     */
    fun manualPickFor(anilistId: Int): com.ansu.anime.data.repository.LastPlayedSource? {
        val parts = prefs.getString("$KEY_MANUAL_PICK$anilistId", null)?.split('\t') ?: return null
        val sourceId = parts.getOrNull(0)?.toLongOrNull()
        val savedAt = parts.getOrNull(3)?.toLongOrNull()
        if (parts.size != 4 || sourceId == null || savedAt == null) return null
        if (System.currentTimeMillis() - savedAt > MANUAL_PICK_TTL_MILLIS) {
            prefs.edit().remove("$KEY_MANUAL_PICK$anilistId").apply()
            return null
        }
        return com.ansu.anime.data.repository.LastPlayedSource(sourceId, parts[1].ifEmpty { null }, parts[2].ifEmpty { null })
    }

    fun setManualPick(anilistId: Int, sourceId: Long, serverName: String?, label: String?) {
        // Tabs separate the fields; none of them can hold one.
        fun clean(text: String?) = text.orEmpty().replace('\t', ' ').replace('\n', ' ')
        prefs.edit()
            .putString("$KEY_MANUAL_PICK$anilistId", "$sourceId\t${clean(serverName)}\t${clean(label)}\t${System.currentTimeMillis()}")
            .apply()
    }

    private val _pauseScreen = MutableStateFlow(prefs.getBoolean(KEY_PAUSE_SCREEN, true))

    /** Pause screen: a few seconds after pausing, the video dims and shows what is being watched. */
    val pauseScreen: StateFlow<Boolean> = _pauseScreen

    fun setPauseScreen(value: Boolean) {
        _pauseScreen.value = value
        prefs.edit().putBoolean(KEY_PAUSE_SCREEN, value).apply()
    }

    private val _prefetchEnabled = MutableStateFlow(prefs.getBoolean(KEY_PREFETCH, true))

    /** Pre-load about a minute of the last-watched episode into the media cache when the app opens. */
    val prefetchEnabled: StateFlow<Boolean> = _prefetchEnabled

    fun setPrefetchEnabled(value: Boolean) {
        _prefetchEnabled.value = value
        prefs.edit().putBoolean(KEY_PREFETCH, value).apply()
    }

    private val _episodeGrid = MutableStateFlow(prefs.getBoolean(KEY_EPISODE_GRID, false))

    /** The landscape Episodes panel's style: false = list of cards, true = grid of numbered tiles. */
    val episodeGrid: StateFlow<Boolean> = _episodeGrid

    fun setEpisodeGrid(value: Boolean) {
        _episodeGrid.value = value
        prefs.edit().putBoolean(KEY_EPISODE_GRID, value).apply()
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

    private val _subtitleRoundedBg = MutableStateFlow(prefs.getBoolean(KEY_SUBTITLE_ROUNDED_BG, DEFAULT_SUBTITLE_ROUNDED_BG))

    /** True: a soft rounded bar behind each line of subtitles. False: one plain rectangle box behind the whole cue. */
    val subtitleRoundedBg: StateFlow<Boolean> = _subtitleRoundedBg

    fun setSubtitleRoundedBg(value: Boolean) {
        _subtitleRoundedBg.value = value
        prefs.edit().putBoolean(KEY_SUBTITLE_ROUNDED_BG, value).apply()
    }

    /** Puts size, height, colours and background back to their defaults. */
    fun resetSubtitleStyle() {
        setSubtitleSize(DEFAULT_SUBTITLE_SIZE)
        setSubtitleHeight(DEFAULT_SUBTITLE_HEIGHT)
        setSubtitleColor(DEFAULT_SUBTITLE_COLOR)
        setSubtitleBgColor(DEFAULT_SUBTITLE_BG_COLOR)
        setSubtitleBgOpacity(DEFAULT_SUBTITLE_BG_OPACITY)
        setSubtitleRoundedBg(DEFAULT_SUBTITLE_ROUNDED_BG)
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
        val QUALITY_OPTIONS = listOf(1080, 720, 480, 360)
        const val DEFAULT_QUALITY = 1080
        const val LANGUAGE_SUB = "sub"
        const val LANGUAGE_DUB = "dub"
        val SUBTITLE_SIZE_RANGE = 10..40
        val SUBTITLE_HEIGHT_RANGE = 0..40
        const val DEFAULT_SUBTITLE_SIZE = 18
        const val DEFAULT_SUBTITLE_HEIGHT = 8
        const val DEFAULT_SUBTITLE_COLOR = 0xFFFFFFFF.toInt()
        const val DEFAULT_SUBTITLE_BG_COLOR = 0xFF000000.toInt()
        const val DEFAULT_SUBTITLE_BG_OPACITY = 50
        const val DEFAULT_SUBTITLE_ROUNDED_BG = true
        const val DEFAULT_SKIP_SECONDS = 15 // the buttons were fixed at 15s before this was a setting

        private const val KEY_DOUBLE_TAP = "double_tap_seek"
        private const val KEY_BRIGHTNESS = "brightness_gesture"
        private const val KEY_VOLUME = "volume_gesture"
        private const val KEY_PIP = "pip_enabled"
        private const val KEY_MANUAL_PICK = "manual_pick_"

        /** How long a stream picked by hand is kept: 3 days, the same as the last played source. */
        private const val MANUAL_PICK_TTL_MILLIS = 3L * 24 * 60 * 60 * 1000
        private const val KEY_PREFERRED_QUALITY = "preferred_quality"
        private const val KEY_PREFERRED_LANGUAGE = "preferred_language"
        private const val KEY_PAUSE_SCREEN = "pause_screen"
        private const val KEY_PREFETCH = "prefetch_enabled"
        private const val KEY_EPISODE_GRID = "episode_grid"
        private const val KEY_SKIP_SECONDS = "skip_seconds"
        private const val KEY_SOURCE_PRIORITY = "source_priority"
        private const val KEY_SUBTITLE_SIZE = "subtitle_size"
        private const val KEY_SUBTITLE_HEIGHT = "subtitle_height"
        private const val KEY_SUBTITLE_COLOR = "subtitle_color"
        private const val KEY_SUBTITLE_BG_COLOR = "subtitle_bg_color"
        private const val KEY_SUBTITLE_BG_OPACITY = "subtitle_bg_opacity"
        private const val KEY_SUBTITLE_ROUNDED_BG = "subtitle_rounded_bg"
    }
}
