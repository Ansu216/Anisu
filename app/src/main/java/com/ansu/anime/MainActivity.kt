package com.ansu.anime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as AnsuApp).container

    /** Android 13+ asks for this once; the "update available" notification waits for the grant. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
        /** Intent extra set by [com.ansu.anime.data.update.UpdateNotifier] when its notification is tapped. */
        const val EXTRA_OPEN_UPDATES = "com.ansu.anime.extra.OPEN_UPDATES"
    }
}
