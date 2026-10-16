@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.appearance

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.ansu.anime.ui.components.backdropBlurSupported
import com.ansu.anime.ui.components.backdropSource
import com.ansu.anime.ui.components.rememberBackdropState
import com.ansu.anime.ui.components.navBarShape
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.components.GlassContentRow
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.GlassSliderRow
import com.ansu.anime.ui.components.GlassToggleRow
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
    val backdropBlur by container.appearancePrefs.navBarBackdropBlur.collectAsStateWithLifecycle()
    val previewBackdrop = rememberBackdropState()
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            GlassGroupLabel("Title")
            GlassGroup {
                GlassContentRow(
                    title = "Language",
                    description = "Show anime titles as Romaji (Shingeki no Kyojin) or English (Attack on Titan). " +
                        "Titles and posters across the app follow this choice.",
                    showDivider = false,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TitleLanguage.entries.forEach { language ->
                            FilterChip(
                                selected = titleLanguage == language,
                                onClick = { container.appearancePrefs.setTitleLanguage(language) },
                                label = { Text(language.label) },
                            )
                        }
                    }
                }
            }

            GlassGroupLabel("Color palette")
            GlassGroup {
                GlassContentRow(
                    title = "Accent colour",
                    description = "Colours the white pills, play buttons, selected chips and the player's progress bar. " +
                        "Pick White to go back to the original look.",
                    showDivider = false,
                ) {
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
                }
                GlassContentRow(title = "Preview", description = "A sample of what the colour is used for") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(AnsuColors.Accent).padding(horizontal = 20.dp, vertical = 10.dp),
                        ) { Text("Play Ep. 1", color = AnsuColors.OnAccent, style = MaterialTheme.typography.titleSmall) }
                        Box(
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(AnsuColors.AccentSoft),
                        ) { Box(modifier = Modifier.fillMaxWidth(0.6f).fillMaxHeight().background(AnsuColors.Accent)) }
                    }
                }
            }

            GlassGroupLabel("Navigation bar")
            GlassGroup {
                GlassContentRow(
                    title = "Live preview",
                    description = "The bar over colourful bands, so the effect of each setting shows",
                    showDivider = false,
                ) {
                    // A flat backdrop would hide the effect of the frostiness setting (a translucent bar over a
                    // solid colour looks the same at any opacity), so the preview sits on colourful bands.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(AnsuColors.BackgroundElevated)
                            .border(1.dp, AnsuColors.StrokeGlass, MaterialTheme.shapes.large),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(modifier = Modifier.fillMaxSize().backdropSource(previewBackdrop)) {
                            previewBackdropColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)))),
                                )
                            }
                        }
                        NavBarSurface(
                            currentRoute = Dest.HOME,
                            roundness = roundness,
                            frostiness = frostiness,
                            backdropBlur = backdropBlur,
                            backdrop = previewBackdrop,
                        )
                    }
                }
                GlassContentRow(title = "Style presets", description = "Pick a ready-made corner shape") {
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
                GlassSliderRow(
                    title = "Roundness",
                    description = "From square corners to a full pill",
                    valueText = "${(roundness * 100).roundToInt()}%",
                    value = roundness,
                    onValueChange = { container.appearancePrefs.setNavBarRoundness(it) },
                )
                GlassSliderRow(
                    title = "Frostiness (opacity)",
                    description = "0% is nearly clear glass; 100% is a fully opaque bar.",
                    valueText = "${(frostiness * 100).roundToInt()}%",
                    value = frostiness,
                    onValueChange = { container.appearancePrefs.setNavBarFrostiness(it) },
                )
                GlassToggleRow(
                    title = "Frosted blur",
                    description = if (backdropBlurSupported) {
                        "Blurs whatever scrolls behind the bar, like frosted glass."
                    } else {
                        "Real blur needs Android 12 or newer; this device keeps the plain frosted look."
                    },
                    checked = backdropBlur,
                    onCheckedChange = { container.appearancePrefs.setNavBarBackdropBlur(it) },
                )
            }

            GlassGroupLabel("Reset")
            GlassGroup {
                GlassRow(
                    title = "Reset to default",
                    description = "Navigation bar look and the accent colour go back to the original",
                    showDivider = false,
                    onClick = {
                        container.appearancePrefs.setNavBarRoundness(AppearancePrefs.DEFAULT_NAV_ROUNDNESS)
                        container.appearancePrefs.setNavBarFrostiness(AppearancePrefs.DEFAULT_NAV_FROSTINESS)
                        container.appearancePrefs.setNavBarBackdropBlur(AppearancePrefs.DEFAULT_NAV_BACKDROP_BLUR)
                        container.appearancePrefs.setAccent(AccentPalette.WHITE)
                    },
                )
            }
        }
    }
}
