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
import androidx.compose.ui.Modifier
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
 * Blurs whatever is behind the window this is composed in (a dialog, a sheet, a menu), the way the
 * Continue Watching card blurs the home screen. Needs Android 12 and a phone that allows blurring between
 * windows; anywhere else nothing happens and the popup keeps its translucent glass fill alone.
 */
@Composable
fun WindowBlurBehind(radius: Dp = 24.dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    DisposableEffect(view, radiusPx) {
        applyBlurBehind(view, radiusPx)
        onDispose { }
    }
}

private fun applyBlurBehind(view: View, radiusPx: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        val root = view.rootView
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        params.blurBehindRadius = radiusPx
        (root.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(root, params)
    }
}

/** A bottom sheet in frosted glass. Same parameters as the Material one, minus the colours it decides itself. */
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = GlassPopupFill,
    contentColor: Color = AnsuColors.TextPrimary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.border(1.dp, AnsuColors.StrokeGlass, shape),
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
        containerColor = GlassPopupFill,
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
        containerColor = GlassPopupFill,
        tonalElevation = 0.dp,
    )
}
