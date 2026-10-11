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
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The black-glass pieces the Updates and About screens are built from: a small uppercase label over a
 * frosted card, the rows inside it, and the value/toggle that sits on a row's right-hand side.
 *
 * They follow the same grid as every other settings screen — 16dp screen edge, 16dp inside a row, a
 * 12dp gap between a row's icon and its text, 2dp between its title and its description and a
 * uniform 16dp vertical rhythm — so a row looks the same in Settings, Appearance, Updates and About.
 */

/** The small uppercase label above a card, aligned with the card's own 16dp inset. */
@Composable
fun GlassGroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AnsuColors.TextTertiary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
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
            HorizontalDivider(color = AnsuColors.StrokeGlass)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 16.dp),
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
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
                description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
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
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    showDivider: Boolean = true,
) {
    GlassRow(
        icon = icon,
        title = title,
        description = description,
        showDivider = showDivider,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
            HorizontalDivider(color = AnsuColors.StrokeGlass)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
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
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
                description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
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
