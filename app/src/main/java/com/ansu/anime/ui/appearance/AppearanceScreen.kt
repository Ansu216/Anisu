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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.ansu.anime.data.prefs.AppearancePrefs
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
            Text("Navigation bar", style = MaterialTheme.typography.titleMedium)

            // ---- Live preview: the exact same bar composable the app uses ----
            Text("Live preview", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextSecondary)
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
                NavBarSurface(currentRoute = Dest.HOME, roundness = roundness, frostiness = frostiness)
            }

            // ---- Preset bars with increasing roundness ----
            Text("Style", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextSecondary)
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

            // ---- Fine control ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Roundness", style = MaterialTheme.typography.titleSmall)
                Text("${(roundness * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
            }
            Slider(
                value = roundness,
                onValueChange = { container.appearancePrefs.setNavBarRoundness(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = AnsuColors.Accent,
                    activeTrackColor = AnsuColors.Accent,
                    inactiveTrackColor = AnsuColors.AccentSoft,
                ),
            )

            // ---- Frostiness: how opaque/frosted the bar background is ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Frostiness", style = MaterialTheme.typography.titleSmall)
                Text("${(frostiness * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
            }
            Slider(
                value = frostiness,
                onValueChange = { container.appearancePrefs.setNavBarFrostiness(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = AnsuColors.Accent,
                    activeTrackColor = AnsuColors.Accent,
                    inactiveTrackColor = AnsuColors.AccentSoft,
                ),
            )
            TextButton(
                onClick = {
                    container.appearancePrefs.setNavBarRoundness(AppearancePrefs.DEFAULT_NAV_ROUNDNESS)
                    container.appearancePrefs.setNavBarFrostiness(AppearancePrefs.DEFAULT_NAV_FROSTINESS)
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Reset to default") }
        }
    }
}
