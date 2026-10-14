package com.ansu.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.ansu.anime.ui.navigation.TabTransitionGate
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import com.ansu.anime.data.prefs.AppearancePrefs
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.navigation.TAB_TRANSITION_MILLIS
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
 * Whether the bar blurs the content scrolling behind it (the "Frosted blur" toggle in Appearance), provided next
 * to the other two so the real bar follows the setting live. Real blur needs Android 12+; below that the bar
 * keeps its plain frosted glass.
 */
val LocalNavBarBackdropBlur = compositionLocalOf { AppearancePrefs.DEFAULT_NAV_BACKDROP_BLUR }

/** Maps frostiness/opacity 0..1 to the background tint opacity: 0.05 (nearly see-through) … 1.0 (solid). */
fun navBarTintAlpha(frostiness: Float): Float = 0.05f + frostiness.coerceIn(0f, 1f) * 0.95f

/** Maps roundness 0..1 to a corner radius that scales with the bar's own height (50% = full pill). */
fun navBarShape(roundness: Float): RoundedCornerShape =
    RoundedCornerShape(percent = (roundness.coerceIn(0f, 1f) * 50f).roundToInt())

/** Frosted-glass bottom nav bar: a compact, icon-only floating pill. */
@Composable
fun AppBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    backdrop: BackdropState? = null,
    modifier: Modifier = Modifier,
) {
    // One tab move at a time. A tab tapped while the previous move is still sliding only replaces this
    // pending destination, and it is applied the moment that slide ends: asking the NavHost for a second
    // tab move while the first one was still running is what left the earlier screen composed on top of
    // the new one - the "last tab gets stuck" the bar was reported for. `snapshotFlow` conflates, so a
    // burst of taps collapses to the tab that was really tapped last instead of playing every step.
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
                    // Hold the next move until the NavHost itself says this one is over: an entry stays in
                    // `visibleEntries` until its screen has really finished leaving, however late a slow screen
                    // (My Space while it loads) started animating. The old gate used timeouts, so a second tap
                    // could still land on a running move and leave the previous tab drawn over the new one.
                    kotlinx.coroutines.withTimeoutOrNull(5000L) {
                        navController.visibleEntries.first { entries ->
                            entries.count { it.destination.route in com.ansu.anime.ui.navigation.bottomNavDestinations } <= 1
                        }
                    }
                }
                delay(TAB_SETTLE_MILLIS)
            }
    }

    // The bar is drawn once, in the navigation shell, so it stays still during a tab change. It is a
    // floating card with no background behind it, so the screen content shows around it.
    Box(
        modifier = modifier
            .fillMaxWidth()
            // The app draws edge to edge and the bar sits flush against the window, so a custom
            // (non-Material) bar has to keep clear of the system navigation bar itself.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        NavBarSurface(
            currentRoute = currentRoute,
            roundness = LocalNavBarRoundness.current,
            frostiness = LocalNavBarFrostiness.current,
            backdropBlur = LocalNavBarBackdropBlur.current,
            backdrop = backdrop,
            // Tapping only queues the tab; the single mover above performs the move and spaces them out.
            onItemClick = { route -> pendingRoute.value = route },
        )
    }
}

/**
 * Empty stand-in that the four tab screens put in their Scaffold's bottomBar slot. The bar itself is
 * drawn once, in the navigation shell, so it stays still during a tab change; the screens only reserve
 * its height here, so their content still scrolls clear of it.
 */
@Composable
fun AppBottomBarReserve(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(Modifier.size(width = NavSlotWidth * barItems.size + 10.dp, height = NavSlotHeight + 10.dp))
    }
}

/** Extra time after a tab slide before the next queued tab may start, so frames of the old slide have finished. */
private const val TAB_SETTLE_MILLIS = 90L

private val NavSlotWidth = 70.dp
private val NavSlotHeight = 40.dp

/** How far (at most) the whole bar slides toward the pill while it is held and dragged. */
private val NavBarDragShift = 10.dp

/**
 * The selection pill as two edges that move on their own springs. The edge facing the destination is pushed
 * out quickly and overshoots a little, the trailing edge is pulled along more slowly, so the pill stretches
 * toward the new tab mid-flight and then squeezes back to a single slot, like a drop of glass being dragged.
 */
private class PillMotion(initialLeft: Float, private val slotPx: Float) {
    private val leftEdge = Animatable(initialLeft)
    private val rightEdge = Animatable(initialLeft + slotPx)

    val left: Float get() = leftEdge.value
    val right: Float get() = rightEdge.value

    suspend fun snapTo(left: Float) {
        leftEdge.snapTo(left)
        rightEdge.snapTo(left + slotPx)
    }

    suspend fun animateTo(targetLeft: Float) {
        val movingRight = targetLeft + slotPx >= rightEdge.value
        val leading = spring<Float>(dampingRatio = 0.64f, stiffness = 520f)
        val trailing = spring<Float>(dampingRatio = 0.82f, stiffness = 170f)
        coroutineScope {
            launch { leftEdge.animateTo(targetLeft, if (movingRight) trailing else leading) }
            launch { rightEdge.animateTo(targetLeft + slotPx, if (movingRight) leading else trailing) }
        }
    }
}

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
    backdropBlur: Boolean = AppearancePrefs.DEFAULT_NAV_BACKDROP_BLUR,
    backdrop: BackdropState? = null,
    onItemClick: ((String) -> Unit)? = null,
) {
    val shape = navBarShape(roundness)
    val slotPx = with(LocalDensity.current) { NavSlotWidth.toPx() }
    val maxLeft = slotPx * (barItems.size - 1)
    val targetIndex = barItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()

    val pill = remember { PillMotion(targetIndex * slotPx, slotPx) }
    var held by remember { mutableStateOf(false) }
    // Left edge of the pill while a finger drags it; null whenever [pill] is in charge.
    var dragLeft by remember { mutableStateOf<Float?>(null) }
    // The pill's spring values change every frame, so they are never read while composing: reading them
    // here used to recompose the whole bar on each frame, which is what made the move stutter. They are
    // read only in the offset/draw/graphics-layer lambdas below, so a tab change just updates those.
    val holdAmount = animateFloatAsState(
        targetValue = if (held) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "navPillHold",
    )
    // The index whose icon is highlighted: the finger's slot while dragging, the target tab otherwise.
    val activeIndex = dragLeft?.let { ((it + slotPx / 2f) / slotPx).toInt().coerceIn(0, barItems.size - 1) }
        ?: targetIndex

    LaunchedEffect(targetIndex) {
        // Only travel when the pill is not already parked on the target. Animating unconditionally re-ran
        // the same run whenever this value and the pill already agreed, which made the pill replay its move.
        if (abs(pill.left - targetIndex * slotPx) > 0.5f) pill.animateTo(targetIndex * slotPx)
    }

    val shiftPx = with(LocalDensity.current) { NavBarDragShift.toPx() }
    // With a backdrop to blur, the glass tint is thinner so the blurred content actually shows through it.
    val blurActive = backdrop != null && backdropBlur && backdropBlurSupported
    val tintAlpha = navBarTintAlpha(frostiness) * (if (blurActive) 0.6f else 1f)
    // The bar's glass is clipped to its shape, but the held pill has to swell past the bar's edges, so the pill and
    // the icons are drawn on top of the glass instead of inside it.
    Box(
        // While the pill is held the whole bar leans a little toward it and swells slightly, and eases
        // back when it is released. Everything scales with holdAmount, so a resting bar is untouched.
        modifier = modifier.graphicsLayer {
            val left = dragLeft ?: pill.left
            val right = if (dragLeft != null) left + slotPx else pill.right
            val lean = (((left + right) / 2f - slotPx / 2f) / maxLeft - 0.5f) * 2f
            val hold = holdAmount.value
            translationX = lean * shiftPx * hold
            scaleX = 1f + 0.04f * hold
            scaleY = 1f + 0.06f * hold
        },
    ) {
        FrostedGlassCard(
            modifier = Modifier.frostedBackdrop(backdrop, backdropBlur, shape),
            shape = shape,
            tintAlpha = tintAlpha,
        ) {
            Spacer(Modifier.size(width = NavSlotWidth * barItems.size + 10.dp, height = NavSlotHeight + 10.dp))
        }
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
                        val route = barItems[index].route
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            if (from != null) pill.snapTo(from)
                            dragLeft = null
                            // A tab change is animated once, by the shared bar's `targetIndex` effect;
                            // animating here as well is what made the pill appear to travel twice.
                            if (route == currentRoute) pill.animateTo(index * slotPx)
                        }
                        if (route != currentRoute) onItemClick(route)
                    }
                },
        ) {
            // Translucent "glass" pill marking the current tab. Held, it swells wider and taller than the bar
            // itself (drawn, not clipped), brightens like a lens, and follows the finger. Moving between tabs it
            // stretches toward the destination and bulges, then settles back into one slot.
            Box(
                modifier = Modifier
                    .offset {
                        val left = dragLeft ?: pill.left
                        IntOffset(left.roundToInt(), 0)
                    }
                    .size(width = NavSlotWidth, height = NavSlotHeight)
                    .drawBehind {
                        val left = dragLeft ?: pill.left
                        val right = if (dragLeft != null) left + slotPx else pill.right
                        val stretch = ((right - left) / slotPx - 1f).coerceIn(0f, 1.2f)
                        val h = holdAmount.value
                        val extraW = 28.dp.toPx() * h
                        val extraH = 26.dp.toPx() * h + 14.dp.toPx() * (stretch / 1.2f)
                        val w = (right - left) + extraW
                        val ht = size.height + extraH
                        val topLeft = Offset(-extraW / 2f, -extraH / 2f)
                        val glow = (h + stretch).coerceAtMost(1.2f)
                        val radius = CornerRadius(minOf(w, ht) * roundness.coerceIn(0f, 1f) / 2f)
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    AnsuColors.Accent.copy(alpha = (0.16f + 0.14f * glow).coerceAtMost(0.5f)),
                                    AnsuColors.Accent.copy(alpha = (0.10f + 0.08f * glow).coerceAtMost(0.4f)),
                                ),
                            ),
                            topLeft = topLeft,
                            size = Size(w, ht),
                            cornerRadius = radius,
                        )
                        // Soft white sheen so the swollen pill reads as glass over the bar.
                        drawRoundRect(
                            color = Color.White.copy(alpha = (0.12f * glow).coerceAtMost(0.2f)),
                            topLeft = topLeft,
                            size = Size(w, ht),
                            cornerRadius = radius,
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = (0.10f + 0.22f * glow).coerceAtMost(0.34f)),
                            topLeft = topLeft,
                            size = Size(w, ht),
                            cornerRadius = radius,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    },
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
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    // The icon under the pill is magnified, like the lens is enlarging it.
                                    val left = dragLeft ?: pill.left
                                    val right = if (dragLeft != null) left + slotPx else pill.right
                                    val stretch = ((right - left) / slotPx - 1f).coerceIn(0f, 1.2f)
                                    val scale = if (index == activeIndex) 1f + 0.3f * holdAmount.value + 0.18f * stretch else 1f
                                    scaleX = scale
                                    scaleY = scale
                                },
                        )
                    }
                }
            }
        }
    }
}
