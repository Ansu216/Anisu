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

    private val _amoledBlack = MutableStateFlow(prefs.getBoolean(KEY_AMOLED, DEFAULT_AMOLED))

    /** Pure black (#000) app background instead of the near-black default, for AMOLED screens. */
    val amoledBlack: StateFlow<Boolean> = _amoledBlack

    init {
        AnsuColors.setAmoled(_amoledBlack.value)
    }

    fun setAmoledBlack(value: Boolean) {
        _amoledBlack.value = value
        AnsuColors.setAmoled(value)
        prefs.edit().putBoolean(KEY_AMOLED, value).apply()
    }

    private val _backgroundStyle = MutableStateFlow(BackgroundStyle.fromKey(prefs.getString(KEY_BACKGROUND_STYLE, null)))

    /** The animation drifting behind the app's screens (Appearance > Background animation). */
    val backgroundStyle: StateFlow<BackgroundStyle> = _backgroundStyle

    fun setBackgroundStyle(value: BackgroundStyle) {
        _backgroundStyle.value = value
        prefs.edit().putString(KEY_BACKGROUND_STYLE, value.key).apply()
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

    private val _searchSource = MutableStateFlow(prefs.getLong(KEY_SEARCH_SOURCE, NO_SOURCE).takeIf { it != NO_SOURCE })

    /** The source Search is limited to (chosen in the filter sheet); null = off, the normal search. Kept across restarts. */
    val searchSource: StateFlow<Long?> = _searchSource

    fun setSearchSource(id: Long?) {
        _searchSource.value = id
        prefs.edit().putLong(KEY_SEARCH_SOURCE, id ?: NO_SOURCE).apply()
    }

    private val _mySpaceTab = MutableStateFlow(prefs.getString(KEY_MY_SPACE_TAB, null) ?: "Watching")

    /** Name of the My Space list chip that was last open. */
    val mySpaceTab: StateFlow<String> = _mySpaceTab

    fun setMySpaceTab(name: String) {
        _mySpaceTab.value = name
        prefs.edit().putString(KEY_MY_SPACE_TAB, name).apply()
    }

    // ---- Fonts the person added (Appearance > Font) ----

    private val appContext = context.applicationContext
    private val fontDir get() = java.io.File(appContext.filesDir, "fonts").also { it.mkdirs() }

    private val _appFontName = MutableStateFlow(storedFontName(KEY_APP_FONT_FILE, KEY_APP_FONT_NAME))

    /** Display name of the font added for the whole app, or null when none was added. */
    val appFontName: StateFlow<String?> = _appFontName

    private val _subtitleFontName = MutableStateFlow(storedFontName(KEY_SUB_FONT_FILE, KEY_SUB_FONT_NAME))

    /** Display name of the font added for subtitles only, or null when none was added. */
    val subtitleFontName: StateFlow<String?> = _subtitleFontName

    private val _subtitleFontSeparate = MutableStateFlow(prefs.getBoolean(KEY_SUB_FONT_SEPARATE, false))

    /** True: subtitles use their own font. False: they follow the app font. */
    val subtitleFontSeparate: StateFlow<Boolean> = _subtitleFontSeparate

    fun setSubtitleFontSeparate(value: Boolean) {
        _subtitleFontSeparate.value = value
        prefs.edit().putBoolean(KEY_SUB_FONT_SEPARATE, value).apply()
        applyFonts()
    }

    init {
        applyFonts()
    }

    private fun storedFontName(fileKey: String, nameKey: String): String? {
        val file = prefs.getString(fileKey, null)?.let { java.io.File(fontDir, it) }
        return if (file != null && file.isFile) prefs.getString(nameKey, null) else null
    }

    private fun storedFontFile(fileKey: String): java.io.File? =
        prefs.getString(fileKey, null)?.let { java.io.File(fontDir, it) }?.takeIf { it.isFile }

    /** Hands the current font files to the theme (app text) and the player (subtitles). */
    private fun applyFonts() {
        val appFile = storedFontFile(KEY_APP_FONT_FILE)
        val subtitleFile = if (_subtitleFontSeparate.value) storedFontFile(KEY_SUB_FONT_FILE) else appFile
        com.ansu.anime.ui.theme.AnsuFont.apply(appFile, subtitleFile)
    }

    /**
     * Copies the font the person picked into the app's private storage (the picked file may disappear, and the
     * app needs a plain file path), then uses it. Returns the display name, or null when the file is not a font.
     * Call off the main thread.
     */
    fun importFont(uri: android.net.Uri, forSubtitles: Boolean): String? {
        val resolver = appContext.contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        val name = (displayName ?: "Custom font").substringBeforeLast('.').ifBlank { "Custom font" }
        val fileKey = if (forSubtitles) KEY_SUB_FONT_FILE else KEY_APP_FONT_FILE
        val fileName = "${if (forSubtitles) "subtitle" else "app"}_${System.currentTimeMillis()}.font"
        val target = java.io.File(fontDir, fileName)
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out ->
                    // Fonts are a few hundred KB; anything near this size is not one.
                    var total = 0L
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FONT_BYTES) return@runCatching false
                        out.write(buffer, 0, read)
                    }
                }
                true
            } ?: false
        }.getOrDefault(false)
        if (!copied || !looksLikeFont(target) || runCatching { android.graphics.Typeface.createFromFile(target) }.isFailure) {
            target.delete()
            return null
        }
        // The new file has a new name, so nothing keeps showing the old font from a cache.
        storedFontFile(fileKey)?.delete()
        prefs.edit()
            .putString(fileKey, fileName)
            .putString(if (forSubtitles) KEY_SUB_FONT_NAME else KEY_APP_FONT_NAME, name)
            .apply()
        if (forSubtitles) _subtitleFontName.value = name else _appFontName.value = name
        applyFonts()
        return name
    }

    /** Removes the added font; text goes back to the default font (or, for subtitles, the app font or default). */
    fun clearFont(forSubtitles: Boolean) {
        val fileKey = if (forSubtitles) KEY_SUB_FONT_FILE else KEY_APP_FONT_FILE
        storedFontFile(fileKey)?.delete()
        prefs.edit().remove(fileKey).remove(if (forSubtitles) KEY_SUB_FONT_NAME else KEY_APP_FONT_NAME).apply()
        if (forSubtitles) _subtitleFontName.value = null else _appFontName.value = null
        applyFonts()
    }

    /** TrueType, OpenType, TrueType collection or the old Apple 'true' signature: the first four bytes say. */
    private fun looksLikeFont(file: java.io.File): Boolean {
        val head = ByteArray(4)
        val read = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(-1)
        if (read < 4) return false
        val tag = String(head, Charsets.ISO_8859_1)
        return tag == "OTTO" || tag == "true" || tag == "ttcf" ||
            (head[0] == 0.toByte() && head[1] == 1.toByte() && head[2] == 0.toByte() && head[3] == 0.toByte())
    }

    companion object {
        private const val KEY_ACCENT = "accent_palette"
        private const val KEY_APP_FONT_FILE = "app_font_file"
        private const val KEY_APP_FONT_NAME = "app_font_name"
        private const val KEY_SUB_FONT_FILE = "subtitle_font_file"
        private const val KEY_SUB_FONT_NAME = "subtitle_font_name"
        private const val KEY_SUB_FONT_SEPARATE = "subtitle_font_separate"
        private const val MAX_FONT_BYTES = 25L * 1024 * 1024
        private const val KEY_SEARCH_GRID = "search_grid_layout"
        private const val KEY_SEARCH_SOURCE = "search_source_id"
        private const val NO_SOURCE = Long.MIN_VALUE
        private const val KEY_MY_SPACE_TAB = "my_space_tab"
        private const val KEY_TITLE_LANGUAGE = "title_language"

        private const val KEY_BACKGROUND_STYLE = "background_style"
        private const val KEY_AMOLED = "amoled_black"
        const val DEFAULT_AMOLED = false

        private const val KEY_NAV_ROUNDNESS = "nav_bar_roundness"
        const val DEFAULT_NAV_ROUNDNESS = 0.7f // ≈ the previous fixed 24dp corners

        private const val KEY_NAV_FROSTINESS = "nav_bar_frostiness"
        const val DEFAULT_NAV_FROSTINESS = 0.5f // = the previous fixed 0.5 tint alpha

        private const val KEY_NAV_BACKDROP_BLUR = "nav_bar_backdrop_blur"
        const val DEFAULT_NAV_BACKDROP_BLUR = true
    }
}

/** Background animations; None keeps the plain background. */
enum class BackgroundStyle(val key: String, val label: String) {
    NONE("none", "None"),
    CIRCLES("circles", "Circles"),
    TRIANGLES("triangles", "Triangles"),
    SHAPES("shapes", "Material shapes"),
    SAKURA("sakura", "Cherry blossom"),
    SNOW("snow", "Snowfall"),
    HALLOWEEN("halloween", "Halloween");

    companion object {
        fun fromKey(key: String?): BackgroundStyle = entries.firstOrNull { it.key == key } ?: NONE
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
    VIOLET("violet", "Violet", Color(0xFF7C5CE6)),
    // Added later; kept mid-tone because the text on an accent is white for every colour except White.
    RED("red", "Red", Color(0xFFE5393B)),
    CRIMSON("crimson", "Crimson", Color(0xFFB3202F)),
    CORAL("coral", "Coral", Color(0xFFEE6352)),
    PINK("pink", "Pink", Color(0xFFD63FA8)),
    PURPLE("purple", "Purple", Color(0xFFA647D1)),
    INDIGO("indigo", "Indigo", Color(0xFF4F5BD5)),
    BLUE("blue", "Blue", Color(0xFF2F6FEB)),
    CYAN("cyan", "Cyan", Color(0xFF0E9FB5)),
    TEAL("teal", "Teal", Color(0xFF138F86)),
    BROWN("brown", "Brown", Color(0xFFA0643C)),
    SLATE("slate", "Slate", Color(0xFF6B7A99)),
    // A soft cherry-blossom pink. It is light, so text on it switches to dark (see AnsuColors.OnAccent).
    SAKURA("sakura", "Sakura", Color(0xFFF6B3CB));

    companion object {
        fun fromKey(key: String?): AccentPalette = entries.firstOrNull { it.key == key } ?: WHITE
    }
}
