package com.ansu.anime.data.update

import android.content.Context
import com.ansu.anime.BuildConfig
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory
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
    private val diagnostics: Diagnostics? = null,
) {
    val prefs: UpdatePrefs = UpdatePrefs(context)

    private val checker = UpdateChecker(client)
    private val installer = UpdateInstaller(context, client, diagnostics)
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
            val channel = prefs.channel.value
            diagnostics?.log(LogCategory.UPDATE, "Checking for updates on channel $channel")
            val result = checker.check(
                channel = channel,
                installedVersionName = BuildConfig.VERSION_NAME,
                installedVersionCode = BuildConfig.VERSION_CODE,
            )
            when (result) {
                is UpdateCheckResult.UpToDate ->
                    diagnostics?.log(LogCategory.UPDATE, "Up to date (${result.latestVersionName})")
                is UpdateCheckResult.Available ->
                    diagnostics?.log(LogCategory.UPDATE, "Update available: ${result.update.versionName} (${result.update.channel})")
                is UpdateCheckResult.Failed ->
                    diagnostics?.log(LogCategory.UPDATE, "Update check failed: ${result.message}")
            }
            _state.update { it.copy(isChecking = false, result = result, error = null) }
        }
    }

    fun downloadAndInstall() {
        val update = (_state.value.result as? UpdateCheckResult.Available)?.update ?: return
        scope.launch {
            diagnostics?.log(LogCategory.UPDATE, "Downloading ${update.versionName} from ${update.apkUrl}")
            _state.update { it.copy(isDownloading = true, downloadProgress = 0f, error = null) }
            val downloaded = runCatching {
                installer.download(update) { progress ->
                    _state.update { it.copy(downloadProgress = progress) }
                }
            }
            val apkFile = downloaded.getOrNull()
            if (apkFile != null) {
                installer.install(apkFile)
            } else {
                diagnostics?.log(LogCategory.UPDATE, "Download failed: ${downloaded.exceptionOrNull()?.message}")
            }
            _state.update { it.copy(isDownloading = false, error = downloaded.exceptionOrNull()?.message) }
        }
    }
}
