package com.ansu.anime

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ansu.anime.ui.navigation.AnsuNavGraph
import com.ansu.anime.ui.theme.AnsuTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as AnsuApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            AnsuTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AnsuNavGraph(container = container)
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
