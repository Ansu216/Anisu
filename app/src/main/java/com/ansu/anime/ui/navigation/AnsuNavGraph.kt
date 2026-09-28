package com.ansu.anime.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ansu.anime.ui.appearance.AppearanceScreen
import com.ansu.anime.ui.components.LocalNavBarRoundness
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.addons.AddonsScreen
import com.ansu.anime.ui.auth.AniListLoginScreen
import com.ansu.anime.ui.details.DetailsScreen
import com.ansu.anime.ui.extensions.ExtensionsScreen
import com.ansu.anime.ui.home.HomeScreen
import com.ansu.anime.ui.myspace.MySpaceScreen
import com.ansu.anime.ui.player.PlayerScreen
import com.ansu.anime.ui.schedule.ScheduleScreen
import com.ansu.anime.ui.search.SearchScreen
import com.ansu.anime.ui.settings.SettingsScreen

object Dest {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SCHEDULE = "schedule"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
    const val MY_SPACE = "my_space"
    const val DETAILS = "details"
    const val PLAYER = "player"
    const val EXTENSIONS = "extensions"
    const val ADDONS = "addons"
    const val ANILIST_LOGIN = "anilist_login"
}

/** Bottom bar destinations, in display order. */
val bottomNavDestinations = listOf(Dest.HOME, Dest.SEARCH, Dest.SCHEDULE, Dest.MY_SPACE)

@Composable
fun AnsuNavGraph(container: AppContainer, navController: NavHostController = rememberNavController()) {
    val navBarRoundness by container.appearancePrefs.navBarRoundness.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalNavBarRoundness provides navBarRoundness) {
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
        composable(Dest.SCHEDULE) {
            ScheduleScreen(
                container = container,
                navController = navController,
                onEntrySelected = { entry ->
                    val anime = com.ansu.anime.core.model.SAnime(
                        id = entry.animeId.toString(),
                        title = entry.animeTitle,
                        posterUrl = entry.imageUrl,
                        anilistId = entry.animeId,
                        // No installed extension/addon has matched this show yet — it only
                        // came from AniList's public schedule — so episodes on the details
                        // page may come back empty until a real source is wired up for it.
                        origin = com.ansu.anime.core.model.MediaOrigin.Extension(sourceId = 1L, urlPath = entry.animeId.toString()),
                    )
                    container.selectionHolder.selectAnime(anime)
                    navController.navigate(Dest.DETAILS)
                },
            )
        }
        composable(Dest.SETTINGS) {
            SettingsScreen(container = container, navController = navController)
        }
        composable(Dest.APPEARANCE) {
            AppearanceScreen(container = container, navController = navController)
        }
        composable(Dest.MY_SPACE) {
            MySpaceScreen(
                container = container,
                navController = navController,
                onAnimeSelected = { anime ->
                    container.selectionHolder.selectAnime(anime)
                    navController.navigate(Dest.DETAILS)
                },
            )
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
}
