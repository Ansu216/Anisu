package com.ansu.anime.data.prefs

import android.content.Context

/**
 * Remembers whether the one-time welcome card has been shown. It is stored as a plain flag in
 * SharedPreferences (nothing sensitive), so the card appears on the first launch of this version
 * and never again, including after the app is restarted.
 */
class WelcomePrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("ansu_welcome", Context.MODE_PRIVATE)

    /** True once the person has closed the welcome card. */
    fun hasSeenWelcome(): Boolean = prefs.getBoolean(KEY_SEEN, false)

    fun markWelcomeSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
    }

    private companion object {
        const val KEY_SEEN = "welcome_seen"
    }
}
