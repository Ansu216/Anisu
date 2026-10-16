package com.ansu.anime.data.prefs

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * User-facing appearance settings, persisted in plain SharedPreferences
 * (nothing sensitive here) and exposed as a StateFlow so every screen —
 * the real bottom bar and the live preview in Settings — updates instantly.
 */
class AppearancePrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("ansu_appearance", Context.MODE_PRIVATE)

    private val _navBarRoundness = MutableStateFlow(
        prefs.getFloat(KEY_NAV_ROUNDNESS, DEFAULT_NAV_ROUNDNESS).coerceIn(0f, 1f),
    )

    /** 0f = square corners, 1f = fully rounded pill. */
    val navBarRoundness: StateFlow<Float> = _navBarRoundness

    fun setNavBarRoundness(value: Float) {
        val v = value.coerceIn(0f, 1f)
        _navBarRoundness.value = v
        prefs.edit().putFloat(KEY_NAV_ROUNDNESS, v).apply()
    }

    private val _navBarFrostiness = MutableStateFlow(
        prefs.getFloat(KEY_NAV_FROSTINESS, DEFAULT_NAV_FROSTINESS).coerceIn(0f, 1f),
    )

    /** Frostiness / opacity: 0f = clear glass (background shows through), 1f = fully opaque solid bar. */
    val navBarFrostiness: StateFlow<Float> = _navBarFrostiness

    fun setNavBarFrostiness(value: Float) {
        val v = value.coerceIn(0f, 1f)
        _navBarFrostiness.value = v
        prefs.edit().putFloat(KEY_NAV_FROSTINESS, v).apply()
    }

    private val _navBarBackdropBlur = MutableStateFlow(prefs.getBoolean(KEY_NAV_BACKDROP_BLUR, DEFAULT_NAV_BACKDROP_BLUR))

    /**
     * Frosted blur of whatever scrolls behind the bottom bar. On Android 12+ it is a real blur of the screen
     * content; on Android 8-11, which have no such effect, the bar simply keeps its frosted glass.
     */
    val navBarBackdropBlur: StateFlow<Boolean> = _navBarBackdropBlur

    fun setNavBarBackdropBlur(value: Boolean) {
        _navBarBackdropBlur.value = value
        prefs.edit().putBoolean(KEY_NAV_BACKDROP_BLUR, value).apply()
    }

    private val _titleLanguage = MutableStateFlow(
        TitleLanguage.fromKey(prefs.getString(KEY_TITLE_LANGUAGE, null)),
    )

    /** Which title (and matching artwork) the app shows for every anime. */
    val titleLanguage: StateFlow<TitleLanguage> = _titleLanguage

    fun setTitleLanguage(value: TitleLanguage) {
        _titleLanguage.value = value
        prefs.edit().putString(KEY_TITLE_LANGUAGE, value.key).apply()
    }

    private val _accent = MutableStateFlow(AccentPalette.fromKey(prefs.getString(KEY_ACCENT, null)))

    /** Colour used for the white pills, play buttons and the player's progress bar. */
    val accent: StateFlow<AccentPalette> = _accent

    init {
        AnsuColors.setAccent(_accent.value.color)
    }

    fun setAccent(value: AccentPalette) {
        _accent.value = value
        AnsuColors.setAccent(value.color)
        prefs.edit().putString(KEY_ACCENT, value.key).apply()
    }

    private val _searchGrid = MutableStateFlow(prefs.getBoolean(KEY_SEARCH_GRID, true))

    /** Search results as a poster grid (true) or a list (false); remembered across screens and restarts. */
    val searchGrid: StateFlow<Boolean> = _searchGrid

    fun setSearchGrid(value: Boolean) {
        _searchGrid.value = value
        prefs.edit().putBoolean(KEY_SEARCH_GRID, value).apply()
    }

    private val _mySpaceTab = MutableStateFlow(prefs.getString(KEY_MY_SPACE_TAB, null) ?: "Watching")

    /** Name of the My Space list chip that was last open. */
    val mySpaceTab: StateFlow<String> = _mySpaceTab

    fun setMySpaceTab(name: String) {
        _mySpaceTab.value = name
        prefs.edit().putString(KEY_MY_SPACE_TAB, name).apply()
    }

    companion object {
        private const val KEY_ACCENT = "accent_palette"
        private const val KEY_SEARCH_GRID = "search_grid_layout"
        private const val KEY_MY_SPACE_TAB = "my_space_tab"
        private const val KEY_TITLE_LANGUAGE = "title_language"

        private const val KEY_NAV_ROUNDNESS = "nav_bar_roundness"
        const val DEFAULT_NAV_ROUNDNESS = 0.7f // ≈ the previous fixed 24dp corners

        private const val KEY_NAV_FROSTINESS = "nav_bar_frostiness"
        const val DEFAULT_NAV_FROSTINESS = 0.5f // = the previous fixed 0.5 tint alpha

        private const val KEY_NAV_BACKDROP_BLUR = "nav_bar_backdrop_blur"
        const val DEFAULT_NAV_BACKDROP_BLUR = true
    }
}

/** Language of anime titles: AniList's romanised Japanese title, or its official English one. */
enum class TitleLanguage(val key: String, val label: String) {
    ROMAJI("romaji", "Romaji"),
    ENGLISH("english", "English");

    companion object {
        fun fromKey(key: String?): TitleLanguage = entries.firstOrNull { it.key == key } ?: ROMAJI
    }
}

/** Accent colour choices; White is the original look. */
enum class AccentPalette(val key: String, val label: String, val color: Color) {
    WHITE("white", "White", Color(0xFFFFFFFF)),
    SKY("sky", "Sky", Color(0xFF2D9CDB)),
    MINT("mint", "Mint", Color(0xFF1FAF7A)),
    LIME("green", "Green", Color(0xFF3BA935)),
    GOLD("amber", "Amber", Color(0xFFD98E00)),
    ORANGE("orange", "Orange", Color(0xFFF2792B)),
    ROSE("rose", "Rose", Color(0xFFE8456B)),
    VIOLET("violet", "Violet", Color(0xFF7C5CE6));

    companion object {
        fun fromKey(key: String?): AccentPalette = entries.firstOrNull { it.key == key } ?: WHITE
    }
}
