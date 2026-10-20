package com.ansu.anime.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
    const val LICENSES = "licenses"
    const val UPDATES = "updates"
    const val CONTRIBUTORS = "contributors"
    const val SYSTEM = "system"
    const val BACKUP = "backup"
    const val MY_SPACE = "my_space"
    const val DOWNLOADS = "downloads"
    const val DETAILS = "details"
    const val SOURCES = "sources"
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
 * Counts the tab screens whose enter or exit animation is running right now, as reported by the NavHost itself
 * (see [TrackTabTransition]). The bottom bar waits for it to reach zero before it starts the next tab move.
 * A fixed wait could end while the previous move was still under way, for instance when the screen being
 * opened was slow to appear, and a second move started over a running one leaves the earlier screen drawn on
 * top of the new one.
 */
object TabTransitionGate {
    private val count = androidx.compose.runtime.mutableIntStateOf(0)

    /** True while any tab screen is animating in or out. Backed by state, so it can be observed. */
    val isRunning: Boolean get() = count.intValue > 0

    internal fun begin() {
        count.intValue += 1
    }

    internal fun end() {
        count.intValue = (count.intValue - 1).coerceAtLeast(0)
    }
}

/**
 * Called first thing by each tab screen's `composable` block: reports to [TabTransitionGate] for as long as
 * this screen is animating in or out. It ends when the animation ends or the screen leaves the composition,
 * whichever comes first, so it can never be left counted.
 */
@androidx.compose.runtime.Composable
fun androidx.compose.animation.AnimatedContentScope.TrackTabTransition() {
    val moving = transition.currentState != transition.targetState || transition.isRunning
    androidx.compose.runtime.DisposableEffect(moving) {
        if (moving) TabTransitionGate.begin()
        onDispose { if (moving) TabTransitionGate.end() }
    }
}

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
    val backgroundStyle by container.appearancePrefs.backgroundStyle.collectAsStateWithLifecycle()

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
    Box(modifier = Modifier.fillMaxSize().background(com.ansu.anime.ui.theme.AnsuColors.Background)) {
    // The animated background sits at the very back; the screens above it are see-through where they have no
    // content, while posters, cards and every opaque surface hide it completely.
    com.ansu.anime.ui.components.BackgroundAnimation(style = backgroundStyle)
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
            TrackTabTransition()
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
            TrackTabTransition()
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
            TrackTabTransition()
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
        composable(Dest.DOWNLOADS) {
            com.ansu.anime.ui.downloads.DownloadsScreen(container = container, navController = navController)
        }
        composable(Dest.LICENSES) {
            com.ansu.anime.ui.about.LicensesScreen(container = container, navController = navController)
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
            TrackTabTransition()
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
                        // Auto Source: the player picks the stream itself. Otherwise every stream of every source is
                        // listed first and the person picks one (or holds one to download it).
                        if (container.playerPrefs.autoSourcePick.value) {
                            navController.navigate(Dest.PLAYER)
                        } else {
                            navController.navigate(Dest.SOURCES)
                        }
                    },
                )
            }
        }
        composable(Dest.SOURCES) {
            com.ansu.anime.ui.sources.SourcePickerScreen(container = container, navController = navController)
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
