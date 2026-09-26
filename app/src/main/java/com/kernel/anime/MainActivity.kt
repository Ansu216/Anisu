package com.kernel.anime

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kernel.anime.ui.navigation.KernelNavGraph
import com.kernel.anime.ui.theme.KernelTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as KernelApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            KernelTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KernelNavGraph(container = container)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (container.aniListAuthManager.handleRedirect(uri)) {
            MainScope().launch { container.aniListRepository.refreshViewer() }
        }
    }
}
