package com.ansu.anime.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Remembers where the tapped poster was on screen, so the details page can grow out of it when it
 * opens and shrink back into it when the user goes back (the same motion as an app opening from, and
 * closing into, its launcher icon).
 *
 * A poster marks itself with [posterTransitionOrigin]; on touch-down that writes its bounds here as the
 * "pending" origin. The details destination claims the pending origin once, when it first composes,
 * and keeps it per back-stack entry so a back press always closes into the poster it opened from.
 */
object PosterTransition {
    /** Length of the open and close animation. */
    const val DURATION_MILLIS = 380

    /** Accelerate-then-settle curve (Material "emphasized"), close to the launcher's open motion. */
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** A tap's touch-down and the navigation it triggers are far closer than this; older origins are stale. */
    private const val PENDING_TTL_MILLIS = 1500L

    private var pending: Rect? = null
    private var pendingAt = 0L
    private val claimed = HashMap<String, Rect?>()

    /** True when [entryId] has been opened before, i.e. this is a return to it (from the player, say), not a first open. */
    fun hasClaimed(entryId: String): Boolean = claimed.containsKey(entryId)

    fun setPending(bounds: Rect) {
        pending = bounds
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
    fun originFor(entryId: String): Rect? {
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
 * Place it on the poster image itself. It only observes the touch-down without consuming it, so
 * clicks, scrolling and long-presses behave exactly as before.
 */
fun Modifier.posterTransitionOrigin(): Modifier = composed {
    val holder = remember { CoordinatesHolder() }
    this
        .onGloballyPositioned { holder.coordinates = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                holder.coordinates?.takeIf { it.isAttached }?.let { PosterTransition.setPending(it.boundsInRoot()) }
            }
        }
}

/** A plain (non-state) holder: layout coordinates change every frame while scrolling and must not recompose. */
private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Hosts a details destination so it opens out of the tapped poster and closes back into it.
 *
 * The page is drawn at the poster's size and position and eased up to full screen (with the window
 * corners rounding off and the content fading in), and played in reverse when the destination leaves
 * through a back press. The progress is driven by the destination's own enter/exit transition, so
 * the NavHost keeps the page on screen until the close animation has finished, and a predictive back
 * gesture scrubs it. A destination that was not opened from a poster just cross-fades.
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
    val progress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = PosterTransition.DURATION_MILLIS, easing = PosterTransition.Easing) },
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
    val windowCornerPx = with(LocalDensity.current) { 28.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress
                if (origin == null || size.width <= 0f || size.height <= 0f) {
                    alpha = (p * 2f).coerceIn(0f, 1f)
                } else {
                    val sx = lerp(origin.width / size.width, 1f, p)
                    val sy = lerp(origin.height / size.height, 1f, p)
                    scaleX = sx
                    scaleY = sy
                    translationX = lerp(origin.center.x - size.width / 2f, 0f, p)
                    translationY = lerp(origin.center.y - size.height / 2f, 0f, p)
                    // The poster's own thumbnail is still visible underneath at the start, so the page
                    // fades in over the first third instead of popping in.
                    alpha = (p * 3f).coerceIn(0f, 1f)
                    // Corner radius is set before the scale is applied, so divide it back out to get
                    // the same on-screen radius on both axes.
                    val onScreenRadius = lerp(windowCornerPx, 0f, p)
                    val radius = onScreenRadius / minOf(sx, sy).coerceAtLeast(0.05f)
                    shape = RoundedCornerShape(radius.coerceAtMost(size.minDimension / 2f))
                    clip = true
                }
            },
    ) {
        content()
    }
}
