package com.ansu.anime.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ansu.anime.R
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.navigation.TAB_TRANSITION_MILLIS
import com.ansu.anime.ui.navigation.bottomNavDestinations
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private data class RailItem(val route: String, val label: String, val icon: ImageVector)

private val railItems = listOf(
    RailItem(Dest.HOME, "Home", Icons.Filled.Home),
    RailItem(Dest.SEARCH, "Search", Icons.Filled.Search),
    RailItem(Dest.SCHEDULE, "Schedule", Icons.Filled.Today),
    RailItem(Dest.NEWS, "News", Icons.AutoMirrored.Filled.Article),
    RailItem(Dest.MY_SPACE, "My Space", Icons.Filled.Person),
)

/**
 * Tablet mode's navigation: a slim rail down the left edge (app logo on top, the tabs in a rounded pill, Settings at
 * the bottom) in place of the floating bottom bar, which stays untouched for phones. Tabs move one at a time, the same
 * way the bar does: a tap only queues the tab, and the next move waits until the previous one has finished.
 */
@Composable
fun TabletNavRail(
    navController: NavHostController,
    currentRoute: String?,
    modifier: Modifier = Modifier,
) {
    val pendingRoute = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(navController) {
        snapshotFlow { pendingRoute.value }
            .filterNotNull()
            .collect { route ->
                pendingRoute.value = null
                if (route != navController.currentBackStackEntry?.destination?.route) {
                    navController.navigate(route) {
                        popUpTo(Dest.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    withTimeoutOrNull(5000L) {
                        navController.visibleEntries.first { entries ->
                            entries.count { it.destination.route in bottomNavDestinations } <= 1
                        }
                    }
                }
                delay(TAB_TRANSITION_MILLIS.toLong())
            }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(TabletRailWidth)
            .background(AnsuColors.Background.copy(alpha = 0.92f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App logo on top, in its own rounded box.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, AnsuColors.StrokeGlass, RoundedCornerShape(14.dp))
                .background(AnsuColors.BackgroundElevated),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Anisu",
                modifier = Modifier.size(44.dp),
            )
        }

        // The tabs, in one rounded pill; the open one is filled.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, AnsuColors.StrokeGlass, RoundedCornerShape(28.dp))
                    .background(AnsuColors.BackgroundElevated.copy(alpha = 0.6f))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                railItems.forEach { item ->
                    RailButton(
                        icon = item.icon,
                        label = item.label,
                        selected = currentRoute == item.route,
                        onClick = { pendingRoute.value = item.route },
                    )
                }
            }
        }

        // Settings at the bottom.
        RailButton(
            icon = Icons.Filled.Settings,
            label = "Settings",
            selected = false,
            onClick = { navController.navigate(Dest.SETTINGS) { launchSingleTop = true } },
        )
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) AnsuColors.Accent else androidx.compose.ui.graphics.Color.Transparent,
        label = "railSelected",
    )
    val tint = if (selected) AnsuColors.OnAccent else AnsuColors.TextSecondary
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
    }
}
