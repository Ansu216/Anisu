@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.ansu.anime.ui.components.FadingTopAppBar
import com.ansu.anime.ui.components.withoutTop
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.data.prefs.SeasonalTheme
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.GlassContentRow
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.components.GlassToggleRow
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The hidden Developer options, reachable from the Developer category that appears in Settings under
 * About only after seven taps on "App version" in Updates. The first row is the way out: turning it
 * off hides the category again and drops any forced seasonal theme, so nothing stays stuck on a season
 * nobody can change. The seasonal picker lives here rather than in About, which keeps the public
 * About page free of test controls.
 */
@Composable
fun DeveloperOptionsScreen(container: AppContainer, navController: NavHostController) {
    val seasonalOverride by container.appearancePrefs.seasonalOverride.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FadingTopAppBar(
                title = { Text("Developer options") },
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
                .padding(padding.withoutTop())
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "These options are hidden. The category and the seasonal logo only exist while " +
                    "Developer options are on.",
                style = MaterialTheme.typography.bodySmall,
                color = AnsuColors.TextSecondary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )

            GlassGroupLabel("Visibility")
            GlassGroup {
                GlassToggleRow(
                    icon = Icons.Filled.Code,
                    title = "Developer options",
                    description = "Show this category and the hidden themes. Turn off to hide them again.",
                    checked = true,
                    showDivider = false,
                    onCheckedChange = { on ->
                        if (!on) {
                            container.appearancePrefs.lockDeveloperOptions()
                            container.diagnostics.log(
                                LogCategory.CLICK,
                                "Developer options → turned off",
                            )
                            navController.popBackStack()
                        }
                    },
                )
            }

            GlassGroupLabel("Seasonal theme")
            GlassGroup {
                GlassContentRow(
                    title = "Theme",
                    description = "Automatic follows the calendar. Force one to test it.",
                    showDivider = false,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SeasonalChip("Auto", seasonalOverride == null) {
                                container.appearancePrefs.setSeasonalOverride(null)
                            }
                            SeasonalTheme.entries.take(2).forEach { theme ->
                                SeasonalChip(theme.label, seasonalOverride == theme) {
                                    container.appearancePrefs.setSeasonalOverride(theme)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SeasonalTheme.entries.drop(2).forEach { theme ->
                                SeasonalChip(theme.label, seasonalOverride == theme) {
                                    container.appearancePrefs.setSeasonalOverride(theme)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Unlocked with seven taps on App version in Updates. Turning Developer options " +
                    "off clears the forced season and hides this page's category from Settings.",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** One choice in the hidden Seasonal theme row. */
@Composable
private fun SeasonalChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        // Chips follow Anisu's black glass, like the ones in Playback.
        colors = FilterChipDefaults.filterChipColors(
            containerColor = AnsuColors.BackgroundElevated,
            labelColor = AnsuColors.TextSecondary,
            selectedContainerColor = AnsuColors.AccentSoft,
            selectedLabelColor = AnsuColors.Accent,
        ),
    )
}
