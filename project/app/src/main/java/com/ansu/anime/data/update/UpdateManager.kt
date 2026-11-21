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
 * to the system installer. The About/Updates screens drive it in the foreground,
 * [checkIfEnabled] runs once at startup and [UpdateCheckWorker] re-checks
 * periodically in the background so the notification can arrive while the app is
 * closed.
 *
 * Everything is orchestrated on the main thread: the network and file work
 * happens on `Dispatchers.IO` inside the checker/installer, while `install()`
 * needs the main thread because it starts an activity.
 */
class UpdateManager(
    context: Context,
    client: OkHttpClient,
    private val diagnostics: Diagnostics? = null,
    private val nightlyDownloads: NightlyDownloads? = null,
) {
    private val appContext: Context = context.applicationContext
    val prefs: UpdatePrefs = UpdatePrefs(context)

    private val checker = UpdateChecker(client)
    private val installer = UpdateInstaller(context, client, diagnostics)
    private val notifier = UpdateNotifier(context, diagnostics)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state

    /** Called once when the app starts: only checks if the user left automatic checks on. */
    fun checkIfEnabled() {
        if (!prefs.autoCheck.value) return
        check()
    }

    fun check() {
        scope.launch { checkNow() }
    }

    /**
     * Runs one check and updates [state]. Suspending, so the background worker can await it instead of
     * firing a second, parallel check through [scope].
     */
    suspend fun checkNow(): UpdateCheckResult {
        _state.update { it.copy(isChecking = true, error = null) }
        val channel = prefs.channel.value
        diagnostics?.log(LogCategory.UPDATE, "Checking for updates on channel $channel")
        val result = checker.check(
            channel = channel,
            installedVersionName = BuildConfig.VERSION_NAME,
            installedVersionCode = BuildConfig.VERSION_CODE,
            installedCommit = BuildConfig.BUILD_COMMIT,
        )
        when (result) {
            is UpdateCheckResult.UpToDate -> {
                diagnostics?.log(LogCategory.UPDATE, "Up to date (${result.latestVersionName})")
                // Nothing to install any more: drop the notification and allow a future build to notify.
                notifier.cancel()
                prefs.setLastNotifiedVersion(null)
            }
            is UpdateCheckResult.Available -> {
                diagnostics?.log(LogCategory.UPDATE, "Update available: ${result.update.versionName} (${result.update.channel})")
                // Only announce each build once, however often the app checks, and only record it as
                // announced when the notification was really posted — otherwise a check that ran
                // before the Android 13 permission was granted would silence that build for good.
                // The build is identified by its commit on the nightly channel: the version name is
                // the same on every nightly, so keying on it would announce exactly one nightly ever.
                if (prefs.lastNotifiedVersion() != result.update.buildId) {
                    if (notifier.notifyUpdate(result.update)) {
                        prefs.setLastNotifiedVersion(result.update.buildId)
                    }
                }
            }
            is UpdateCheckResult.Failed ->
                diagnostics?.log(LogCategory.UPDATE, "Update check failed: ${result.message}")
        }
        // A cached APK belongs to the build it was downloaded for, so the Install button only comes
        // back for the same build: a newer one has to be downloaded first.
        val cachedApk = (result as? UpdateCheckResult.Available)?.let { installer.cachedApk(it.update) }
        _state.update {
            it.copy(isChecking = false, result = result, error = null, downloadedApk = cachedApk)
        }
        return result
    }

    /**
     * Posts a real system notification on demand, so the user can confirm it arrives without waiting
     * for a new build to be published. Returns whether the system accepted it.
     */
    fun notifyTest(): Boolean = notifier.notifyTest()

    /** What is stopping a notification right now, or `null` when nothing is. */
    fun notificationBlockedReason(): String? = notifier.blockedReason()

    /** Whether the system would deliver an update notification right now. */
    fun notificationsEnabled(): Boolean = notifier.canNotify()

    /** How many downloaded update APKs are sitting in the cache. */
    fun cachedApkCount(): Int = installer.cachedApkCount()

    /** Empties that cache and reports whether it worked, for the Updates screen's storage row. */
    fun clearCachedApks(): Boolean {
        val cleared = installer.clearCachedApks()
        if (cleared) _state.update { it.copy(downloadedApk = null) }
        diagnostics?.log(LogCategory.UPDATE, "Cleared the downloaded update APKs: $cleared")
        return cleared
    }

    /** Turns automatic checks on or off and keeps the periodic background check in step with it. */
    fun setAutoCheck(enabled: Boolean) {
        prefs.setAutoCheck(enabled)
        applyAutoCheck()
    }

    /** Reconciles the scheduled background check with the stored preference; safe to call on every launch. */
    fun applyAutoCheck() {
        if (prefs.autoCheck.value) {
            UpdateCheckWorker.schedule(appContext)
        } else {
            UpdateCheckWorker.cancel(appContext)
        }
    }

    /**
     * Downloads the build on offer and stops there. The install is a separate, explicit action
     * ([installDownloaded]), so a download that took a while on a bad connection is not immediately
     * thrown into the installer, and a file already on disk can be installed without downloading it again.
     */
    fun download() {
        val update = (_state.value.result as? UpdateCheckResult.Available)?.update ?: return
        scope.launch {
            diagnostics?.log(LogCategory.UPDATE, "Downloading ${update.versionName} from ${update.apkUrl}")
            _state.update {
                it.copy(isDownloading = true, downloadProgress = 0f, downloadedApk = null, error = null)
            }
            val downloaded = runCatching {
                installer.download(update) { progress ->
                    _state.update { it.copy(downloadProgress = progress) }
                }
            }
            val apkFile = downloaded.getOrNull()
            val failure = downloaded.exceptionOrNull()
            if (failure != null) {
                diagnostics?.log(LogCategory.UPDATE, "Download failed: ${failure.message}")
            }
            _state.update { it.copy(isDownloading = false, downloadedApk = apkFile, error = failure?.message) }
        }
    }

    /** Hands the APK that [download] already wrote to the system installer. */
    fun installDownloaded() {
        val apkFile = _state.value.downloadedApk ?: return
        val update = (_state.value.result as? UpdateCheckResult.Available)?.update
        // About's nightly counter. A nightly has no GitHub download count to read, so the app counts
        // its own downloads; it runs beside the install, never in front of it.
        if (update?.channel == UpdateChannel.NIGHTLY) {
            scope.launch { nightlyDownloads?.recordDownload() }
        }
        installer.install(apkFile)
        // The system installer owns the file from here; asking for it again would install the build
        // that is being replaced. A cached file is offered again only if the same build is still the
        // one on offer at the next check.
        _state.update { it.copy(downloadedApk = null) }
    }
}
