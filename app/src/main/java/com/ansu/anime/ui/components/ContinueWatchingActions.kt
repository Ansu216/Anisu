package com.ansu.anime.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ansu.anime.core.util.formatEpisodeNumber
import com.ansu.anime.data.db.ContinueWatchingEntity
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** The entry that was held, plus where its card sat on screen (window coordinates) when it was held. */
data class ContinueWatchingActionTarget(val entry: ContinueWatchingEntity, val bounds: Rect)

private enum class ActionPhase { Menu, Removing, Closing }

// ---- Layout of the floating stack (all fixed, so the card's landing spot is known on the first frame) ----

private val TitleHeight = 28.dp
private val SubtitleHeight = 22.dp
private val MenuRowHeight = 55.dp
private val GapAfterCard = 14.dp
private val GapBeforeMenu = 18.dp
private val StackLift = 16.dp
private val CardBaseWidth = 220.dp // width of the real card in the row

// ---- The two sword cuts, in unit coordinates of the card (they run a little past its edges) ----
// A is the steep one, B the shallow one; they cross inside the card and split it in four.

private val SlashAFrom = Offset(0.70f, -0.08f)
private val SlashATo = Offset(0.30f, 1.08f)
private val SlashBFrom = Offset(-0.08f, 0.22f)
private val SlashBTo = Offset(1.08f, 0.80f)

private fun smoothStep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Keeps the part of convex [poly] on one side of the line a→b (Sutherland–Hodgman, one edge). */
private fun clipToSide(poly: List<Offset>, a: Offset, b: Offset, keepLeft: Boolean): List<Offset> {
    fun side(p: Offset): Float {
        val s = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        return if (keepLeft) s else -s
    }
    val out = ArrayList<Offset>()
    for (i in poly.indices) {
        val cur = poly[i]
        val nxt = poly[(i + 1) % poly.size]
        val sc = side(cur)
        val sn = side(nxt)
        if (sc >= 0f) out.add(cur)
        if ((sc >= 0f) != (sn >= 0f)) {
            val t = sc / (sc - sn)
            out.add(Offset(cur.x + (nxt.x - cur.x) * t, cur.y + (nxt.y - cur.y) * t))
        }
    }
    return out
}

/** A polygon given in unit coordinates, cut out of the card's own rounded rectangle. */
private class ShardShape(private val unit: List<Offset>, private val cornerDp: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val poly = Path().apply {
            unit.forEachIndexed { i, o ->
                val x = o.x * size.width
                val y = o.y * size.height
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val corner = with(density) { cornerDp.toPx() }
        val card = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner, corner)) }
        val shard = Path()
        shard.op(card, poly, PathOperation.Intersect)
        return Outline.Generic(shard)
    }
}

private class Shard(
    val shape: ShardShape,
    val centroid: Offset,
    val dir: Offset,
    val spin: Float,
    val speed: Float,
    val drift: Float,
)

private fun buildShards(cornerDp: Dp): List<Shard> {
    val rect = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))
    val shards = ArrayList<Shard>()
    for (leftOfA in listOf(true, false)) {
        for (leftOfB in listOf(true, false)) {
            val poly = clipToSide(clipToSide(rect, SlashAFrom, SlashATo, leftOfA), SlashBFrom, SlashBTo, leftOfB)
            if (poly.size < 3) continue
            val c = Offset(poly.sumOf { it.x.toDouble() }.toFloat() / poly.size, poly.sumOf { it.y.toDouble() }.toFloat() / poly.size)
            val away = Offset(c.x - 0.5f, c.y - 0.5f)
            val len = sqrt(away.x * away.x + away.y * away.y).let { if (it < 0.001f) 1f else it }
            val i = shards.size
            shards += Shard(
                shape = ShardShape(poly, cornerDp),
                centroid = c,
                dir = Offset(away.x / len, away.y / len),
                spin = (if (c.x >= 0.5f) 1f else -1f) * (12f + i * 7f),
                speed = 0.85f + 0.12f * i,
                drift = away.x / len,
            )
        }
    }
    return shards
}

private class Dust(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val delay: Float,
    val life: Float,
    val radiusDp: Float,
    val color: Color,
)

private fun buildDust(accent: Color): List<Dust> {
    val random = Random(7)
    val palette = listOf(Color.White, Color(0xFFCFD3E3), Color(0xFFB8BCCF), accent)
    return List(130) {
        Dust(
            x = random.nextFloat(),
            y = random.nextFloat(),
            vx = (random.nextFloat() - 0.5f) * 2f,
            vy = -(0.35f + random.nextFloat()),
            delay = 0.16f + random.nextFloat() * 0.45f,
            life = 0.30f + random.nextFloat() * 0.35f,
            radiusDp = 1f + random.nextFloat() * 2.4f,
            color = palette[random.nextInt(palette.size)],
        )
    }
}

/**
 * The press-and-hold experience for a continue-watching card.
 *
 * The screen behind frosts over (a live blur of the real content, so the hero banner keeps moving
 * underneath), the held card floats from its place in the row to the middle of the screen with its title
 * and a small menu below it, and a tap anywhere else (or Back) floats it back home.
 * "Remove" plays a short scene: two sword cuts flash across the card, it falls apart in four pieces that
 * dissolve into dust, the blur lifts and the row closes the gap.
 *
 * Every moving part is driven by an [Animatable] read inside a graphics-layer or draw lambda, so the
 * animation never recomposes while it runs.
 */
@Composable
fun ContinueWatchingActionOverlay(
    target: ContinueWatchingActionTarget,
    backdrop: BackdropState,
    onGoToDetails: (ContinueWatchingEntity) -> Unit,
    onRemove: (ContinueWatchingEntity) -> Unit,
    onFinished: () -> Unit,
) {
    val entry = target.entry
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(ActionPhase.Menu) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var shardsLive by remember { mutableStateOf(false) }

    val open = remember { Animatable(0f) }   // card flight: 0 = back in the row, 1 = centred
    val menu = remember { Animatable(0f) }   // title + menu reveal
    val bg = remember { Animatable(0f) }     // frosted backdrop
    val pulse = remember { Animatable(0f) }  // little punch on every sword hit
    val slashAHead = remember { Animatable(0f) }
    val slashATail = remember { Animatable(0f) }
    val slashBHead = remember { Animatable(0f) }
    val slashBTail = remember { Animatable(0f) }
    val fall = remember { Animatable(0f) }   // shards falling / dust rising, 0..1

    val shards = remember { buildShards(14.dp) }
    val dust = remember { buildDust(AnsuColors.Accent) }

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { bg.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        launch { open.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 330f)) }
        launch {
            delay(90)
            menu.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 420f))
        }
    }

    fun dismiss() {
        if (phase != ActionPhase.Menu) return
        phase = ActionPhase.Closing
        scope.launch {
            launch { menu.animateTo(0f, tween(140, easing = FastOutLinearInEasing)) }
            launch { bg.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
            open.animateTo(0f, spring(dampingRatio = 1f, stiffness = 420f))
            onFinished()
        }
    }

    fun startRemove() {
        if (phase != ActionPhase.Menu) return
        phase = ActionPhase.Removing
        scope.launch {
            launch { menu.animateTo(0f, tween(170, easing = FastOutLinearInEasing)) }
            delay(150)

            // Cut one: steep, then cut two: shallow. Each is a bright head racing along the line with a tail
            // that catches up a moment later, so the streak looks like a blade passing.
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            launch {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(45))
                pulse.animateTo(0f, tween(190))
            }
            launch { slashATail.animateTo(1f, tween(260, delayMillis = 50, easing = LinearEasing)) }
            slashAHead.animateTo(1f, tween(110, easing = LinearEasing))
            delay(70)

            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            launch {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(45))
                pulse.animateTo(0f, tween(190))
            }
            launch { slashBTail.animateTo(1f, tween(260, delayMillis = 50, easing = LinearEasing)) }
            slashBHead.animateTo(1f, tween(110, easing = LinearEasing))
            delay(120) // let the finished cut hang for a beat

            shardsLive = true
            // The list entry goes and the blur lifts while the last dust is still in the air, so the row
            // closing the gap is part of the same moment.
            val wrapUp = launch {
                delay(560)
                onRemove(entry)
                bg.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
            }
            fall.animateTo(1f, tween(1000, easing = LinearEasing))
            wrapUp.join()
            onFinished()
        }
    }

    BackHandler(enabled = true) { dismiss() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { dismiss() } },
    ) {
        val cardWidth = min(maxWidth.value * 0.84f, 320f).dp
        val cardHeight = cardWidth * 9f / 16f
        val stackHeight = cardHeight + GapAfterCard + TitleHeight + SubtitleHeight + GapBeforeMenu + MenuRowHeight * 2 + 1.dp
        val cardUnit = cardWidth.value / CardBaseWidth.value

        val cardWidthPx = with(density) { cardWidth.toPx() }
        val targetLeftPx = with(density) { ((maxWidth - cardWidth) / 2).toPx() }
        val targetTopPx = with(density) { ((maxHeight - stackHeight) / 2 - StackLift).toPx() }
        val fallDistancePx = with(density) { 260.dp.toPx() }
        val popPx = with(density) { 10.dp.toPx() }
        val driftPx = with(density) { 46.dp.toPx() }

        // ---- Frosted backdrop: a live blur of the screen underneath, dimmed ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bg.value.coerceIn(0f, 1f) }
                .frostedBackdrop(backdrop, enabled = true, shape = RectangleShape, radius = 26.dp)
                .background(Color.Black.copy(alpha = if (backdropBlurSupported) 0.42f else 0.82f)),
        )

        // ---- The floating stack: card, title, episode line, menu ----
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -StackLift)
                .width(cardWidth)
                .height(stackHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The card itself, flying in from where it sat in the row.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .graphicsLayer {
                        val p = open.value
                        val startScale = (target.bounds.width / cardWidthPx).coerceIn(0.2f, 1.5f)
                        val s = lerp(startScale, 1f, p) * (1f + 0.045f * pulse.value)
                        scaleX = s
                        scaleY = s
                        val startCenter = Offset(
                            target.bounds.center.x - rootOrigin.x,
                            target.bounds.center.y - rootOrigin.y,
                        )
                        val targetCenter = Offset(targetLeftPx + cardWidthPx / 2f, targetTopPx + size.height / 2f)
                        translationX = (startCenter.x - targetCenter.x) * (1f - p)
                        translationY = (startCenter.y - targetCenter.y) * (1f - p)
                    },
            ) {
                // The whole card, until the cuts land.
                ContinueWatchingThumbnail(
                    entry = entry,
                    scale = cardUnit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (shardsLive) 0f else 1f }
                        .clip(RoundedCornerShape(14.dp * cardUnit)),
                )

                // The four pieces. They are composed from the start (so their pictures are already loaded)
                // and stay invisible until the card is cut.
                shards.forEach { shard ->
                    ContinueWatchingThumbnail(
                        entry = entry,
                        scale = cardUnit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!shardsLive) {
                                    alpha = 0f
                                    return@graphicsLayer
                                }
                                val f = fall.value
                                val pop = 1f - (1f - min(f / 0.14f, 1f)).pow(3)
                                translationX = shard.dir.x * popPx * pop + shard.drift * driftPx * f
                                translationY = shard.dir.y * popPx * pop + f * f * fallDistancePx * shard.speed
                                rotationZ = shard.spin * f
                                transformOrigin = TransformOrigin(shard.centroid.x, shard.centroid.y)
                                val dissolve = smoothStep(0.30f, 0.88f, f)
                                alpha = 1f - dissolve
                                if (backdropBlurSupported && dissolve > 0.02f) {
                                    val r = dissolve * 16.dp.toPx()
                                    renderEffect = BlurEffect(r, r, TileMode.Decal)
                                }
                            }
                            .clip(shard.shape),
                    )
                }

                // Sword cuts and dust. Drawn over everything and allowed to spill past the card's edges.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    fun at(o: Offset) = Offset(o.x * w, o.y * h)
                    val f = fall.value
                    val crack = 1f - smoothStep(0f, 0.12f, f)

                    drawSlash(at(SlashAFrom), at(SlashATo), slashAHead.value, slashATail.value, crack, AnsuColors.Accent)
                    drawSlash(at(SlashBFrom), at(SlashBTo), slashBHead.value, slashBTail.value, crack, AnsuColors.Accent)

                    if (shardsLive) {
                        dust.forEach { d ->
                            val u = (f - d.delay) / d.life
                            if (u <= 0f || u >= 1f) return@forEach
                            val pos = Offset(
                                d.x * w + d.vx * 34.dp.toPx() * u,
                                d.y * h + d.vy * 70.dp.toPx() * u,
                            )
                            val alpha = (1f - u).pow(1.3f) * 0.85f
                            drawCircle(d.color.copy(alpha = alpha), d.radiusDp.dp.toPx() * (1f - 0.5f * u), pos)
                        }
                    }
                }
            }

            Spacer(Modifier.height(GapAfterCard))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TitleHeight)
                    .revealWith(menu, density),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AnsuColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SubtitleHeight)
                    .revealWith(menu, density),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = episodeLine(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = AnsuColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(GapBeforeMenu))

            Box(modifier = Modifier.fillMaxWidth().revealWith(menu, density)) {
                ActionMenu(
                    onGoToDetails = {
                        if (phase == ActionPhase.Menu) onGoToDetails(entry)
                    },
                    onRemove = { startRemove() },
                )
            }
        }
    }
}

private fun episodeLine(entry: ContinueWatchingEntity): String {
    val number = "Episode ${entry.episodeNumber.formatEpisodeNumber()}"
    val name = entry.episodeName.trim()
    return if (name.isBlank() || name.equals(number, ignoreCase = true)) number else "$number · $name"
}

/** Fades, lifts and swells a block in as [progress] goes 0 → 1 (and back out as it returns to 0). */
private fun Modifier.revealWith(progress: Animatable<Float, *>, density: Density): Modifier =
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 22f * density.density
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }

@Composable
private fun ActionMenu(onGoToDetails: () -> Unit, onRemove: () -> Unit) {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tintAlpha = 0.66f,
    ) {
        Column {
            ActionMenuRow("Go to details", Icons.Outlined.Info, AnsuColors.TextPrimary, onGoToDetails)
            Box(Modifier.fillMaxWidth().height(1.dp).background(AnsuColors.StrokeGlass))
            ActionMenuRow("Remove", Icons.Outlined.Delete, AnsuColors.Error, onRemove)
        }
    }
}

@Composable
private fun ActionMenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MenuRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * One blade pass: a glowing streak between [tail] and [head] (both 0..1 along the line), then a hairline
 * crack that stays on the card until the pieces part. The streak fades in from its tail.
 */
private fun DrawScope.drawSlash(from: Offset, to: Offset, head: Float, tail: Float, crack: Float, glow: Color) {
    if (head <= 0f) return
    val p1 = lerp(from, to, head)
    if (tail < 1f) {
        val p0 = lerp(from, to, tail)
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        if (max(kotlin.math.abs(dx), kotlin.math.abs(dy)) > 1f) {
            drawLine(
                brush = Brush.linearGradient(listOf(Color.Transparent, glow.copy(alpha = 0.55f)), start = p0, end = p1),
                start = p0,
                end = p1,
                strokeWidth = 16.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                brush = Brush.linearGradient(listOf(Color.Transparent, Color.White), start = p0, end = p1),
                start = p0,
                end = p1,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
    if (head >= 1f && crack > 0f) {
        drawLine(
            color = Color.White.copy(alpha = 0.85f * crack),
            start = from,
            end = to,
            strokeWidth = 1.2.dp.toPx(),
        )
    }
}
