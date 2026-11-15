@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.components

import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
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

/** A dropdown menu in Pixel OS frosted glass. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val strokeColor = Color.White.copy(alpha = GlassTuning.borderAlpha.coerceIn(0f, 0.8f))
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        containerColor = glassPopupFill(),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, strokeColor),
    ) {
        WindowBlurBehind()
        content()
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