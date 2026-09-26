package com.kernel.anime.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kernel.anime.di.AppContainer
import com.kernel.anime.ui.addons.AddonsScreen
import com.kernel.anime.ui.auth.AniListLoginScreen
import com.kernel.anime.ui.details.DetailsScreen
import com.kernel.anime.ui.extensions.ExtensionsScreen
import com.kernel.anime.ui.home.HomeScreen
import com.kernel.anime.ui.library.LibraryScreen
import com.kernel.anime.ui.player.PlayerScreen
import com.kernel.anime.ui.search.SearchScreen
import com.kernel.anime.ui.settings.SettingsScreen

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
fun KernelNavGraph(container: AppContainer, navController: NavHostController = rememberNavController()) {
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
            DetailsScreen(
                container = container,
                navController = navController,
                onEpisodeSelected = { episode ->
                    container.selectionHolder.selectEpisode(episode)
                    navController.navigate(Dest.PLAYER)
                },
            )
        }
        composable(Dest.PLAYER) {
            PlayerScreen(container = container, navController = navController)
        }
    }
}
