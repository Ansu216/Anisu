package com.ansu.anime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Maps roundness 0..1 to a corner radius that scales with the bar's own height (50% = full pill). */
fun navBarShape(roundness: Float): RoundedCornerShape =
    RoundedCornerShape(percent = (roundness.coerceIn(0f, 1f) * 50f).roundToInt())

/** Frosted-glass bottom nav bar — visual replacement for the old plain KernelBottomBar. */
@Composable
fun AppBottomBar(navController: NavHostController, currentRoute: String?) {
    NavBarSurface(
        currentRoute = currentRoute,
        roundness = LocalNavBarRoundness.current,
        onItemClick = { route ->
            navController.navigate(route) {
                popUpTo(Dest.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    )
}

/**
 * The bar itself, decoupled from navigation so Settings can render an
 * identical, non-navigating copy as a live preview.
 */
@Composable
fun NavBarSurface(
    currentRoute: String?,
    roundness: Float,
    modifier: Modifier = Modifier,
    onItemClick: ((String) -> Unit)? = null,
) {
    FrostedGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = navBarShape(roundness),
        tintAlpha = 0.55f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Pull items inward as corners round off so edge icons never touch the curve.
                .padding(horizontal = (roundness * 12f).dp, vertical = 10.dp),
        ) {
            barItems.forEach { item ->
                val selected = currentRoute == item.route
                val tint = if (selected) AnsuColors.Accent else AnsuColors.TextTertiary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (onItemClick != null) Modifier.clickable { onItemClick(item.route) } else Modifier)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(imageVector = item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.padding(bottom = 4.dp))
                    Text(
                        text = item.label,
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
