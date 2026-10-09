package com.ansu.anime

import android.Manifest
import android.content.Intent
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.content.pm.PackageManager
import android.util.Rational
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.data.update.UpdateCheckResult
import com.ansu.anime.ui.components.UpdateBanner
import com.ansu.anime.ui.navigation.AnsuNavGraph
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as AnsuApp).container

    /**
     * Android 13+ asks for this once. The startup check runs at the same moment, so it usually
     * finishes while the permission is still unanswered and cannot post anything; checking again the
     * instant it is granted is what makes the "update available" notification appear on first launch
     * instead of waiting for the next app start.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) container.updateManager.checkIfEnabled()
        }

    /** Set by a tap on the update notification, then consumed once the nav graph is ready. */
    private val openUpdatesRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        requestNotificationPermissionIfNeeded()

        // Automatic update check; a no-op when the user turned it off in Updates/About.
        container.updateManager.checkIfEnabled()

        setContent {
            AnsuTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val updateState by container.updateManager.state.collectAsStateWithLifecycle()
                val update = (updateState.result as? UpdateCheckResult.Available)?.update
                // Dismissing the in-app banner only hides this version, so the next build still shows.
                var dismissedVersion by rememberSaveable { mutableStateOf<String?>(null) }
                val showUpdateBanner = update != null && update.versionName != dismissedVersion

                // The in-app banner is a heads-up: it hides itself after four seconds so it never
                // stays pinned over the screen. Dismissing it by hand does the same thing earlier.
                LaunchedEffect(showUpdateBanner) {
                    if (showUpdateBanner) {
                        delay(BANNER_AUTO_DISMISS_MS)
                        dismissedVersion = update?.versionName
                    }
                }

                LaunchedEffect(openUpdatesRequest.value) {
                    if (openUpdatesRequest.value) {
                        openUpdatesRequest.value = false
                        navController.navigate(Dest.UPDATES) { launchSingleTop = true }
                    }
                }

                // API failures reported by ApiErrorHandler surface here, above whichever screen is open.
                LaunchedEffect(Unit) {
                    container.apiErrorHandler.events.collect { error ->
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(error.userMessage)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnsuNavGraph(container = container, navController = navController)

                        if (showUpdateBanner) {
                            UpdateBanner(
                                state = updateState,
                                onOpen = {
                                    container.diagnostics.log(LogCategory.CLICK, "Update banner opened")
                                    navController.navigate(Dest.UPDATES) { launchSingleTop = true }
                                },
                                onDismiss = {
                                    dismissedVersion = update?.versionName
                                    container.diagnostics.log(LogCategory.CLICK, "Update banner dismissed")
                                },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                            )
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = if (showUpdateBanner) 84.dp else 8.dp),
                        )
                    }
                }
            }
        }
    }

    /** True while the player is on screen, playing, and picture-in-picture is switched on in Settings. */
    @Volatile
    private var pipEligible = false
    private var pipPlaying = false

    /** Set by the player screen: what the floating window's play/pause button does. */
    var pipToggle: (() -> Unit)? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_TOGGLE) pipToggle?.invoke()
        }
    }

    fun setPipState(eligible: Boolean, playing: Boolean) {
        pipEligible = eligible
        pipPlaying = playing
        // Keeps the window's play/pause icon right, and (Android 12+) lets Home enter it by itself.
        runCatching { setPictureInPictureParams(pipParams(autoEnter = eligible)) }
    }

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val toggle = RemoteAction(
            Icon.createWithResource(
                this,
                if (pipPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            ),
            if (pipPlaying) "Pause" else "Play",
            if (pipPlaying) "Pause" else "Play",
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_PIP_TOGGLE).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(toggle))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(pipReceiver) }
        super.onStop()
    }

    // Android 8-11 have no auto-enter, so pressing Home enters picture-in-picture from here.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipEligible && Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isInPictureInPictureMode) {
            runCatching { enterPictureInPictureMode(pipParams(autoEnter = false)) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        // A tap on the "update available" notification opens the app on the Updates screen.
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)) {
            openUpdatesRequest.value = true
            intent.removeExtra(EXTRA_OPEN_UPDATES)
        }

        val uri = intent.data ?: return
        if (container.aniListAuthManager.handleRedirect(uri)) {
            MainScope().launch { container.aniListRepository.refreshViewer() }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        private const val ACTION_PIP_TOGGLE = "com.ansu.anime.action.PIP_TOGGLE"

        /** Intent extra set by [com.ansu.anime.data.update.UpdateNotifier] when its notification is tapped. */
        const val EXTRA_OPEN_UPDATES = "com.ansu.anime.extra.OPEN_UPDATES"

        /** How long the in-app update banner stays before it hides itself (4 seconds). */
        private const val BANNER_AUTO_DISMISS_MS = 4_000L
    }
}
