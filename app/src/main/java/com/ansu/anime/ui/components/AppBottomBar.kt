package com.ansu.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlin.math.abs
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
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
 * Where the selection pill was (in px, left edge) when the previous bar left the screen. Every screen
 * owns its own [AppBottomBar], so a new bar starts its pill here and slides it to the new tab, which is
 * what makes the pill appear to travel between tabs across a navigation.
 */
private var lastPillPx = Float.NaN

private val NavSlotWidth = 70.dp
private val NavSlotHeight = 40.dp

/**
 * The bar itself, decoupled from navigation so Settings can render an
 * identical, non-navigating copy as a live preview. It wraps its content
 * (four 70dp x 40dp slots) instead of spanning the screen.
 *
 * The selection pill slides to the tapped tab with a soft spring. Pressing and holding makes the pill
 * grow and brighten, and while it is held the pill can be dragged across the bar; releasing it selects
 * the tab it is over.
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
    val slotPx = with(LocalDensity.current) { NavSlotWidth.toPx() }
    val maxLeft = slotPx * (barItems.size - 1)
    val targetIndex = barItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val pillSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 380f)

    val pill = remember { Animatable(if (lastPillPx.isNaN()) targetIndex * slotPx else lastPillPx) }
    var held by remember { mutableStateOf(false) }
    // Left edge of the pill while a finger drags it; null whenever [pill] is in charge.
    var dragLeft by remember { mutableStateOf<Float?>(null) }
    val pillLeft = dragLeft ?: pill.value
    val holdAmount by animateFloatAsState(
        targetValue = if (held) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "navPillHold",
    )

    LaunchedEffect(targetIndex) { pill.animateTo(targetIndex * slotPx, pillSpring) }
    if (onItemClick != null) SideEffect { lastPillPx = pillLeft }
    val activeIndex = (pillLeft / slotPx).roundToInt().coerceIn(0, barItems.size - 1)

    FrostedGlassCard(
        modifier = modifier,
        shape = shape,
        tintAlpha = navBarTintAlpha(frostiness),
        blurRadius = navBarBlurRadius(blur),
    ) {
        Box(
            modifier = Modifier
                .padding(5.dp)
                .pointerInput(onItemClick, currentRoute) {
                    if (onItemClick == null) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        held = true
                        var moved = false
                        var lastX = down.position.x
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            lastX = change.position.x
                            if (!change.pressed) break
                            if (!moved && abs(lastX - down.position.x) > viewConfiguration.touchSlop) moved = true
                            // A plain press keeps the pill where it is (it just grows); only a drag moves it.
                            if (moved) {
                                dragLeft = (lastX - slotPx / 2).coerceIn(0f, maxLeft)
                                change.consume()
                            }
                        }
                        held = false
                        val index = (lastX / slotPx).toInt().coerceIn(0, barItems.size - 1)
                        val from = dragLeft
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            if (from != null) pill.snapTo(from)
                            dragLeft = null
                            pill.animateTo(index * slotPx, pillSpring)
                        }
                        if (barItems[index].route != currentRoute) onItemClick(barItems[index].route)
                    }
                },
        ) {
            // Translucent "glass" pill marking the current tab; it slides between tabs and swells while held.
            Box(
                modifier = Modifier
                    .offset { IntOffset(pillLeft.roundToInt(), 0) }
                    .size(width = NavSlotWidth, height = NavSlotHeight)
                    .graphicsLayer {
                        scaleX = 1f + 0.2f * holdAmount
                        scaleY = 1f + 0.22f * holdAmount
                    }
                    .clip(shape)
                    .background(AnsuColors.Accent.copy(alpha = 0.16f + 0.14f * holdAmount))
                    .border(1.dp, AnsuColors.Accent.copy(alpha = 0.14f + 0.1f * holdAmount), shape),
            )
            Row {
                barItems.forEachIndexed { index, item ->
                    Box(
                        modifier = Modifier
                            .size(width = NavSlotWidth, height = NavSlotHeight)
                            .semantics(mergeDescendants = true) {
                                onClick {
                                    onItemClick?.invoke(item.route)
                                    onItemClick != null
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (index == activeIndex) AnsuColors.Accent else AnsuColors.TextTertiary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
