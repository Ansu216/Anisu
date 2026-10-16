package com.ansu.anime.ui.extensions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.components.BackdropState
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.LocalNavBarBackdropBlur
import com.ansu.anime.ui.components.LocalNavBarFrostiness
import com.ansu.anime.ui.components.LocalNavBarRoundness
import com.ansu.anime.ui.components.PillMotion
import com.ansu.anime.ui.components.backdropBlurSupported
import com.ansu.anime.ui.components.frostedBackdrop
import com.ansu.anime.ui.components.navBarShape
import com.ansu.anime.ui.components.navBarTintAlpha
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ExtensionsTab(val label: String) { Available("Available"), Installed("Installed") }

/** What the little circle beside the tab bar does right now. */
enum class ExtensionsAction { Add, Download, Close }

private val TabSlotWidth = 112.dp
private val TabSlotHeight = 44.dp

/** Height of the whole floating row (bar and circle share it). */
val ExtensionsBarHeight = TabSlotHeight + 10.dp

/** How far (at most) the bar leans toward the pill while it is held and dragged. */
private val TabBarDragShift = 8.dp

/**
 * The floating Available / Installed bar. It is the Home nav bar's twin: the same frosted glass (following
 * the Appearance roundness / frostiness / blur settings), the same two-edge spring pill that stretches toward
 * the tab it is going to, the same swell when held and the same drag-to-pick. Only the slots are wider and
 * carry words instead of icons.
 */
@Composable
fun ExtensionsTabBar(
    tab: ExtensionsTab,
    onTabSelected: (ExtensionsTab) -> Unit,
    backdrop: BackdropState,
    modifier: Modifier = Modifier,
) {
    val roundness = LocalNavBarRoundness.current
    val frostiness = LocalNavBarFrostiness.current
    val backdropBlur = LocalNavBarBackdropBlur.current
    val shape = navBarShape(roundness)
    val tabs = ExtensionsTab.entries
    val density = LocalDensity.current
    val slotPx = with(density) { TabSlotWidth.toPx() }
    val maxLeft = slotPx * (tabs.size - 1)
    val targetIndex = tab.ordinal
    val scope = rememberCoroutineScope()

    val pill = remember { PillMotion(targetIndex * slotPx, slotPx) }
    var held by remember { mutableStateOf(false) }
    var dragLeft by remember { mutableStateOf<Float?>(null) }
    val holdAmount = animateFloatAsState(
        targetValue = if (held) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "extTabHold",
    )
    val activeIndex = dragLeft?.let { ((it + slotPx / 2f) / slotPx).toInt().coerceIn(0, tabs.size - 1) } ?: targetIndex

    LaunchedEffect(targetIndex) {
        if (abs(pill.left - targetIndex * slotPx) > 0.5f) pill.animateTo(targetIndex * slotPx)
    }

    val shiftPx = with(density) { TabBarDragShift.toPx() }
    val blurActive = backdropBlur && backdropBlurSupported
    val tintAlpha = navBarTintAlpha(frostiness) * (if (blurActive) 0.6f else 1f)

    Box(
        modifier = modifier.graphicsLayer {
            val left = dragLeft ?: pill.left
            val right = if (dragLeft != null) left + slotPx else pill.right
            val lean = (((left + right) / 2f - slotPx / 2f) / maxLeft - 0.5f) * 2f
            val hold = holdAmount.value
            translationX = lean * shiftPx * hold
            scaleX = 1f + 0.03f * hold
            scaleY = 1f + 0.06f * hold
        },
    ) {
        FrostedGlassCard(
            modifier = Modifier.frostedBackdrop(backdrop, backdropBlur, shape),
            shape = shape,
            tintAlpha = tintAlpha,
        ) {
            Spacer(Modifier.size(width = TabSlotWidth * tabs.size + 10.dp, height = ExtensionsBarHeight))
        }
        Box(
            modifier = Modifier
                .padding(5.dp)
                .pointerInput(tab) {
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
                            if (moved) {
                                dragLeft = (lastX - slotPx / 2).coerceIn(0f, maxLeft)
                                change.consume()
                            }
                        }
                        held = false
                        val index = (lastX / slotPx).toInt().coerceIn(0, tabs.size - 1)
                        val from = dragLeft
                        val picked = tabs[index]
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            if (from != null) pill.snapTo(from)
                            dragLeft = null
                            // A tab change is animated once, by the targetIndex effect above.
                            if (picked == tab) pill.animateTo(index * slotPx)
                        }
                        if (picked != tab) onTabSelected(picked)
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        val left = dragLeft ?: pill.left
                        IntOffset(left.roundToInt(), 0)
                    }
                    .size(width = TabSlotWidth, height = TabSlotHeight)
                    .drawBehind {
                        val left = dragLeft ?: pill.left
                        val right = if (dragLeft != null) left + slotPx else pill.right
                        val stretch = ((right - left) / slotPx - 1f).coerceIn(0f, 1.2f)
                        val h = holdAmount.value
                        val extraW = 22.dp.toPx() * h
                        val extraH = 22.dp.toPx() * h + 12.dp.toPx() * (stretch / 1.2f)
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
                tabs.forEachIndexed { index, item ->
                    Box(
                        modifier = Modifier.size(width = TabSlotWidth, height = TabSlotHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (index == activeIndex) AnsuColors.Accent else AnsuColors.TextSecondary,
                            modifier = Modifier.graphicsLayer {
                                val scale = if (index == activeIndex) 1f + 0.12f * holdAmount.value else 1f
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

/**
 * The circle beside the bar: the same frosted glass, one size with the bar. Its icon morphs: a plus
 * (add a repo), a download arrow (updates are waiting) or a close cross (a card is open). The plus
 * turns 45 degrees into the cross, so opening and closing the card reads as one motion.
 */
@Composable
fun ExtensionsActionButton(
    action: ExtensionsAction,
    onClick: () -> Unit,
    backdrop: BackdropState,
    modifier: Modifier = Modifier,
) {
    val frostiness = LocalNavBarFrostiness.current
    val backdropBlur = LocalNavBarBackdropBlur.current
    val blurActive = backdropBlur && backdropBlurSupported
    val tintAlpha = navBarTintAlpha(frostiness) * (if (blurActive) 0.6f else 1f)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "extActionPress",
    )
    val plusAlpha = animateFloatAsState(if (action == ExtensionsAction.Download) 0f else 1f, tween(200), label = "extPlusAlpha")
    val downloadAlpha = animateFloatAsState(if (action == ExtensionsAction.Download) 1f else 0f, tween(200), label = "extDownloadAlpha")
    val turn = animateFloatAsState(
        targetValue = if (action == ExtensionsAction.Close) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 320f),
        label = "extActionTurn",
    )
    val description = when (action) {
        ExtensionsAction.Add -> "Add extension"
        ExtensionsAction.Download -> "Update sources"
        ExtensionsAction.Close -> "Close"
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
            }
            .semantics { contentDescription = description },
    ) {
        FrostedGlassCard(
            modifier = Modifier.frostedBackdrop(backdrop, backdropBlur, CircleShape),
            shape = CircleShape,
            tintAlpha = tintAlpha,
        ) {
            Box(
                modifier = Modifier
                    .size(ExtensionsBarHeight)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = AnsuColors.TextPrimary,
                    modifier = Modifier.size(26.dp).graphicsLayer {
                        alpha = plusAlpha.value
                        rotationZ = turn.value
                        val s = 0.6f + 0.4f * plusAlpha.value
                        scaleX = s
                        scaleY = s
                    },
                )
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    tint = AnsuColors.Accent,
                    modifier = Modifier.size(26.dp).graphicsLayer {
                        alpha = downloadAlpha.value
                        val s = 0.6f + 0.4f * downloadAlpha.value
                        scaleX = s
                        scaleY = s
                    },
                )
            }
        }
    }
}
