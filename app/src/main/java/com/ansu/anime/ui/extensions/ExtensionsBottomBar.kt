package com.ansu.anime.ui.extensions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ansu.anime.ui.components.backdropBlurSupported
import com.ansu.anime.ui.components.frostedBackdrop
import com.ansu.anime.ui.components.navBarShape
import com.ansu.anime.ui.components.pillCornerRadius
import com.ansu.anime.ui.components.navBarTintAlpha
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.roundToInt

enum class ExtensionsTab(val label: String) { Available("Available"), Installed("Installed") }

/** What the little circle beside the tab bar does right now. */
enum class ExtensionsAction { Add, Download, Close }

private val TabSlotWidth = 112.dp
private val TabSlotHeight = 44.dp

/** Height of the whole floating row (bar and circle share it). */
val ExtensionsBarHeight = TabSlotHeight + 10.dp

/**
 * The floating Available / Installed bar. It is the Home nav bar's twin: the same frosted glass (following
 * the Appearance roundness / frostiness / blur settings) and wider slots that carry words instead of icons.
 * The pill moves like the Schedule / News switch: one spring that overshoots a touch, stretching a little as it
 * travels, with a swell while a finger is held on it. Tabs switch by tapping.
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
    val targetIndex = tab.ordinal

    val sources = remember { tabs.map { MutableInteractionSource() } }
    val held = sources.any { it.collectIsPressedAsState().value }
    val holdAmount = animateFloatAsState(
        targetValue = if (held) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "extTabHold",
    )
    // Same motion as the Schedule / News switch: one spring that overshoots a touch.
    val position = animateFloatAsState(
        targetValue = targetIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "extTabPosition",
    )
    val activeIndex = targetIndex

    val blurActive = backdropBlur && backdropBlurSupported
    val tintAlpha = navBarTintAlpha(frostiness) * (if (blurActive) 0.6f else 1f)

    Box(
        modifier = modifier.graphicsLayer {
            val hold = holdAmount.value
            scaleX = 1f + 0.04f * hold
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
        Box(modifier = Modifier.padding(5.dp)) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((position.value * slotPx).roundToInt(), 0) }
                    .size(width = TabSlotWidth, height = TabSlotHeight)
                    .drawBehind {
                        val p = position.value
                        val travel = (4f * p * (1f - p)).coerceIn(0f, 1f)
                        val h = holdAmount.value
                        val extraW = 14.dp.toPx() * travel + 22.dp.toPx() * h
                        val extraH = 22.dp.toPx() * h + 6.dp.toPx() * travel
                        val w = size.width + extraW
                        val ht = size.height + extraH
                        val topLeft = Offset(-extraW / 2f, -extraH / 2f)
                        val glow = (h + travel).coerceAtMost(1.2f)
                        val radius = pillCornerRadius(w, ht, roundness, 1.dp.toPx())
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
                        modifier = Modifier
                            .size(width = TabSlotWidth, height = TabSlotHeight)
                            .clickable(interactionSource = sources[index], indication = null) { onTabSelected(item) },
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
    val plusAlpha = animateFloatAsState(if (action == ExtensionsAction.Download) 0f else 1f, tween(160), label = "extPlusAlpha")
    val downloadAlpha = animateFloatAsState(if (action == ExtensionsAction.Download) 1f else 0f, tween(160), label = "extDownloadAlpha")
    val turn = animateFloatAsState(
        targetValue = if (action == ExtensionsAction.Close) 45f else 0f,
        animationSpec = tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
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
