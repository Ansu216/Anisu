@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.appearance

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.data.prefs.AccentPalette
import com.ansu.anime.data.prefs.AppearancePrefs
import com.ansu.anime.data.prefs.TitleLanguage
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.NavBarSurface
import com.ansu.anime.ui.components.navBarShape
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.roundToInt

/** Preset bars shown as tappable options, left to right with increasing roundness. */
private val presets = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

/** Vivid bands drawn behind the live preview so the bar's translucency can be seen. */
private val previewBackdropColors = listOf(
    Color(0xFFE5484D),
    Color(0xFFF5A524),
    Color(0xFF3DD68C),
    Color(0xFF3E8BFF),
    Color(0xFFA36CFF),
)

@Composable
fun AppearanceScreen(container: AppContainer, navController: NavHostController) {
    val roundness by container.appearancePrefs.navBarRoundness.collectAsStateWithLifecycle()
    val frostiness by container.appearancePrefs.navBarFrostiness.collectAsStateWithLifecycle()
    val blur by container.appearancePrefs.navBarBlur.collectAsStateWithLifecycle()
    val titleLanguage by container.appearancePrefs.titleLanguage.collectAsStateWithLifecycle()
    val accent by container.appearancePrefs.accent.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ===== TITLE SETTINGS BOX =====
            SettingsBox(title = "Title Settings") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Language", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TitleLanguage.entries.forEach { language ->
                            FilterChip(
                                selected = titleLanguage == language,
                                onClick = { container.appearancePrefs.setTitleLanguage(language) },
                                label = { Text(language.label) },
                            )
                        }
                    }
                    Text(
                        text = "Show anime titles as Romaji (Shingeki no Kyojin) or English (Attack on Titan). " +
                            "Titles and posters across the app follow this choice.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnsuColors.TextTertiary,
                    )
                }
            }

            // ===== COLOR PALETTE BOX =====
            SettingsBox(title = "Color Palette") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select Color", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccentPalette.entries.forEach { palette ->
                            val selected = palette == accent
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(palette.color)
                                    .border(if (selected) 3.dp else 1.dp, if (selected) AnsuColors.TextPrimary else AnsuColors.StrokeGlass, CircleShape)
                                    .clickable { container.appearancePrefs.setAccent(palette) },
                            )
                        }
                    }
                    
                    // Sample of what the colour is used for.
                    Text("Preview", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(AnsuColors.Accent).padding(horizontal = 20.dp, vertical = 10.dp),
                        ) { Text("Play Ep. 1", color = AnsuColors.OnAccent, style = MaterialTheme.typography.titleSmall) }
                        Box(
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(AnsuColors.AccentSoft),
                        ) { Box(modifier = Modifier.fillMaxWidth(0.6f).fillMaxHeight().background(AnsuColors.Accent)) }
                    }
                    
                    Text(
                        text = "Colours the white pills, play buttons, selected chips and the player's progress bar. " +
                            "Pick White to go back to the original look.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnsuColors.TextTertiary,
                    )
                }
            }

            // ===== NAVIGATION BAR BOX =====
            SettingsBox(title = "Navigation Bar") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    // ---- Live preview ----
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Live Preview", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextSecondary)
                        // A flat backdrop would hide the effect of the frostiness setting (a
                        // translucent bar over a solid colour looks the same at any opacity),
                        // so the preview sits on colourful bands like real page content.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(AnsuColors.BackgroundElevated)
                                .border(1.dp, AnsuColors.StrokeGlass, MaterialTheme.shapes.large),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                previewBackdropColors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)))),
                                    )
                                }
                            }
                            NavBarSurface(currentRoute = Dest.HOME, roundness = roundness, frostiness = frostiness, blur = blur)
                        }
                    }

                    // ---- Style Presets ----
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Style Presets", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            presets.forEach { value ->
                                val selected = (roundness * 100).roundToInt() == (value * 100).roundToInt()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(navBarShape(value))
                                        .background(if (selected) AnsuColors.Accent else AnsuColors.AccentSoft)
                                        .border(1.dp, AnsuColors.StrokeGlass, navBarShape(value))
                                        .clickable { container.appearancePrefs.setNavBarRoundness(value) },
                                )
                            }
                        }
                    }

                    // ---- Roundness Slider ----
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingSlider(
                            title = "Roundness",
                            value = roundness,
                            onValueChange = { container.appearancePrefs.setNavBarRoundness(it) },
                        )
                    }

                    // ---- Frostiness Slider ----
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingSlider(
                            title = "Frostiness (Opacity)",
                            value = frostiness,
                            onValueChange = { container.appearancePrefs.setNavBarFrostiness(it) },
                        )
                        Text(
                            text = "0% is nearly clear glass; 100% is a fully opaque bar.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AnsuColors.TextTertiary,
                        )
                    }

                    // ---- Blur Slider ----
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingSlider(
                            title = "Blur",
                            value = blur,
                            onValueChange = { container.appearancePrefs.setNavBarBlur(it) },
                        )
                        Text(
                            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                "Softens the glass behind the bar."
                            } else {
                                "Real blur needs Android 12 or newer; this device keeps the frosted look."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = AnsuColors.TextTertiary,
                        )
                    }
                }
            }

            // ===== RESET BUTTON =====
            TextButton(
                onClick = {
                    container.appearancePrefs.setNavBarRoundness(AppearancePrefs.DEFAULT_NAV_ROUNDNESS)
                    container.appearancePrefs.setNavBarFrostiness(AppearancePrefs.DEFAULT_NAV_FROSTINESS)
                    container.appearancePrefs.setNavBarBlur(AppearancePrefs.DEFAULT_NAV_BLUR)
                    container.appearancePrefs.setAccent(AccentPalette.WHITE)
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Reset to default") }
        }
    }
}

/**
 * Reusable settings box with rounded borders and padding.
 * Keeps all related settings organized in a clear, visually separated container.
 */
@Composable
private fun SettingsBox(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AnsuColors.BackgroundElevated)
            .border(1.dp, AnsuColors.StrokeGlass, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AnsuColors.TextPrimary)
        content()
    }
}

@Composable
private fun SettingSlider(title: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = AnsuColors.Accent,
            activeTrackColor = AnsuColors.Accent,
            inactiveTrackColor = AnsuColors.AccentSoft,
        ),
    )
}
