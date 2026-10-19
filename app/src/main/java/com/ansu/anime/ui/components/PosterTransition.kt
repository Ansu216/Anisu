package com.ansu.anime.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.roundToInt

/**
 * Remembers which poster was tapped (where it is on screen, its image and its corner radius), so the
 * details page can grow out of that exact poster when it opens and shrink back into it when the user
 * goes back, the same morph as an app opening from, and closing into, its launcher icon.
 *
 * A poster marks itself with [posterTransitionOrigin]; on touch-down that writes its [Origin] here as
 * the "pending" origin. The details destination claims the pending origin once, when it first composes,
 * and keeps it per back-stack entry so a back press always closes into the poster it opened from.
 */
object PosterTransition {
    /** Open: fast out of the poster, long soft landing (Material "emphasized decelerate"). */
    const val OPEN_MILLIS = 440

    /**
     * The details page is heavy: composing it on the first frame eats a big, variable chunk of the animation's
     * clock, so the window used to appear already half open. The open therefore waits this long before it starts
     * moving, which gives that first frame time to finish; the close has nothing to compose and needs no wait.
     */
    private const val OPEN_START_DELAY_MILLIS = 90

    /** Close: a little quicker than the open, as a launcher does. */
    const val CLOSE_MILLIS = 320

    /** Black-bars open (the "Your Next Watch" cards): long enough to see the bars grow, then the page shows up. */
    const val BARS_OPEN_MILLIS = 640

    /** Black-bars close: the page goes, the bars close over the screen, then pull back into the card. */
    const val BARS_CLOSE_MILLIS = 520

    private val OpenEasing = CubicBezierEasing(0.3f, 0f, 0f, 1f)

    /** Standard in-out curve, so the bars visibly accelerate out of the card and settle over the screen. */
    private val BarsEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Settles gently into the poster at the end (Material "emphasized"). */
    private val CloseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * What the details page grows out of. [posterUrl] is drawn inside the growing window. A [cover], when
     * given, is drawn there instead of the bare picture (it gets the origin's size in pixels): the home
     * banner passes its own slide, so the window opens from, and closes into, the banner exactly as it looks,
     * with its gradient and title, rather than a plain picture that the real banner then replaces.
     */
    class Origin(
        val bounds: Rect,
        val posterUrl: String?,
        val cornerRadius: Dp,
        val cover: (@Composable (Size) -> Unit)? = null,
        /** True: no growing window; black bars grow from the card's top and bottom edges until the screen is covered. */
        val bars: Boolean = false,
    )

    /** A tap's touch-down and the navigation it triggers are far closer than this; older origins are stale. */
    private const val PENDING_TTL_MILLIS = 1500L

    private var pending: Origin? = null
    private var pendingAt = 0L
    private val claimed = HashMap<String, Origin?>()

    internal fun openSpec(delayed: Boolean = false, bars: Boolean = false) =
        if (bars) {
            tween<Float>(durationMillis = BARS_OPEN_MILLIS, delayMillis = if (delayed) OPEN_START_DELAY_MILLIS else 0, easing = BarsEasing)
        } else {
            tween<Float>(durationMillis = OPEN_MILLIS, delayMillis = if (delayed) OPEN_START_DELAY_MILLIS else 0, easing = OpenEasing)
        }

    internal fun closeSpec(bars: Boolean = false) =
        if (bars) tween<Float>(durationMillis = BARS_CLOSE_MILLIS, easing = BarsEasing) else tween<Float>(durationMillis = CLOSE_MILLIS, easing = CloseEasing)

    /** True when [entryId] has been opened before, i.e. this is a return to it (from the player, say), not a first open. */
    fun hasClaimed(entryId: String): Boolean = claimed.containsKey(entryId)

    fun setPending(origin: Origin) {
        pending = origin
        pendingAt = SystemClock.uptimeMillis()
    }

    /** Forget the pending origin, e.g. before a navigation that should simply cross-fade. */
    fun clearPending() {
        pending = null
    }

    /**
     * The origin for the back-stack entry [entryId]: the pending one on first call (consumed), the same
     * value on every later call, or null when the entry was not opened from a poster.
     */
    fun originFor(entryId: String): Origin? {
        if (claimed.containsKey(entryId)) return claimed[entryId]
        val fresh = pending?.takeIf { SystemClock.uptimeMillis() - pendingAt <= PENDING_TTL_MILLIS }
        pending = null
        // Bounded: entries of long-gone screens do not need to be kept.
        if (claimed.size > 32) claimed.clear()
        claimed[entryId] = fresh
        return fresh
    }
}

/**
 * True while the details page is at rest, i.e. not in the middle of opening or closing. The page is heavy, and
 * its data (artwork, AniList details, a thousand-episode list) used to land in the middle of the animation,
 * each arrival rebuilding the hero and the list inside a frame that also had to move the window, which is
 * the occasional stutter. The page holds those updates back while this is false and applies them in one go
 * once the motion has finished. True by default, for any screen not hosted by [PosterExpandContainer].
 */
val LocalPosterSettled = compositionLocalOf { true }

/**
 * Marks a poster (or any tappable card) as the starting point of the details open/close animation.
 * Place it on the poster image itself, before its `clip`. It only observes the touch-down without
 * consuming it, so clicks, scrolling and long-presses behave exactly as before.
 *
 * [posterUrl] is the image drawn inside the growing window (pass the same URL the poster shows), and
 * [cornerRadius] the poster's own corner radius, so the window starts out with exactly the poster's shape.
 */
fun Modifier.posterTransitionOrigin(
    posterUrl: String? = null,
    cornerRadius: Dp = 12.dp,
    cover: (@Composable (Size) -> Unit)? = null,
    /** Open with growing black bars from the card's top and bottom edges instead of the growing window. */
    bars: Boolean = false,
): Modifier = composed {
    val holder = remember { CoordinatesHolder() }
    // Read at touch-down, so the cover always reflects what is on screen then (a logo that arrived late, say).
    val latestCover by rememberUpdatedState(cover)
    this
        .onGloballyPositioned { holder.coordinates = it }
        .pointerInput(posterUrl, cornerRadius, bars) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                holder.coordinates?.takeIf { it.isAttached }?.let {
                    PosterTransition.setPending(PosterTransition.Origin(it.fullBoundsInRoot(), posterUrl, cornerRadius, latestCover, bars))
                }
            }
        }
}

/**
 * Lets a whole card start the details animation from its poster. A card whose poster is only a small part of
 * it (a My Space list row) used to animate only when the poster itself was touched; a tap on the title or the
 * empty part of the card opened the page with a plain fade. The poster is marked with
 * [posterTransitionAnchor], the card with [posterTransitionTrigger], and both share one handle.
 */
class PosterOriginHandle {
    internal var coordinates: LayoutCoordinates? = null
}

/** Marks the poster that the animation should grow out of, for a card that uses [posterTransitionTrigger]. */
fun Modifier.posterTransitionAnchor(handle: PosterOriginHandle): Modifier =
    onGloballyPositioned { handle.coordinates = it }

/**
 * On a card: a touch anywhere on it starts the animation from the poster marked with [posterTransitionAnchor].
 * Like [posterTransitionOrigin] it only observes the touch-down, so clicks and scrolling are untouched.
 */
fun Modifier.posterTransitionTrigger(
    handle: PosterOriginHandle,
    posterUrl: String?,
    cornerRadius: Dp = 12.dp,
): Modifier = pointerInput(handle, posterUrl, cornerRadius) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        handle.coordinates?.takeIf { it.isAttached }?.let {
            PosterTransition.setPending(PosterTransition.Origin(it.fullBoundsInRoot(), posterUrl, cornerRadius))
        }
    }
}

/**
 * The poster's whole rectangle in the root, even the part that is scrolled or cut off outside the screen or its
 * list. boundsInRoot() returns only the visible part, so a poster sitting at a side or corner (half off the screen
 * edge) gave a smaller, shifted rectangle and the opening animation started from the wrong place.
 */
private fun LayoutCoordinates.fullBoundsInRoot(): Rect = Rect(positionInRoot(), size.toSize())

/** A plain (non-state) holder: layout coordinates change every frame while scrolling and must not recompose. */
private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

/** Where the container sits in the window; a plain holder for the same reason. */
private class OffsetHolder {
    var offset: Offset = Offset.Zero
}

/**
 * The window's rounded rectangle, with "continuous" corners (a squircle, like iOS and modern launchers): the
 * curve eases into the straight edges instead of meeting them at a hard circular arc, which is what made the
 * corners look boxy. Equal windows compare equal, so an unchanged window does not invalidate the layer.
 */
private class WindowShape(val rect: Rect, val radiusPx: Float, private val scratch: Path) : Shape {
    // Equal windows are equal shapes, so an unchanged window does not invalidate the layer's outline.
    override fun equals(other: Any?): Boolean = other is WindowShape && other.rect == rect && other.radiusPx == radiusPx
    override fun hashCode(): Int = 31 * rect.hashCode() + radiusPx.hashCode()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (radiusPx < 0.5f) return Outline.Rectangle(rect)
        // The curve starts a little further out along each edge than a circle's would.
        val e = (radiusPx * 1.28f).coerceAtMost(minOf(rect.width, rect.height) / 2f)
        val k = e * 0.36f // control point distance from the corner
        val l = rect.left; val t = rect.top; val r = rect.right; val b = rect.bottom
        // One path, rewound and refilled every frame: a fresh native Path per frame (60 a second for the whole
        // animation) piled up garbage that a collection would then pause a frame for. The platform copies the
        // path into the layer's outline right away, so reusing it is safe.
        val path = scratch
        path.rewind()
        path.moveTo(l + e, t)
        path.lineTo(r - e, t)
        path.cubicTo(r - k, t, r, t + k, r, t + e)
        path.lineTo(r, b - e)
        path.cubicTo(r, b - k, r - k, b, r - e, b)
        path.lineTo(l + e, b)
        path.cubicTo(l + k, b, l, b - k, l, b - e)
        path.lineTo(l, t + e)
        path.cubicTo(l, t + k, l + k, t, l + e, t)
        path.close()
        return Outline.Generic(path)
    }
}

/** The window at progress [p]: the poster's bounds at 0, the whole container at 1. Container coordinates. */
private fun windowBounds(origin: Rect?, containerOffset: Offset, containerSize: Size, p: Float): Rect {
    if (origin == null) return Rect(0f, 0f, containerSize.width, containerSize.height)
    val from = origin.translate(-containerOffset.x, -containerOffset.y)
    return Rect(
        left = lerp(from.left, 0f, p),
        top = lerp(from.top, 0f, p),
        right = lerp(from.right, containerSize.width, p),
        bottom = lerp(from.bottom, containerSize.height, p),
    )
}

/**
 * Poster radius -> the phone's own screen-corner radius, smoothly, and it stays rounded all the way to full size
 * (the way an app opening from its icon does), instead of going square before the end, which looked blocky.
 * Only the very last frame drops the clip: the display's own rounded corners hide that.
 */
private fun windowCornerPx(posterPx: Float, screenPx: Float, p: Float): Float {
    if (p >= 0.999f) return 0f
    val t = (p / 0.7f).coerceIn(0f, 1f)
    val eased = t * t * (3f - 2f * t)
    return lerp(posterPx, screenPx, eased)
}

/**
 * Hosts a details destination so it opens out of the tapped poster and closes back into it.
 *
 * A rounded window with the poster's own shape grows from the poster's exact bounds to the full screen
 * (and shrinks back the same way). The poster image is drawn in the window at its own aspect ratio, so
 * it is never stretched, and the page fades in over it. The page itself is never resized or stretched:
 * it stays at full-screen size and is only revealed by the window, so each frame costs a clip and an
 * alpha change instead of a re-layout or an off-screen copy of the whole page.
 *
 * The progress is driven by the destination's own enter/exit transition, so the NavHost keeps the page
 * on screen until the close animation has finished, and a predictive back gesture scrubs it. A destination
 * that was not opened from a poster just cross-fades.
 *
 * [isOnBackStack] must report whether this entry is still on the back stack; it is how a push to
 * another page (the page stays, covered, and must not shrink) is told apart from a back press.
 */
@Composable
fun AnimatedContentScope.PosterExpandContainer(
    entryId: String,
    isOnBackStack: () -> Boolean,
    content: @Composable () -> Unit,
) {
    // Order matters: the first open is read before originFor() records the entry as opened.
    val firstOpen = remember(entryId) { !PosterTransition.hasClaimed(entryId) }
    val origin = remember(entryId) { PosterTransition.originFor(entryId) }
    val progress = transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.PostExit) {
                PosterTransition.closeSpec(bars = origin?.bars == true)
            } else {
                PosterTransition.openSpec(delayed = firstOpen && origin != null, bars = origin?.bars == true)
            }
        },
        label = "posterExpand",
    ) { state ->
        when (state) {
            // Coming back to a page that was already open (e.g. from the player) must not replay the open.
            EnterExitState.PreEnter -> if (firstOpen) 0f else 1f
            EnterExitState.Visible -> 1f
            // Covered by another page: stay fully open. Popped: shrink back into the poster.
            EnterExitState.PostExit -> if (isOnBackStack()) 1f else 0f
        }
    }
    val density = LocalDensity.current
    val screenCornerPx = with(density) { 40.dp.toPx() }
    val posterCornerPx = with(density) { (origin?.cornerRadius ?: 0.dp).toPx() }
    val originBounds = origin?.bounds
    // A poster image is what makes the morph look right: with no image to draw inside the window the
    // close would only shrink a half-transparent page over the background. Those cases cross-fade.
    val originCover = origin?.cover
    val bars = origin != null && origin.bars
    val canCover = !bars && (origin?.posterUrl != null || originCover != null) && originBounds != null
    val container = remember { OffsetHolder() }
    val shapePath = remember { Path() }
    // The page may apply its held-back data once the motion is over (see LocalPosterSettled).
    val settled by remember { derivedStateOf { progress.value >= 0.999f } }
    // True while the page is being popped (back press / back gesture) rather than covered by another page.
    // The close shrinks the whole page into the poster; the open is left exactly as it was.
    val closing = transition.targetState == EnterExitState.PostExit && !isOnBackStack()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { container.offset = it.positionInRoot() }
            .graphicsLayer {
                val p = progress.value
                if (bars) {
                    // The bars draw themselves and the page only fades in under them, so nothing is clipped here.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                } else if (!canCover) {
                    // No poster to grow from: a plain fade, without an off-screen copy of the page.
                    alpha = (p * 2f).coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                } else {
                    val bounds = windowBounds(originBounds, container.offset, size, p)
                    val radius = windowCornerPx(posterCornerPx, screenCornerPx, p)
                        .coerceAtMost(minOf(bounds.width, bounds.height) / 2f)
                    shape = WindowShape(bounds, radius, shapePath)
                    clip = true
                }
            }
            .then(
                if (bars) {
                    // Bars mode has no window to hold the page's black: the screen must stay the home screen until
                    // the bars have covered it, and only then is it black underneath (the bars end full-screen, so
                    // the switch is invisible; on close it hands back to the bars the same way).
                    Modifier.drawBehind { if (progress.value >= BARS_COVERED_AT) drawRect(AnsuColors.Background) }
                } else {
                    Modifier.background(AnsuColors.Background)
                },
            ),
    ) {
        if (origin != null && originCover != null) {
            // The banner itself, kept composed for as long as the page lives: it is prepared (artwork out of the
            // cache, logo decoded) while the page is open, so a back press finds it ready to shrink into.
            // It costs nothing while hidden: a layer with no alpha is not drawn.
            BannerCover(
                cover = originCover,
                bounds = origin.bounds,
                boundsAt = { containerSize ->
                    windowBounds(origin.bounds, container.offset, containerSize, progress.value)
                },
                alpha = { ((0.75f - progress.value) / 0.25f).coerceIn(0f, 1f) },
            )
        } else if (origin != null && origin.posterUrl != null) {
            // Kept composed for as long as the page lives, like the banner above: it used to be dropped once the
            // page was open and composed again on the first frame of the close, where the heavy page was also
            // recomposing, so the close started late (the page froze for a moment before shrinking). Its alpha is 0
            // while the page is open, so nothing is drawn; its picture is already in the cache.
            PosterCover(
                posterUrl = origin.posterUrl,
                posterBounds = origin.bounds,
                boundsAt = { containerSize ->
                    windowBounds(origin.bounds, container.offset, containerSize, progress.value)
                },
                // Opaque while the window is near the poster, gone once the page fills it; played the other
                // way on close. Keeping it opaque a little longer hides the shrinking page instead of
                // leaving a half-transparent picture floating over the background.
                alpha = { ((0.6f - progress.value) / 0.35f).coerceIn(0f, 1f) },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (bars) {
                        // Hidden until the bars have covered the screen, then shown in as they pull away; on close
                        // the page goes first and the bars close over the empty screen.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = ((progress.value - BARS_COVERED_AT) / 0.4f).coerceIn(0f, 1f)
                    } else if (canCover) {
                        val p = progress.value
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        if (closing) {
                            // Close: the page itself deflates with the window. It is scaled by the window's width
                            // and pinned to the window's top-left corner, so the sides pull in and the page
                            // visibly shrinks down into the poster instead of being cropped by a shrinking frame.
                            val bounds = windowBounds(originBounds, container.offset, size, p)
                            val s = if (size.width > 0f) bounds.width / size.width else 1f
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = bounds.left
                            translationY = bounds.top
                            scaleX = s
                            scaleY = s
                            // Hands over to the poster cover early, so the shrinking page never reads as a tiny screen.
                            // A banner is as wide as the screen, so the page does not narrow at all: it hands
                            // over to the banner cover sooner and leaves the banner fully visible as it lands.
                            alpha = if (originCover != null) ((p - 0.35f) / 0.35f).coerceIn(0f, 1f) else ((p - 0.12f) / 0.4f).coerceIn(0f, 1f)
                        } else {
                            // The page fades in once the window has grown a little, and settles in from just under full size.
                            alpha = if (originCover != null) ((p - 0.35f) / 0.35f).coerceIn(0f, 1f) else ((p - 0.15f) / 0.45f).coerceIn(0f, 1f)
                            val s = lerp(0.94f, 1f, p)
                            scaleX = s
                            scaleY = s
                        }
                    }
                },
        ) {
            CompositionLocalProvider(LocalPosterSettled provides settled) {
                content()
            }
        }
        if (bars && origin != null) {
            BarsCurtain(
                bounds = origin.bounds,
                containerOffset = { container.offset },
                progress = { progress.value },
            )
        }
    }
}

/** Progress at which the bars have grown over the whole screen; the page shows up after this. */
private const val BARS_COVERED_AT = 0.58f

/**
 * Two black bars, one growing up out of the card's top edge and one down out of its bottom edge. Each also grows
 * across the card to its middle, so when they have finished the whole screen is black. From then on the page
 * shows up out of the black. On close it plays backwards: the page goes, the bars start from the full screen and
 * pull in to the card's edges, uncovering the home screen.
 */
@Composable
private fun BarsCurtain(bounds: Rect, containerOffset: () -> Offset, progress: () -> Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val p = progress()
        // Past this point the screen behind is solid black already (see the container), so nothing is drawn.
        if (p <= 0f || p >= BARS_COVERED_AT) return@Canvas
        // 0 while the bars are just a line at the card's edges, 1 when they have covered the screen.
        val grow = p / BARS_COVERED_AT
        val color = AnsuColors.Background
        val off = containerOffset()
        val top = (bounds.top - off.y).coerceIn(0f, size.height)
        val bottom = (bounds.bottom - off.y).coerceIn(0f, size.height)
        val mid = (top + bottom) / 2f
        val upperTop = lerp(top, 0f, grow)
        val upperBottom = lerp(top, mid, grow)
        val lowerTop = lerp(bottom, mid, grow)
        val lowerBottom = lerp(bottom, size.height, grow)
        // A pixel of overlap, so the two bars never leave a hairline where they meet over the card.
        drawRect(color, topLeft = Offset(0f, upperTop), size = Size(size.width, (upperBottom - upperTop + 1f).coerceAtLeast(0f)))
        drawRect(color, topLeft = Offset(0f, lowerTop - 1f), size = Size(size.width, (lowerBottom - lowerTop + 1f).coerceAtLeast(0f)))
    }
}

/**
 * A caller-supplied cover (the home banner's own slide) placed at the window's bounds. It is laid out at the
 * window's size, so the slide's title block stays pinned to the window's bottom edge as it grows and shrinks.
 */
@Composable
private fun BannerCover(
    cover: @Composable (Size) -> Unit,
    bounds: Rect,
    boundsAt: (containerSize: Size) -> Rect,
    alpha: () -> Float,
) {
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val window = boundsAt(Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
                val w = window.width.roundToInt().coerceAtLeast(1)
                val h = window.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(window.left.roundToInt(), window.top.roundToInt())
                }
            }
            .graphicsLayer { this.alpha = alpha() },
    ) {
        cover(bounds.size)
    }
}

/**
 * The tapped poster, drawn to fill the growing window with its aspect ratio kept (cropped, never
 * stretched). Only this one node is re-laid out while the window grows, not the page.
 */
@Composable
private fun PosterCover(
    posterUrl: String,
    posterBounds: Rect,
    boundsAt: (containerSize: Size) -> Rect,
    alpha: () -> Float,
) {
    val context = LocalContext.current
    // Same URL and size as the poster the user tapped, so it is served from the memory cache with no
    // flash; the placeholder key covers a poster that was cached at a different size.
    val request = remember(posterUrl, posterBounds) {
        ImageRequest.Builder(context)
            .data(posterUrl)
            .size(posterBounds.width.roundToInt().coerceAtLeast(1), posterBounds.height.roundToInt().coerceAtLeast(1))
            .placeholderMemoryCacheKey(MemoryCache.Key(posterUrl))
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .layout { measurable, constraints ->
                val bounds = boundsAt(Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
                val w = bounds.width.roundToInt().coerceAtLeast(1)
                val h = bounds.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(bounds.left.roundToInt(), bounds.top.roundToInt())
                }
            }
            .graphicsLayer { this.alpha = alpha() },
    )
}
