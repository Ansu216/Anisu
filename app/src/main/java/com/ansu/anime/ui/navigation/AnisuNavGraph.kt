package com.ansu.anime.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.addons.AddonsScreen
import com.ansu.anime.ui.auth.AniListLoginScreen
import com.ansu.anime.ui.details.DetailsScreen
import com.ansu.anime.anilist.AniListMedia
import com.ansu.anime.ui.extensions.ExtensionsScreen
import com.ansu.anime.ui.home.HomeScreen
import com.ansu.anime.ui.library.LibraryScreen
import com.ansu.anime.ui.player.PlayerScreen
import com.ansu.anime.ui.search.SearchScreen
import com.ansu.anime.ui.settings.SettingsScreen

object Dest {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DETAILS = "details"
    const val PLAYER = "player"
    const val EXTENSIONS = "extensions"
    const val ADDONS = "addons"
    const val ANILIST_LOGIN = "anilist_login"
}

/** Bottom bar destinations, in display order. */
val bottomNavDestinations = listOf(Dest.HOME, Dest.SEARCH, Dest.LIBRARY, Dest.SETTINGS)

@Composable
fun AnisuNavGraph(container: AppContainer, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Dest.HOME) {
        composable(Dest.HOME) {
            HomeScreen(
                container = container,
                navController = navController,
                onAnimeSelected = { anime ->
                    container.selectionHolder.selectAnime(anime)
                    navController.navigate(Dest.DETAILS)
                },
            )
        }
        composable(Dest.SEARCH) {
            SearchScreen(
                container = container,
                navController = navController,
                onAnimeSelected = { anime ->
                    container.selectionHolder.selectAnime(anime)
                    navController.navigate(Dest.DETAILS)
                },
            )
        }
        composable(Dest.LIBRARY) {
            LibraryScreen(
                container = container,
                navController = navController,
                onAnimeSelected = { anime ->
                    container.selectionHolder.selectAnime(anime)
                    navController.navigate(Dest.DETAILS)
                },
            )
        }
        composable(Dest.SETTINGS) {
            SettingsScreen(container = container, navController = navController)
        }
        composable(Dest.EXTENSIONS) {
            ExtensionsScreen(container = container, navController = navController)
        }
        composable(Dest.ADDONS) {
            AddonsScreen(container = container, navController = navController)
        }
        composable(Dest.ANILIST_LOGIN) {
            AniListLoginScreen(container = container, navController = navController)
        }
        composable(Dest.DETAILS) {
            val anime = container.selectionHolder.currentAnime.value
            val anilistId = anime?.anilistId
            val media by produceState<com.ansu.anime.anilist.AniListMedia?>(initialValue = null, anilistId) {
                value = anilistId?.let { container.aniListRepository.getMediaDetails(it) }
            }
            media?.let { loaded ->
                DetailsScreen(
                    media = loaded,
                    isLiked = false,
                    onBack = { navController.popBackStack() },
                    onPlay = { navController.navigate(Dest.PLAYER) },
                    onToggleLike = { },
                    onEpisodeClick = { episode ->
                        container.selectionHolder.selectEpisode(
                            com.ansu.anime.core.model.SEpisode(
                                id = episode.number.toString(),
                                name = episode.title,
                                episodeNumber = episode.number.toFloat(),
                                thumbnailUrl = episode.thumbnailUrl,
                            ),
                        )
                        navController.navigate(Dest.PLAYER)
                    },
                )
            }
        }
        composable(Dest.PLAYER) {
            PlayerScreen(container = container, navController = navController)
        }
    }
}
