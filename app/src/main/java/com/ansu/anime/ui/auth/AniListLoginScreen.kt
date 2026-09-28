package com.ansu.anime.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.di.AppContainer

@Composable
fun AniListLoginScreen(container: AppContainer, navController: NavHostController) {
    val isLoggedIn by container.aniListRepository.isLoggedIn.collectAsStateWithLifecycle()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) navController.popBackStack()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Connect AniList", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ansu uses AniList to sync your watch progress and show your lists on the Library tab. " +
                "You'll be taken to anilist.co to sign in; nothing is shared with any extension or addon.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = { container.aniListRepository.login() }) {
            Text("Continue to AniList")
        }
    }
}
