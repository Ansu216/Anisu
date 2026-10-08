package com.ansu.anime.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
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

    private val OpenEasing = CubicBezierEasing(0.3f, 0f, 0f, 1f)

    /** Settles gently into the poster at the end (Material "emphasized"). */
    private val CloseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** What the details page grows out of. [posterUrl] is drawn inside the growing window. */
    class Origin(val bounds: Rect, val posterUrl: String?, val cornerRadius: Dp)

    /** A tap's touch-down and the navigation it triggers are far closer than this; older origins are stale. */
    private const val PENDING_TTL_MILLIS = 1500L

    private var pending: Origin? = null
    private var pendingAt = 0L
    private val claimed = HashMap<String, Origin?>()

    internal fun openSpec(delayed: Boolean = false) =
        tween<Float>(durationMillis = OPEN_MILLIS, delayMillis = if (delayed) OPEN_START_DELAY_MILLIS else 0, easing = OpenEasing)

    internal fun closeSpec() = tween<Float>(durationMillis = CLOSE_MILLIS, easing = CloseEasing)

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
): Modifier = composed {
    val holder = remember { CoordinatesHolder() }
    this
        .onGloballyPositioned { holder.coordinates = it }
        .pointerInput(posterUrl, cornerRadius) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                holder.coordinates?.takeIf { it.isAttached }?.let {
                    PosterTransition.setPending(PosterTransition.Origin(it.boundsInRoot(), posterUrl, cornerRadius))
                }
            }
        }
}

/** A plain (non-state) holder: layout coordinates change every frame while scrolling and must not recompose. */
private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

/** Where the container sits in the window; a plain holder for the same reason. */
private class OffsetHolder {
    var offset: Offset = Offset.Zero
}

/** The window's rounded rectangle. A data class, so an unchanged window does not invalidate the layer. */
private data class WindowShape(val rect: Rect, val radiusPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radiusPx)))
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

/** Poster radius -> phone-screen radius by halfway, then the screen's own square edge at full size. */
private fun windowCornerPx(posterPx: Float, screenPx: Float, p: Float): Float =
    if (p < 0.5f) lerp(posterPx, screenPx, p / 0.5f) else lerp(screenPx, 0f, (p - 0.5f) / 0.5f)

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
                PosterTransition.closeSpec()
            } else {
                PosterTransition.openSpec(delayed = firstOpen && origin != null)
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
    val screenCornerPx = with(density) { 28.dp.toPx() }
    val posterCornerPx = with(density) { (origin?.cornerRadius ?: 0.dp).toPx() }
    val originBounds = origin?.bounds
    val container = remember { OffsetHolder() }
    // The poster cover is only needed until the page is fully open; dropping it then frees its layer.
    val coverVisible by remember { derivedStateOf { progress.value < 0.999f } }
    // True while the page is being popped (back press / back gesture) rather than covered by another page.
    // The close shrinks the whole page into the poster; the open is left exactly as it was.
    val closing = transition.targetState == EnterExitState.PostExit && !isOnBackStack()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { container.offset = it.positionInRoot() }
            .graphicsLayer {
                val p = progress.value
                if (originBounds == null) {
                    // No poster to grow from: a plain fade, without an off-screen copy of the page.
                    alpha = (p * 2f).coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                } else {
                    val bounds = windowBounds(originBounds, container.offset, size, p)
                    val radius = windowCornerPx(posterCornerPx, screenCornerPx, p)
                        .coerceAtMost(minOf(bounds.width, bounds.height) / 2f)
                    shape = WindowShape(bounds, radius)
                    clip = true
                }
            }
            .background(AnsuColors.Background),
    ) {
        if (origin != null && origin.posterUrl != null && coverVisible) {
            PosterCover(
                posterUrl = origin.posterUrl,
                posterBounds = origin.bounds,
                boundsAt = { containerSize ->
                    windowBounds(origin.bounds, container.offset, containerSize, progress.value)
                },
                // Fully there until the page is open, then gone; played backwards on close.
                alpha = { ((1f - progress.value) / 0.15f).coerceIn(0f, 1f) },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (originBounds != null) {
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
                            alpha = ((p - 0.12f) / 0.4f).coerceIn(0f, 1f)
                        } else {
                            // The page fades in once the window has grown a little, and settles in from just under full size.
                            alpha = ((p - 0.15f) / 0.45f).coerceIn(0f, 1f)
                            val s = lerp(0.94f, 1f, p)
                            scaleX = s
                            scaleY = s
                        }
                    }
                },
        ) {
            content()
        }
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
