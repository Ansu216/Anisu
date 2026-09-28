@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.anilist.AniListMediaListEntry
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.AppBottomBar
import com.ansu.anime.ui.components.PosterRow
import com.ansu.anime.ui.components.ShelfHeader
import com.ansu.anime.ui.navigation.Dest

@Composable
fun LibraryScreen(
    container: AppContainer,
    navController: NavHostController,
    onAnimeSelected: (SAnime) -> Unit,
) {
    val isLoggedIn by container.aniListRepository.isLoggedIn.collectAsStateWithLifecycle()
    var watching by remember { mutableStateOf<List<AniListMediaListEntry>>(emptyList()) }
    var planning by remember { mutableStateOf<List<AniListMediaListEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            isLoading = true
            watching = container.aniListRepository.getCurrentlyWatching()
            planning = container.aniListRepository.getPlanning()
            isLoading = false
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        bottomBar = { AppBottomBar(navController, Dest.LIBRARY) },
    ) { padding ->
        if (!isLoggedIn) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Connect your AniList account to see your lists and sync watch progress here.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Button(onClick = { navController.navigate(Dest.ANILIST_LOGIN) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Connect AniList")
                }
            }
            return@Scaffold
        }

        if (isLoading) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 60.dp))
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { ShelfHeader("Currently Watching") }
            item { PosterRow(items = watching.map { it.toSAnime() }, onClick = onAnimeSelected) }
            item { ShelfHeader("Planning") }
            item { PosterRow(items = planning.map { it.toSAnime() }, onClick = onAnimeSelected) }
        }
    }
}

private fun AniListMediaListEntry.toSAnime(): SAnime = SAnime(
    id = media.id.toString(),
    title = media.title,
    posterUrl = media.posterUrl,
    bannerUrl = media.bannerUrl,
    description = media.description,
    genres = media.genres,
    releaseYear = media.year,
    rating = media.averageScore?.div(10.0),
    anilistId = media.id,
    origin = MediaOrigin.Extension(sourceId = 1L, urlPath = media.id.toString()),
)
