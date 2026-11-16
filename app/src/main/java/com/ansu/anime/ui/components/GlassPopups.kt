@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.components

import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The look of every menu, sheet and dialog, driven live by [GlassTuning.popupTintAlpha].
 */
val GlassPopupFill: Color
    get() = AnsuColors.SurfaceGlassBase.copy(alpha = GlassTuning.popupTintAlpha)

/**
 * Whether the phone is actually allowed to blur between windows right now. Android 12+ only, and phones switch it
 * off in battery saver, on low-end hardware or in developer options.
 */
@Composable
fun rememberCrossWindowBlurEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val context = LocalContext.current
    val windowManager = remember(context) { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    var enabled by remember(windowManager) { mutableStateOf(windowManager.isCrossWindowBlurEnabled) }
    DisposableEffect(windowManager) {
        val listener = java.util.function.Consumer<Boolean> { enabled = it }
        windowManager.addCrossWindowBlurEnabledListener(listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}

/**
 * The popup fill: uses [GlassTuning.popupTintAlpha] when the phone can blur behind it,
 * and falls back to 0.96f nearly solid when cross-window blur is disabled (e.g. Battery Saver).
 */
@Composable
fun glassPopupFill(): Color {
    val windowBlur = rememberCrossWindowBlurEnabled() && GlassTuning.windowBlurDp > 0.5f
    return AnsuColors.SurfaceGlassBase.copy(alpha = if (windowBlur) GlassTuning.popupTintAlpha else 0.96f)
}

/**
 * Blurs whatever is behind the window this is composed in (a dialog, a sheet, a menu),
 * reacting live to [GlassTuning.windowBlurDp].
 */
@Composable
fun WindowBlurBehind(radius: Dp = GlassTuning.windowBlurDp.dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    DisposableEffect(view, radiusPx) {
        val apply = Runnable { applyBlurBehind(view, radiusPx) }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { v.post(apply) }
            override fun onViewDetachedFromWindow(v: View) {}
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) view.post(apply)
        onDispose {
            view.removeOnAttachStateChangeListener(listener)
            view.removeCallbacks(apply)
        }
    }
}

private fun applyBlurBehind(view: View, radiusPx: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        val surfaceBlurPx = (radiusPx * 1.15f).toInt()
        val dialogWindow = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        if (dialogWindow != null) {
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            dialogWindow.attributes = dialogWindow.attributes.also { it.blurBehindRadius = radiusPx }
            dialogWindow.setBackgroundBlurRadius(surfaceBlurPx)
            return
        }
        val root = view.rootView
        if (!root.isAttachedToWindow) return
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        params.blurBehindRadius = radiusPx
        (root.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(root, params)
    }
}

/**
 * A hairline along the sheet's top edge and rounded corners only, driven by [GlassTuning.borderAlpha].
 */
private fun Modifier.topEdgeStroke(corner: Dp): Modifier = drawWithContent {
    drawContent()
    val strokeAlpha = GlassTuning.borderAlpha.coerceIn(0f, 0.8f)
    val r = corner.toPx()
    val half = 0.5.dp.toPx()
    val w = size.width
    val path = Path().apply {
        moveTo(half, r * 2f)
        lineTo(half, r)
        arcTo(Rect(half, half, half + 2 * (r - half), half + 2 * (r - half)), 180f, 90f, false)
        lineTo(w - r, half)
        arcTo(Rect(w - half - 2 * (r - half), half, w - half, half + 2 * (r - half)), 270f, 90f, false)
        lineTo(w - half, r * 2f)
    }
    drawPath(path, Color.White.copy(alpha = strokeAlpha), style = Stroke(width = 1.dp.toPx()))
}

/** A bottom sheet in Pixel OS frosted glass. */
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = glassPopupFill(),
    contentColor: Color = AnsuColors.TextPrimary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.topEdgeStroke(28.dp),
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.40f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) },
    ) {
        WindowBlurBehind()
        content()
    }
}

/**
 * A dropdown list in frosted glass that grows out of the thing that opened it, as in the reference recording:
 * it starts small and blurred at the anchor's corner, scales up on a soft spring while the blur clears and the
 * rows fade in, and closing runs the same thing backwards a little faster (shrinking back into the anchor,
 * blurring and fading out). Same call as the Material menu, so every caller keeps working.
 *
 * It is a plain popup rather than Material's DropdownMenu, whose own open/close animation cannot be replaced.
 * The window-level blur is not used here: a fixed blurred rectangle behind a menu that is still scaling
 * would show as a box, so the fill is firmer instead.
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    // Stays composed while it plays the closing animation, then leaves.
    if (!state.currentState && !state.targetState) return

    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    var origin by remember { mutableStateOf(TransformOrigin(1f, 0f)) }
    val positioner = remember(marginPx) { GlassMenuPositionProvider(marginPx) { origin = it } }
    val shape = RoundedCornerShape(22.dp)
    val strokeColor = Color.White.copy(alpha = GlassTuning.borderAlpha.coerceIn(0f, 0.8f))
    val fill = glassPopupFill().let { it.copy(alpha = it.alpha.coerceAtLeast(0.92f)) }

    Popup(
        popupPositionProvider = positioner,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        val transition = updateTransition(state, label = "glassMenu")
        val progress by transition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    spring(dampingRatio = 0.80f, stiffness = 420f)
                } else {
                    tween(durationMillis = 190, easing = FastOutLinearInEasing)
                }
            },
            label = "glassMenuProgress",
        ) { open -> if (open) 1f else 0f }

        Box(
            modifier = modifier
                .graphicsLayer {
                    val p = progress
                    val s = 0.35f + 0.65f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = origin
                    alpha = (p * 1.8f).coerceIn(0f, 1f)
                    val blurPx = (1f - p).coerceIn(0f, 1f) * 14.dp.toPx()
                    renderEffect = if (blurPx > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BlurEffect(blurPx, blurPx, TileMode.Decal)
                    } else {
                        null
                    }
                }
                .clip(shape)
                .background(fill)
                .border(1.dp, strokeColor, shape),
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .defaultMinSize(minWidth = 112.dp)
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

/**
 * Puts the list under its anchor with its right edge on the anchor's (where the value of a settings row sits),
 * flips it above when there is no room below, keeps it inside the window, and tells the menu which corner it
 * grows out of.
 */
private class GlassMenuPositionProvider(
    private val marginPx: Int,
    private val onOrigin: (TransformOrigin) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val endAligned = anchorBounds.right - popupContentSize.width
        val x = (if (endAligned >= marginPx) endAligned else anchorBounds.left).coerceIn(marginPx, maxX)

        val roomBelow = windowSize.height - marginPx - anchorBounds.bottom
        val opensDown = popupContentSize.height <= roomBelow || anchorBounds.top < roomBelow
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        val y = (if (opensDown) anchorBounds.bottom else anchorBounds.top - popupContentSize.height).coerceIn(marginPx, maxY)

        val pivotX = ((anchorBounds.center.x - x).toFloat() / popupContentSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        onOrigin(TransformOrigin(pivotX, if (opensDown) 0f else 1f))
        return IntOffset(x, y)
    }
}

/** An alert dialog in Pixel OS frosted glass. */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val strokeColor = Color.White.copy(alpha = GlassTuning.borderAlpha.coerceIn(0f, 0.8f))
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(BorderStroke(1.dp, strokeColor), shape),
        dismissButton = dismissButton,
        title = title,
        text = {
            WindowBlurBehind()
            text?.invoke()
        },
        shape = shape,
        containerColor = glassPopupFill(),
        tonalElevation = 0.dp,
    )
}