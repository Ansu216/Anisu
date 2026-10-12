package com.ansu.anime.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.ui.about.AboutScreen
import com.ansu.anime.ui.about.UpdatesScreen
import com.ansu.anime.ui.appearance.AppearanceScreen
import com.ansu.anime.ui.components.AppBottomBar
import com.ansu.anime.ui.components.LocalNavBarBackdropBlur
import com.ansu.anime.ui.components.LocalNavBarFrostiness
import com.ansu.anime.ui.components.LocalNavBarRoundness
import com.ansu.anime.ui.components.PosterExpandContainer
import com.ansu.anime.ui.components.PosterTransition
import com.ansu.anime.ui.components.rememberBackdropState
import com.ansu.anime.ui.contributors.ContributorsScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.auth.AniListLoginScreen
import com.ansu.anime.ui.backup.BackupScreen
import com.ansu.anime.ui.details.DetailsScreen
import com.ansu.anime.ui.extensions.ExtensionsScreen
import com.ansu.anime.ui.home.HomeScreen
import com.ansu.anime.ui.myspace.MySpaceScreen
import com.ansu.anime.ui.player.PlayerScreen
import com.ansu.anime.ui.schedule.ScheduleScreen
import com.ansu.anime.ui.search.SearchScreen
import com.ansu.anime.ui.settings.SettingsScreen
import com.ansu.anime.ui.system.SystemScreen

object Dest {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SCHEDULE = "schedule"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
    const val PLAYER_STREAMING = "player_streaming"
    const val ABOUT = "about"
    const val UPDATES = "updates"
    const val CONTRIBUTORS = "contributors"
    const val SYSTEM = "system"
    const val BACKUP = "backup"
    const val MY_SPACE = "my_space"
    const val DETAILS = "details"
    const val PLAYER = "player"
    const val EXTENSIONS = "extensions"
    const val ANILIST_LOGIN = "anilist_login"
}

/** Bottom bar destinations, in display order. */
val bottomNavDestinations = listOf(Dest.HOME, Dest.SEARCH, Dest.SCHEDULE, Dest.MY_SPACE)

/**
 * How long a tab change fades for. The bar waits exactly this long before it applies a tab that was
 * tapped while the previous move was still running, so two tab changes never overlap.
 */
const val TAB_TRANSITION_MILLIS = 230

/**
 * +1 when a change goes to a later bottom-bar tab, -1 to an earlier one and 0 when the change is not
 * between two tabs. A tab change is a soft cross-fade: the old page fades out while the new one fades
 * in, with no sideways travel, so the direction only tells a tab change apart from any other move.
 */
private fun tabSlideDirection(fromRoute: String?, toRoute: String?): Int {
    val from = bottomNavDestinations.indexOf(fromRoute)
    val to = bottomNavDestinations.indexOf(toRoute)
    if (from < 0 || to < 0 || from == to) return 0
    return if (to > from) 1 else -1
}

@Composable
fun AnsuNavGraph(container: AppContainer, navController: NavHostController = rememberNavController()) {
    val navBarRoundness by container.appearancePrefs.navBarRoundness.collectAsStateWithLifecycle()
    val navBarFrostiness by container.appearancePrefs.navBarFrostiness.collectAsStateWithLifecycle()
    val navBarBackdropBlur by container.appearancePrefs.navBarBackdropBlur.collectAsStateWithLifecycle()

    // Every route change is written to the diagnostics log, so an exported report shows where the
    // user actually went before a problem happened.
    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry) {
        backStackEntry?.destination?.route?.let { route ->
            container.diagnostics.log(LogCategory.NAVIGATION, "→ $route")
        }
    }

    // The bar is drawn once, in a sibling Box above the NavHost, so a tab change fades only the screen
    // and its content: the bar itself stays still. The four tab screens record their content into this
    // one shared backdrop, which is what the bar blurs.
    val backdrop = rememberBackdropState()

    CompositionLocalProvider(
        LocalNavBarRoundness provides navBarRoundness,
        LocalNavBarFrostiness provides navBarFrostiness,
        LocalNavBarBackdropBlur provides navBarBackdropBlur,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
    // Screen transitions are about half the platform default, which makes the whole app feel snappier.
    // Switching between the four bottom-bar tabs is a cross-fade: the page you leave fades out while the
    // page you open fades in, in place. The same fade is used whether the move pushes or pops (tab
    // changes do both, through `popUpTo`), so the two can never disagree.
    NavHost(
        navController = navController,
        startDestination = Dest.HOME,
        enterTransition = {
            if (tabSlideDirection(initialState.destination.route, targetState.destination.route) != 0) {
                fadeIn(animationSpec = tween(durationMillis = TAB_TRANSITION_MILLIS, easing = LinearOutSlowInEasing))
            } else {
                fadeIn(animationSpec = tween(durationMillis = 175))
            }
        },
        // The page under a details page stays put (only dimmed) while the details page grows out of the
        // tapped poster and shrinks back into it, so the poster has something to grow out of.
        exitTransition = {
            when {
                targetState.destination.route == Dest.DETAILS ->
                    fadeOut(targetAlpha = 0.55f, animationSpec = tween(durationMillis = PosterTransition.OPEN_MILLIS))
                tabSlideDirection(initialState.destination.route, targetState.destination.route) != 0 ->
                    fadeOut(animationSpec = tween(durationMillis = TAB_TRANSITION_MILLIS, easing = FastOutSlowInEasing))
                else -> fadeOut(animationSpec = tween(durationMillis = 150))
            }
        },
        popEnterTransition = {
            when {
                initialState.destination.route == Dest.DETAILS ->
                    fadeIn(initialAlpha = 0.55f, animationSpec = tween(durationMillis = PosterTransition.CLOSE_MILLIS))
                tabSlideDirection(initialState.destination.route, targetState.destination.route) != 0 ->
                    fadeIn(animationSpec = tween(durationMillis = TAB_TRANSITION_MILLIS, easing = LinearOutSlowInEasing))
                else -> fadeIn(animationSpec = tween(durationMillis = 150))
            }
        },
        // A tab move can pop (see `popUpTo` in the bar), so the leaving screen fades the same way
        // the entering one does; everything else keeps the plain quick fade.
        popExitTransition = {
            if (tabSlideDirection(initialState.destination.route, targetState.destination.route) != 0) {
                fadeOut(animationSpec = tween(durationMillis = TAB_TRANSITION_MILLIS, easing = FastOutSlowInEasing))
            } else {
                fadeOut(animationSpec = tween(durationMillis = 150))
            }
        },
    ) {
        composable(Dest.HOME) {
            HomeScreen(
                container = container,
                navController = navController,
                backdrop = backdrop,
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
                backdrop = backdrop,
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
                backdrop = backdrop,
                onEntrySelected = { entry ->
                    val anime = com.ansu.anime.core.model.SAnime(
                        id = entry.animeId.toString(),
                        title = entry.animeTitle,
                        posterUrl = entry.imageUrl,
                        anilistId = entry.animeId,
                        // No installed extension has matched this show yet — it only
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
        composable(Dest.PLAYER_STREAMING) {
            com.ansu.anime.ui.playersettings.PlayerStreamingScreen(container = container, navController = navController)
        }
        composable(Dest.ABOUT) {
            AboutScreen(container = container, navController = navController)
        }
        composable(Dest.UPDATES) {
            UpdatesScreen(container = container, navController = navController)
        }
        composable(Dest.CONTRIBUTORS) {
            ContributorsScreen(container = container, navController = navController)
        }
        composable(Dest.SYSTEM) {
            SystemScreen(container = container, navController = navController)
        }
        composable(Dest.BACKUP) {
            BackupScreen(container = container, navController = navController)
        }
        composable(Dest.MY_SPACE) {
            MySpaceScreen(
                container = container,
                navController = navController,
                backdrop = backdrop,
                onAnimeSelected = { anime ->
                    container.selectionHolder.selectAnime(anime)
                    navController.navigate(Dest.DETAILS)
                },
            )
        }
        composable(Dest.EXTENSIONS) {
            ExtensionsScreen(container = container, navController = navController)
        }
        composable(Dest.ANILIST_LOGIN) {
            AniListLoginScreen(container = container, navController = navController)
        }
        // The details page animates itself (PosterExpandContainer): it grows out of the tapped poster and
        // closes back into it. The NavHost's own transitions are switched off for it, and its progress
        // is tied to this destination's enter/exit transition so it stays on screen until it has closed.
        composable(
            Dest.DETAILS,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) { entry ->
            PosterExpandContainer(
                entryId = entry.id,
                isOnBackStack = { navController.currentBackStack.value.any { it.id == entry.id } },
            ) {
                DetailsScreen(
                    container = container,
                    navController = navController,
                    onEpisodeSelected = { episode ->
                        container.selectionHolder.selectEpisode(episode)
                        navController.navigate(Dest.PLAYER)
                    },
                )
            }
        }
        composable(Dest.PLAYER) {
            PlayerScreen(container = container, navController = navController)
        }
    }

    // The single shared bar: it sits above the NavHost so it never fades with a screen. It is shown
    // only on the four tab destinations and fades out as soon as a full-screen route (details, player,
    // settings, ...) is opened over them.
    val currentRoute = backStackEntry?.destination?.route
    if (currentRoute != null) {
        AnimatedVisibility(
            visible = currentRoute in bottomNavDestinations,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            AppBottomBar(
                navController = navController,
                currentRoute = currentRoute,
                backdrop = backdrop,
            )
        }
    }
    }
    }
}
