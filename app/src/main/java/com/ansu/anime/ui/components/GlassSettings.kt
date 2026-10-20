@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The black-glass pieces the Updates and About screens are built from: a small uppercase label over a
 * frosted card, the rows inside it, and the value/toggle that sits on a row's right-hand side.
 *
 * They follow the same grid as every other settings screen — 16dp screen edge, 16dp inside a row, a
 * 12dp gap between a row's icon and its text, 2dp between its title and its description and a
 * uniform 16dp vertical rhythm — so a row looks the same in Settings, Appearance, Updates and About.
 */

/** Settings text, sized and spaced like a roomy system settings page: a plain title over a lighter grey description. */
internal val SettingsTitleStyle: androidx.compose.ui.text.TextStyle get() = androidx.compose.ui.text.TextStyle(
    fontFamily = com.ansu.anime.ui.theme.AnsuFont.appFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 17.sp,
    lineHeight = 24.sp,
)
internal val SettingsDescriptionStyle: androidx.compose.ui.text.TextStyle get() = androidx.compose.ui.text.TextStyle(
    fontFamily = com.ansu.anime.ui.theme.AnsuFont.appFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.5.sp,
    lineHeight = 20.sp,
)
internal val SettingsLabelStyle: androidx.compose.ui.text.TextStyle get() = androidx.compose.ui.text.TextStyle(
    fontFamily = com.ansu.anime.ui.theme.AnsuFont.appFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 12.5.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.4.sp,
)
internal val SettingsRowVerticalPadding = 18.dp

/** The section heading, sitting outside and above its card, aligned with the card's own 16dp inset. */
@Composable
fun GlassGroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = SettingsLabelStyle,
        color = AnsuColors.TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 24.dp, bottom = 10.dp),
    )
}

/** A black-glass card that holds the rows, with no heading of its own. */
@Composable
fun GlassGroup(
    modifier: Modifier = Modifier,
    tintAlpha: Float = 0.4f,
    content: @Composable () -> Unit,
) {
    FrostedGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tintAlpha = tintAlpha,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/**
 * One settings row: a round accent icon, a title, an optional description and an optional trailing
 * slot. The whole row is tappable when [onClick] is given. [showDivider] is false for the first row of
 * a card, the same way the Settings rows do it.
 *
 * A row that leads with a picture instead of an icon (the developer's avatar, say) passes [leading]:
 * it replaces the icon tile and the row keeps the same 12dp gap, so the two kinds of row line up.
 */
@Composable
fun GlassRow(
    icon: ImageVector? = null,
    title: String,
    description: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = AnsuColors.StrokeGlass)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = SettingsRowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> Box(modifier = Modifier.padding(end = 12.dp)) { leading() }
                icon != null -> Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(AnsuColors.AccentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = AnsuColors.Accent, modifier = Modifier.size(20.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = if (leading != null) 0.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = SettingsTitleStyle, color = AnsuColors.TextPrimary)
                description?.let {
                    Text(it, style = SettingsDescriptionStyle, color = AnsuColors.TextSecondary)
                }
            }
            trailing?.let {
                Box(modifier = Modifier.padding(start = 12.dp)) { it() }
            }
        }
    }
}

/**
 * A row whose trailing slot is a switch; tapping anywhere on the row flips it too, so the target is
 * the whole row and not just the 46dp control.
 */
@Composable
fun GlassToggleRow(
    icon: ImageVector? = null,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    showDivider: Boolean = true,
    /** False greys the switch out and ignores taps on the row (a sub-option of a switch that is off). */
    enabled: Boolean = true,
) {
    GlassRow(
        icon = icon,
        title = title,
        description = description,
        showDivider = showDivider,
        onClick = if (enabled) {
            {
                onCheckedChange(!checked)
            }
        } else {
            null
        },
        trailing = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = {
                    onCheckedChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AnsuColors.OnAccent,
                    checkedTrackColor = AnsuColors.Accent,
                    uncheckedThumbColor = AnsuColors.TextSecondary,
                ),
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    )
}

/**
 * A row that opens a block of content in place, for a compact screen that would otherwise either hide
 * something important or spend half the page on it: the long explanation, or a group of settings that are
 * read far less often than the one row above them. The chevron turns as it opens and the block animates
 * its own height, so nothing above it jumps and the row keeps looking like every other one when closed.
 */
@Composable
fun GlassDisclosureRow(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    description: String? = null,
    showDivider: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = AnsuColors.StrokeGlass)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 16.dp, vertical = SettingsRowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(AnsuColors.AccentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = AnsuColors.Accent, modifier = Modifier.size(20.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = if (icon != null) 12.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = SettingsTitleStyle, color = AnsuColors.TextPrimary)
                description?.let {
                    Text(it, style = SettingsDescriptionStyle, color = AnsuColors.TextSecondary)
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = AnsuColors.TextTertiary,
                modifier = Modifier.size(22.dp).graphicsLayer {
                    rotationZ = if (expanded) 180f else 0f
                },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(durationMillis = 160)) +
                expandVertically(animationSpec = tween(durationMillis = 220)),
            exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                shrinkVertically(animationSpec = tween(durationMillis = 180)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

/** The plain value a row shows on its right ("Nightly", "2026101201", "On"). */
@Composable
fun GlassRowValue(
    text: String,
    color: Color = AnsuColors.TextSecondary,
    bold: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    )
}

/**
 * A row whose body is its own content (chips, swatches, a preview) under the title and description, with the
 * same padding and divider as every other row, so a mixed card still reads as one list.
 */
@Composable
fun GlassContentRow(
    title: String,
    description: String? = null,
    showDivider: Boolean = true,
    trailingText: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = AnsuColors.StrokeGlass)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = SettingsRowVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary, modifier = Modifier.weight(1f))
                    trailingText?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = AnsuColors.TextSecondary) }
                }
                description?.let { Text(it, style = SettingsDescriptionStyle, color = AnsuColors.TextSecondary) }
            }
            content()
        }
    }
}

/** A slider row: title and description on top, the current value on the right, the slider underneath. */
@Composable
fun GlassSliderRow(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    description: String? = null,
    showDivider: Boolean = true,
) {
    GlassContentRow(title = title, description = description, showDivider = showDivider, trailingText = valueText) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = AnsuColors.Accent,
                activeTrackColor = AnsuColors.Accent,
                inactiveTrackColor = AnsuColors.AccentSoft,
            ),
        )
    }
}
