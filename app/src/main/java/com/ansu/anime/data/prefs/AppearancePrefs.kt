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

    companion object {
        private const val KEY_NAV_ROUNDNESS = "nav_bar_roundness"
        const val DEFAULT_NAV_ROUNDNESS = 0.7f // ≈ the previous fixed 24dp corners
    }
}
