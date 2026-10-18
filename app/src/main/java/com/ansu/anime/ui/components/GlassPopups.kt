@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.components

import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The look of every menu, sheet and dialog: the same translucent glass as the Continue Watching "Remove" card
 * (the same 0.66 tint over the same base colour, a hairline edge), with the screen behind it blurred where the
 * phone can do that. One place, so a popup never ends up as a solid grey slab again.
 */
val GlassPopupFill: Color = AnsuColors.SurfaceGlassBase.copy(alpha = 0.66f)

/**
 * Whether the phone is actually allowed to blur between windows right now. Android 12+ only, and phones switch it
 * off in battery saver, on low-end hardware or in developer options. When it is off a translucent popup would show
 * the page behind it as sharp, overlapping text, so the popups fall back to a nearly solid glass fill instead.
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

/** The popup fill: the translucent glass when the phone can blur behind it, a nearly solid one when it cannot. */
@Composable
fun glassPopupFill(): Color =
    AnsuColors.SurfaceGlassBase.copy(alpha = if (rememberCrossWindowBlurEnabled()) 0.66f else 0.96f)

/**
 * Blurs whatever is behind the window this is composed in (a dialog, a sheet, a menu), the way the
 * Continue Watching card blurs the home screen. The blur has to be requested once the popup's window is attached:
 * this composes before that, and asking earlier found no window parameters, did nothing and never tried again,
 * which is why the sheets showed the page behind them sharp. Needs Android 12 and a phone that allows it.
 */
@Composable
fun WindowBlurBehind(radius: Dp = 24.dp) {
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
        // A Dialog-based popup hands out its window directly; a sheet that owns its window is changed through
        // the window manager.
        val dialogWindow = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        if (dialogWindow != null) {
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            dialogWindow.attributes = dialogWindow.attributes.also { it.blurBehindRadius = radiusPx }
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
 * A hairline along the sheet's top edge and rounded corners only. The sheet used to wear a full `border` with a
 * top-rounded shape, which also strokes the straight sides and the bottom edge: wherever the sheet's surface did not
 * run off the screen (while it slid in, was dragged, or was shorter than the screen) those edges showed as thin white
 * lines in the middle of the page, like a box drawn around it.
 */
private fun Modifier.topEdgeStroke(corner: Dp): Modifier = drawWithContent {
    drawContent()
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
    drawPath(path, AnsuColors.StrokeGlass, style = Stroke(width = 1.dp.toPx()))
}

/** A bottom sheet in frosted glass. Same parameters as the Material one, minus the colours it decides itself. */
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

/** A dropdown menu in frosted glass. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        containerColor = glassPopupFill(),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, AnsuColors.StrokeGlass),
    ) {
        WindowBlurBehind(radius = 20.dp)
        content()
    }
}

/** An alert dialog in frosted glass. */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = {
            WindowBlurBehind()
            text?.invoke()
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = glassPopupFill(),
        tonalElevation = 0.dp,
    )
}
