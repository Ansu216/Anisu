package com.ansu.anime.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
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

    /** Two-stage open (the "Your Next Watch" cards): sides to the screen edges, then top and bottom to the corners. */
    const val STAGED_OPEN_MILLIS = 760

    /** Two-stage close: the same path back, a little quicker, landing softly in the card. */
    const val STAGED_CLOSE_MILLIS = 560

    /** Progress at which the open's first stage (sides to the edges) ends and the second (top and bottom) begins. */
    internal const val STAGE_SPLIT = 0.38f

    /** Stage one: the card widens to the screen edges. Ease-out, so it leaves the card fast. */
    internal val StageOneEasing = CubicBezierEasing(0.25f, 0.9f, 0.35f, 1f)

    /** Stage two: top and bottom travel to the corners. Ease-out, continuing from where stage one ended. */
    internal val StageTwoEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** The close eases the progress itself: it leaves briskly and settles softly into the card. */
    private val StagedCloseEasing = CubicBezierEasing(0.15f, 0.55f, 0.3f, 1f)

    private val OpenEasing = CubicBezierEasing(0.3f, 0f, 0f, 1f)

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
        /**
         * True: the two-stage open. The window first widens until its sides touch the screen edges, then grows from
         * its top and bottom edges together until it reaches the four corners.
         */
        val staged: Boolean = false,
    )

    /** A tap's touch-down and the navigation it triggers are far closer than this; older origins are stale. */
    private const val PENDING_TTL_MILLIS = 1500L

    /**
     * The back-stack entry of the page UNDER a details page that is opening or closing, and how far along that
     * motion is (0 closed, 1 open). The page under reads it through [posterBlurBehind] and blurs itself by that
     * much, the way a phone's home screen softens behind an app that opens from an icon and sharpens again as
     * it closes. Null when nothing is animating, so no other page is ever blurred.
     */
    internal var blurBehindEntryId: String? by mutableStateOf(null)
    internal var blurProgress: Float by mutableFloatStateOf(0f)

    /** The page under blurs by this much (in dp) when the details page is fully open. */
    internal val BLUR_RADIUS_DP = 18.dp

    /**
     * The blur radius in px for a motion [amount] (0..1). It reaches full strength by the halfway point (the details
     * page covers the screen soon after, so later blur is never seen) and moves in a few steps instead of smoothly:
     * a blur that changes every frame is re-rendered over the whole page every frame, which is what made the open and
     * close stutter, while an unchanged radius is drawn from the cached result.
     */
    internal fun blurRadiusPx(amount: Float, density: Float): Float {
        val t = (amount / 0.5f).coerceIn(0f, 1f)
        val step = Math.round(t * BLUR_STEPS) / BLUR_STEPS.toFloat()
        return BLUR_RADIUS_DP.value * density * step
    }
    private const val BLUR_STEPS = 5

    private var pending: Origin? = null
    private var pendingAt = 0L
    private val claimed = HashMap<String, Origin?>()

    internal fun openSpec(delayed: Boolean = false, staged: Boolean = false) =
        if (staged) {
            // Linear in time: the two stages carry their own easing (see stagedWindowBounds), so no second curve sits on top.
            tween<Float>(durationMillis = STAGED_OPEN_MILLIS, delayMillis = if (delayed) OPEN_START_DELAY_MILLIS else 0, easing = LinearEasing)
        } else {
            tween<Float>(durationMillis = OPEN_MILLIS, delayMillis = if (delayed) OPEN_START_DELAY_MILLIS else 0, easing = OpenEasing)
        }

    internal fun closeSpec(staged: Boolean = false) =
        if (staged) tween<Float>(durationMillis = STAGED_CLOSE_MILLIS, easing = StagedCloseEasing) else tween<Float>(durationMillis = CLOSE_MILLIS, easing = CloseEasing)

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
 * Blurs this page while a details page opens over it or closes back to it, following the poster animation, and
 * leaves it sharp otherwise. [entryId] is this page's back-stack entry id: only the page directly under the
 * animating details page reacts. It is always on (there is no switch for it), and does nothing below Android 12, which
 * cannot blur a layer (the page is then only dimmed, as before).
 */
fun Modifier.posterBlurBehind(entryId: String): Modifier =
    if (!backdropBlurSupported) {
        this
    } else {
        graphicsLayer {
            val amount = if (PosterTransition.blurBehindEntryId == entryId) PosterTransition.blurProgress else 0f
            val radius = PosterTransition.blurRadiusPx(amount, density)
            renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
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
    cover: (@Composable (Size) -> Unit)? = null,
    /** Open in two stages (sides to the screen edges, then top and bottom to the corners) instead of the plain window. */
    staged: Boolean = false,
): Modifier = composed {
    val holder = remember { CoordinatesHolder() }
    // Read at touch-down, so the cover always reflects what is on screen then (a logo that arrived late, say).
    val latestCover by rememberUpdatedState(cover)
    this
        .onGloballyPositioned { holder.coordinates = it }
        .pointerInput(posterUrl, cornerRadius, staged) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                holder.coordinates?.takeIf { it.isAttached }?.let {
                    PosterTransition.setPending(PosterTransition.Origin(it.fullBoundsInRoot(), posterUrl, cornerRadius, latestCover, staged))
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
private fun windowBounds(
    origin: Rect?,
    containerOffset: Offset,
    containerSize: Size,
    p: Float,
    staged: Boolean = false,
    breathPx: Float = 0f,
): Rect {
    if (origin == null) return Rect(0f, 0f, containerSize.width, containerSize.height)
    val from = origin.translate(-containerOffset.x, -containerOffset.y)
    if (staged) return stagedWindowBounds(from, containerSize, p, breathPx)
    return Rect(
        left = lerp(from.left, 0f, p),
        top = lerp(from.top, 0f, p),
        right = lerp(from.right, containerSize.width, p),
        bottom = lerp(from.bottom, containerSize.height, p),
    )
}

/**
 * The two-stage window at progress [p] (0 = the card, 1 = the whole container), in container coordinates.
 *
 * Stage one: the card widens until its left and right sides touch the container's edges, growing a touch taller
 * ([breathPx] on each side) so it has some weight. Stage two: from that full-width strip the top edge travels to the
 * top and the bottom edge to the bottom at the same time, until the window reaches the four corners. Each stage has
 * its own ease-out curve and the second starts exactly where the first ends, so the motion never stops or steps.
 */
private fun stagedWindowBounds(from: Rect, containerSize: Size, p: Float, breathPx: Float): Rect {
    val split = PosterTransition.STAGE_SPLIT
    val stripTop = from.top - breathPx
    val stripBottom = from.bottom + breathPx
    return if (p <= split) {
        val t = PosterTransition.StageOneEasing.transform((p / split).coerceIn(0f, 1f))
        Rect(
            left = lerp(from.left, 0f, t),
            top = lerp(from.top, stripTop, t),
            right = lerp(from.right, containerSize.width, t),
            bottom = lerp(from.bottom, stripBottom, t),
        )
    } else {
        val t = PosterTransition.StageTwoEasing.transform(((p - split) / (1f - split)).coerceIn(0f, 1f))
        Rect(
            left = 0f,
            top = lerp(stripTop, 0f, t),
            right = containerSize.width,
            bottom = lerp(stripBottom, containerSize.height, t),
        )
    }
}

/**
 * Corner radius of the two-stage window: the card's own radius softens to [midPx] as the sides reach the screen
 * edges, then grows to the phone's screen-corner radius ([screenPx]) as the window reaches full size.
 */
private fun stagedCornerPx(posterPx: Float, midPx: Float, screenPx: Float, p: Float): Float {
    if (p >= 0.999f) return 0f
    val split = PosterTransition.STAGE_SPLIT
    return if (p <= split) {
        lerp(posterPx, midPx, PosterTransition.StageOneEasing.transform((p / split).coerceIn(0f, 1f)))
    } else {
        lerp(midPx, screenPx, PosterTransition.StageTwoEasing.transform(((p - split) / (1f - split)).coerceIn(0f, 1f)))
    }
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
    /** The back-stack entry of the page this one opened over; it blurs while this page opens and closes. Null: no blur. */
    behindEntryId: String? = null,
    content: @Composable () -> Unit,
) {
    // Order matters: the first open is read before originFor() records the entry as opened.
    val firstOpen = remember(entryId) { !PosterTransition.hasClaimed(entryId) }
    val origin = remember(entryId) { PosterTransition.originFor(entryId) }
    val progress = transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.PostExit) {
                PosterTransition.closeSpec(staged = origin?.staged == true)
            } else {
                PosterTransition.openSpec(delayed = firstOpen && origin != null, staged = origin?.staged == true)
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
    val staged = origin != null && origin.staged
    val canCover = (origin?.posterUrl != null || originCover != null) && originBounds != null
    val breathPx = with(density) { 6.dp.toPx() }
    val stagedMidCornerPx = with(density) { 10.dp.toPx() }
    val container = remember { OffsetHolder() }
    val shapePath = remember { Path() }
    // The page may apply its held-back data once the motion is over (see LocalPosterSettled).
    val settled by remember { derivedStateOf { progress.value >= 0.999f } }
    // True while the page is being popped (back press / back gesture) rather than covered by another page.
    // The close shrinks the whole page into the poster; the open is left exactly as it was.
    val closing = transition.targetState == EnterExitState.PostExit && !isOnBackStack()

    // The page under blurs as this one opens and sharpens as it closes. Only the growing-window animation does this
    // (the plain fade has nothing to look through).
    if (behindEntryId != null && canCover && backdropBlurSupported) {
        LaunchedEffect(entryId, behindEntryId) {
            snapshotFlow { progress.value }.collect { p ->
                PosterTransition.blurBehindEntryId = behindEntryId
                PosterTransition.blurProgress = p
            }
        }
        DisposableEffect(entryId, behindEntryId) {
            onDispose {
                if (PosterTransition.blurBehindEntryId == behindEntryId) {
                    PosterTransition.blurBehindEntryId = null
                    PosterTransition.blurProgress = 0f
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { container.offset = it.positionInRoot() }
            .graphicsLayer {
                val p = progress.value
                if (!canCover) {
                    // No poster to grow from: a plain fade, without an off-screen copy of the page.
                    alpha = (p * 2f).coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                } else {
                    val bounds = windowBounds(originBounds, container.offset, size, p, staged, breathPx)
                    val radius = (if (staged) stagedCornerPx(posterCornerPx, stagedMidCornerPx, screenCornerPx, p) else windowCornerPx(posterCornerPx, screenCornerPx, p))
                        .coerceAtMost(minOf(bounds.width, bounds.height) / 2f)
                    shape = WindowShape(bounds, radius, shapePath)
                    clip = true
                }
            }
            .background(AnsuColors.Background),
    ) {
        if (origin != null && originCover != null) {
            // The banner itself, kept composed for as long as the page lives: it is prepared (artwork out of the
            // cache, logo decoded) while the page is open, so a back press finds it ready to shrink into.
            // It costs nothing while hidden: a layer with no alpha is not drawn.
            BannerCover(
                cover = originCover,
                bounds = origin.bounds,
                boundsAt = { containerSize ->
                    windowBounds(origin.bounds, container.offset, containerSize, progress.value, staged, breathPx)
                },
                alpha = {
                    if (staged) ((0.82f - progress.value) / 0.40f).coerceIn(0f, 1f) else ((0.75f - progress.value) / 0.25f).coerceIn(0f, 1f)
                },
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
                    windowBounds(origin.bounds, container.offset, containerSize, progress.value, staged, breathPx)
                },
                // Opaque while the window is near the poster, gone once the page fills it; played the other
                // way on close. Keeping it opaque a little longer hides the shrinking page instead of
                // leaving a half-transparent picture floating over the background.
                alpha = {
                    if (staged) ((0.82f - progress.value) / 0.40f).coerceIn(0f, 1f) else ((0.6f - progress.value) / 0.35f).coerceIn(0f, 1f)
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // A details page opened from this one (a recommendation): this page is the one under it.
                    val behindAmount = if (backdropBlurSupported && PosterTransition.blurBehindEntryId == entryId) {
                        PosterTransition.blurProgress.coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val behindRadius = PosterTransition.blurRadiusPx(behindAmount, density.density)
                    renderEffect = if (behindRadius > 0.5f) BlurEffect(behindRadius, behindRadius, TileMode.Clamp) else null
                    if (canCover) {
                        val p = progress.value
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        if (staged) {
                            // Only fades, no scaling: the window is the motion. The page starts to show once the window
                            // is a full-width strip and is complete before it is full size, as the card's picture leaves.
                            alpha = ((p - 0.40f) / 0.42f).coerceIn(0f, 1f)
                        } else if (closing) {
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
