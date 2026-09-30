package com.ansu.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ansu.anime.data.prefs.AppearancePrefs
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.roundToInt

private data class BarItem(val route: String, val label: String, val icon: ImageVector)

private val barItems = listOf(
    BarItem(Dest.HOME, "Home", Icons.Filled.Home),
    BarItem(Dest.SEARCH, "Search", Icons.Filled.Search),
    BarItem(Dest.SCHEDULE, "Schedule", Icons.Filled.Today),
    BarItem(Dest.MY_SPACE, "My Space", Icons.Filled.Person),
)

/**
 * Current nav bar roundness (0f square … 1f pill). Provided once in AnsuNavGraph
 * from [AppearancePrefs], so every screen's AppBottomBar picks up changes live
 * without each call site having to pass it in.
 */
val LocalNavBarRoundness = compositionLocalOf { AppearancePrefs.DEFAULT_NAV_ROUNDNESS }

/**
 * Current nav bar frostiness / opacity (0f clear … 1f fully opaque), provided next to
 * [LocalNavBarRoundness] so the real bar follows the setting live.
 */
val LocalNavBarFrostiness = compositionLocalOf { AppearancePrefs.DEFAULT_NAV_FROSTINESS }

/**
 * Current nav bar blur level (0f none … 1f maximum), provided next to the other two so the bar
 * follows the setting live. Real blur needs Android 12+; below that it degrades to frost.
 */
val LocalNavBarBlur = compositionLocalOf { AppearancePrefs.DEFAULT_NAV_BLUR }

/** Maps frostiness/opacity 0..1 to the background tint opacity: 0.05 (nearly see-through) … 1.0 (solid). */
fun navBarTintAlpha(frostiness: Float): Float = 0.05f + frostiness.coerceIn(0f, 1f) * 0.95f

/** Maps blur 0..1 to a blur radius in dp (0 … 24dp). */
fun navBarBlurRadius(blur: Float): Dp = (blur.coerceIn(0f, 1f) * 24f).dp

/** Maps roundness 0..1 to a corner radius that scales with the bar's own height (50% = full pill). */
fun navBarShape(roundness: Float): RoundedCornerShape =
    RoundedCornerShape(percent = (roundness.coerceIn(0f, 1f) * 50f).roundToInt())

/** Frosted-glass bottom nav bar: a compact, icon-only floating pill. */
@Composable
fun AppBottomBar(navController: NavHostController, currentRoute: String?) {
    // The Scaffold's bottomBar slot has no background of its own, so only the
    // pill itself is drawn and the screen content shows around it.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The app draws edge to edge and a Scaffold lays its bottomBar slot
            // flush against the window, so a custom (non-Material) bar has to keep
            // clear of the system navigation bar itself.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        NavBarSurface(
            currentRoute = currentRoute,
            roundness = LocalNavBarRoundness.current,
            frostiness = LocalNavBarFrostiness.current,
            blur = LocalNavBarBlur.current,
            onItemClick = { route ->
                navController.navigate(route) {
                    popUpTo(Dest.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}

/**
 * The bar itself, decoupled from navigation so Settings can render an
 * identical, non-navigating copy as a live preview. It wraps its content
 * (four 70dp x 40dp slots) instead of spanning the screen.
 */
@Composable
fun NavBarSurface(
    currentRoute: String?,
    roundness: Float,
    frostiness: Float,
    modifier: Modifier = Modifier,
    blur: Float = AppearancePrefs.DEFAULT_NAV_BLUR,
    onItemClick: ((String) -> Unit)? = null,
) {
    val shape = navBarShape(roundness)
    FrostedGlassCard(
        modifier = modifier,
        shape = shape,
        tintAlpha = navBarTintAlpha(frostiness),
        blurRadius = navBarBlurRadius(blur),
    ) {
        Row(modifier = Modifier.padding(5.dp)) {
            barItems.forEach { item ->
                val selected = currentRoute == item.route
                Box(
                    modifier = Modifier
                        .size(width = 70.dp, height = 40.dp)
                        .clip(shape)
                        // Translucent "glass" pill marking the current tab.
                        .then(
                            if (selected) {
                                Modifier
                                    .background(AnsuColors.Accent.copy(alpha = 0.16f))
                                    .border(1.dp, AnsuColors.Accent.copy(alpha = 0.14f), shape)
                            } else {
                                Modifier
                            },
                        )
                        .then(if (onItemClick != null) Modifier.clickable { onItemClick(item.route) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) AnsuColors.Accent else AnsuColors.TextTertiary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
