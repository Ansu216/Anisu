package com.ansu.anime.data.prefs

import android.content.Context
import com.ansu.anime.data.update.UpdateChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Update settings, persisted in plain SharedPreferences next to
 * [AppearancePrefs] (nothing sensitive) and exposed as StateFlows so the About
 * screen and the startup check always agree.
 */
class UpdatePrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("ansu_updates", Context.MODE_PRIVATE)

    private val _channel = MutableStateFlow(loadChannel())
    val channel: StateFlow<UpdateChannel> = _channel

    private val _autoCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK, true))
    val autoCheck: StateFlow<Boolean> = _autoCheck

    fun setChannel(value: UpdateChannel) {
        _channel.value = value
        prefs.edit().putString(KEY_CHANNEL, value.name).apply()
    }

    fun setAutoCheck(value: Boolean) {
        _autoCheck.value = value
        prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply()
    }

    private fun loadChannel(): UpdateChannel {
        val stored = prefs.getString(KEY_CHANNEL, null) ?: return UpdateChannel.RELEASES
        return UpdateChannel.entries.firstOrNull { it.name == stored } ?: UpdateChannel.RELEASES
    }

    companion object {
        private const val KEY_CHANNEL = "update_channel"
        private const val KEY_AUTO_CHECK = "auto_check"
    }
}
