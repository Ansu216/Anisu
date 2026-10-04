@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.contributors

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.data.contributors.Contributor
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The people behind Ansu, read live from `contributorsansu.json` at the root of the project's
 * repository (with a bundled fallback). Adapted to a phone-width card list rather than a desktop
 * grid: one card per person, avatar on the left, name/handle and what they did on the right.
 */
@Composable
fun ContributorsScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    var contributors by remember { mutableStateOf<List<Contributor>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        contributors = container.contributorsRepository.load(force = reload > 0)
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contributors") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { reload++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "Anisu exists thanks to these people. The list is read live from the project " +
                        "repository, so it always reflects who is helping right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            if (loading && contributors.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AnsuColors.Accent)
                    }
                }
            }

            items(contributors, key = { it.username }) { contributor ->
                ContributorCard(
                    contributor = contributor,
                    onClick = {
                        container.diagnostics.log(
                            com.ansu.anime.core.diagnostics.LogCategory.CLICK,
                            "Contributor opened: @${contributor.username}",
                        )
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contributor.profileUrl))) }
                    },
                )
            }

            if (!loading && contributors.isEmpty()) {
                item {
                    Text(
                        text = "Couldn't load the contributors right now. Pull the refresh button to try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnsuColors.TextSecondary,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorCard(contributor: Contributor, onClick: () -> Unit) {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        tintAlpha = 0.4f,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(AnsuColors.BackgroundElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = AnsuColors.TextTertiary,
                    modifier = Modifier.size(34.dp),
                )
                AsyncImage(
                    model = contributor.avatarUrl,
                    contentDescription = contributor.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    text = contributor.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = AnsuColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${contributor.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AnsuColors.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                contributor.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnsuColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
