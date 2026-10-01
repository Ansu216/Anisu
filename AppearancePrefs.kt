package com.ansu.anime.data.prefs

import android.content.Context
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

    private val _navBarBlur = MutableStateFlow(
        prefs.getFloat(KEY_NAV_BLUR, DEFAULT_NAV_BLUR).coerceIn(0f, 1f),
    )

    /**
     * Blur strength of the bar background, 0f = none … 1f = maximum. On Android 12+ it drives a real
     * platform blur; on Android 8–11, which have no such effect, the bar simply keeps its frosted glass.
     */
    val navBarBlur: StateFlow<Float> = _navBarBlur

    fun setNavBarBlur(value: Float) {
        val v = value.coerceIn(0f, 1f)
        _navBarBlur.value = v
        prefs.edit().putFloat(KEY_NAV_BLUR, v).apply()
    }

    companion object {
        private const val KEY_NAV_ROUNDNESS = "nav_bar_roundness"
        const val DEFAULT_NAV_ROUNDNESS = 0.7f // ≈ the previous fixed 24dp corners

        private const val KEY_NAV_FROSTINESS = "nav_bar_frostiness"
        const val DEFAULT_NAV_FROSTINESS = 0.5f // = the previous fixed 0.5 tint alpha

        private const val KEY_NAV_BLUR = "nav_bar_blur"
        const val DEFAULT_NAV_BLUR = 0f // off by default
    }
}
