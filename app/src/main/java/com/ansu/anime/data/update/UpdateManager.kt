package com.ansu.anime.data.update

import android.content.Context
import com.ansu.anime.BuildConfig
import com.ansu.anime.data.prefs.UpdatePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Ansu's self-updater: remembers the channel the user picked, checks what the
 * project publishes on GitHub, and — on request — downloads the APK and hands it
 * to the system installer. The About screen is the only consumer, and
 * [checkIfEnabled] runs once at startup.
 *
 * Everything is orchestrated on the main thread: the network and file work
 * happens on `Dispatchers.IO` inside the checker/installer, while `install()`
 * needs the main thread because it starts an activity.
 */
class UpdateManager(
    context: Context,
    client: OkHttpClient,
) {
    val prefs: UpdatePrefs = UpdatePrefs(context)

    private val checker = UpdateChecker(client)
    private val installer = UpdateInstaller(context, client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state

    /** Called once when the app starts: only checks if the user left automatic checks on. */
    fun checkIfEnabled() {
        if (!prefs.autoCheck.value) return
        check()
    }

    fun check() {
        scope.launch {
            _state.update { it.copy(isChecking = true, error = null) }
            val result = checker.check(
                channel = prefs.channel.value,
                installedVersionName = BuildConfig.VERSION_NAME,
                installedVersionCode = BuildConfig.VERSION_CODE,
            )
            _state.update { it.copy(isChecking = false, result = result, error = null) }
        }
    }

    fun downloadAndInstall() {
        val update = (_state.value.result as? UpdateCheckResult.Available)?.update ?: return
        scope.launch {
            _state.update { it.copy(isDownloading = true, downloadProgress = 0f, error = null) }
            val downloaded = runCatching {
                installer.download(update) { progress ->
                    _state.update { it.copy(downloadProgress = progress) }
                }
            }
            val apkFile = downloaded.getOrNull()
            if (apkFile != null) installer.install(apkFile)
            _state.update { it.copy(isDownloading = false, error = downloaded.exceptionOrNull()?.message) }
        }
    }
}
