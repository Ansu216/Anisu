package com.ansu.anime.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ansu.anime.AnsuApp
import com.ansu.anime.R
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.navigation.TAB_TRANSITION_MILLIS
import com.ansu.anime.ui.navigation.bottomNavDestinations
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private data class RailItem(val route: String, val label: String, val icon: ImageVector)

private val railItems = listOf(
    RailItem(Dest.HOME, "Home", Icons.Filled.Home),
    RailItem(Dest.SEARCH, "Search", Icons.Filled.Search),
    RailItem(Dest.SCHEDULE, "Schedule", Icons.Filled.Today),
    RailItem(Dest.NEWS, "News", Icons.AutoMirrored.Filled.Article),
    RailItem(Dest.MY_SPACE, "My Space", Icons.Filled.Person),
)

/** A release faster than this (towards the edge the rail sits on) switches sides even from a short drag. */
private val SWITCH_VELOCITY = 600.dp

/** A drag past this share of the rail's width switches sides even when it is let go slowly. */
private const val SWITCH_DRAG_FRACTION = 0.4f

/**
 * Tablet mode's navigation: a slim rail down one edge (app logo on top, the tabs in a rounded pill, Settings at the
 * bottom) in place of the floating bottom bar, which stays untouched for phones. Tabs move one at a time, the same
 * way the bar does: a tap only queues the tab, and the next move waits until the previous one has finished.
 *
 * Flinging the rail towards the edge it sits on sends it to the other side. It follows the finger while dragged;
 * once it is let go past the middle (or flicked fast) it leaves through that edge while the screen stretches after
 * it, then comes in from the opposite edge and pushes the screen along as it settles. The side is saved, and the
 * layout mirrors with it. A short or slow drag springs back.
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

    val context = LocalContext.current
    val appearancePrefs = remember { (context.applicationContext as AnsuApp).container.appearancePrefs }
    val right = LocalTabletRailRight.current
    val rightNow by rememberUpdatedState(right)
    val density = LocalDensity.current
    val railPx = with(density) { TabletRailWidth.toPx() }
    val switchVelocityPx = with(density) { SWITCH_VELOCITY.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motion = TabletRailMotion.exit
    // +1 when "off screen" is to the right of the rail, -1 when it is to the left.
    val outwardSign = if (right) 1f else -1f
    var switching by remember { mutableStateOf(false) }

    // A fresh rail always starts on screen (an earlier switch may have been cut short by leaving the tab screens).
    LaunchedEffect(Unit) { motion.snapTo(0f) }

    fun switchSides() {
        switching = true
        scope.launch {
            val target = !rightNow
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            // 1. Out through the edge it sits on; the screen stretches into the room it leaves.
            motion.animateTo(1f, tween(durationMillis = 230, easing = FastOutLinearInEasing))
            // 2. Change sides while it is out of sight, and wait until the screens have laid out on the new side.
            appearancePrefs.setTabletRailRight(target)
            withTimeoutOrNull(600L) { snapshotFlow { rightNow }.first { it == target } }
            withFrameNanos { }
            // 3. In from the opposite edge, pushing the screen along; a small settle at the end.
            motion.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
            switching = false
        }
    }

    val dragState = rememberDraggableState { delta ->
        if (!switching) {
            // Only towards the edge the rail sits on; dragging the other way does nothing.
            val next = (motion.value + delta * outwardSign / railPx).coerceIn(0f, 1f)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { motion.snapTo(next) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(TabletRailWidth)
            // The drag sits outside the moving layer: a drag on a node that moves with the finger would stutter.
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    if (!switching) {
                        val outward = velocity * outwardSign
                        if (motion.value > SWITCH_DRAG_FRACTION || outward > switchVelocityPx) {
                            switchSides()
                        } else {
                            scope.launch {
                                motion.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
                            }
                        }
                    }
                },
            )
            .graphicsLayer { translationX = outwardSign * railPx * motion.value }
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
