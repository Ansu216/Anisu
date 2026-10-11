package com.ansu.anime.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Backup settings, persisted in plain SharedPreferences next to [UpdatePrefs]
 * (nothing sensitive) and exposed as StateFlows so the Backup screen and the
 * scheduled worker always agree.
 */
class BackupPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("ansu_backup", Context.MODE_PRIVATE)

    private val _autoBackup = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP, true))
    val autoBackup: StateFlow<Boolean> = _autoBackup

    private val _lastAutoBackupAt = MutableStateFlow(prefs.getLong(KEY_LAST_RUN, 0L))

    /** When the automatic backup last wrote an archive, or 0 while it never has. */
    val lastAutoBackupAt: StateFlow<Long> = _lastAutoBackupAt

    fun setAutoBackup(value: Boolean) {
        _autoBackup.value = value
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, value).apply()
    }

    fun setLastAutoBackupAt(millis: Long) {
        _lastAutoBackupAt.value = millis
        prefs.edit().putLong(KEY_LAST_RUN, millis).apply()
    }

    private companion object {
        const val KEY_AUTO_BACKUP = "auto_backup"
        const val KEY_LAST_RUN = "last_auto_backup_at"
    }
}
