@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.ansu.anime.ui.components.FadingTopAppBar
import com.ansu.anime.ui.components.withoutTop
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.BuildConfig
import com.ansu.anime.data.update.RepoCommit
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.GlassRowValue
import com.ansu.anime.ui.theme.AnsuColors

private const val REPO_COMMITS_URL = "https://github.com/Ansu216/Anisu/commits/main"

/**
 * The Commits subscreen, rebuilt on vivi-music's `CommitScreen`: the repository's newest commits,
 * read from the public GitHub API, newest first, each with its subject, its author and short SHA,
 * opening the commit on GitHub when tapped. The build this APK came from stays at the top, so the
 * page still answers "what am I running?" before it answers "what changed?".
 *
 * vivi draws the list flat; here the same list is a [GlassGroup] of [GlassRow]s, so it follows
 * Anisu's black glass instead of Material surfaces.
 */
@Composable
fun CommitsScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val buildCommit = BuildConfig.BUILD_COMMIT.ifBlank { "local build" }
    var commits by remember { mutableStateOf<List<RepoCommit>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshes by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshes) {
        loading = true
        error = null
        container.gitHubCommits.load(force = refreshes > 0)
            .onSuccess {
                commits = it
                loading = false
            }
            .onFailure {
                error = it.message ?: "GitHub did not answer"
                loading = false
            }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FadingTopAppBar(
                title = { Text("Commits") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshes++ },
                        enabled = !loading,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh commits")
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
            Spacer(Modifier.height(8.dp))
            GlassGroupLabel("This build")
            GlassGroup {
                GlassRow(
                    icon = Icons.Filled.Code,
                    title = "Current build",
                    description = "The commit this APK was made from",
                    showDivider = false,
                    // A local build has no SHA; show "local" instead of cutting "local build" to "local b".
                    trailing = {
                        GlassRowValue(if (buildCommit == "local build") "local" else buildCommit.take(7))
                    },
                )
            }

            GlassGroupLabel("Recent commits")
            when {
                loading -> GlassGroup {
                    GlassRow(
                        icon = Icons.Filled.Commit,
                        title = "Loading commits…",
                        description = "Reading github.com/Ansu216/Anisu",
                        showDivider = false,
                        trailing = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AnsuColors.Accent,
                                strokeWidth = 2.dp,
                            )
                        },
                    )
                }

                error != null -> GlassGroup {
                    GlassRow(
                        icon = Icons.Filled.Commit,
                        title = "Commits did not load",
                        description = error,
                        showDivider = false,
                        trailing = {
                            TextButton(onClick = { refreshes++ }) {
                                Text("Retry", color = AnsuColors.Accent, fontWeight = FontWeight.Bold)
                            }
                        },
                    )
                }

                commits.isEmpty() -> GlassGroup {
                    GlassRow(
                        icon = Icons.Filled.Commit,
                        title = "No commits yet",
                        description = "The repository answered with an empty list",
                        showDivider = false,
                    )
                }

                else -> GlassGroup {
                    commits.forEachIndexed { index, commit ->
                        GlassRow(
                            icon = Icons.Filled.Commit,
                            title = commit.message,
                            description = listOfNotNull(
                                commit.authorName.takeIf { it.isNotBlank() },
                                commit.date.takeIf { it.isNotBlank() },
                                commit.shortSha,
                            ).joinToString(" · "),
                            showDivider = index > 0,
                            trailing = { CommitAvatar(commit) },
                            onClick = { openCommit(context, commit.htmlUrl) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Live from github.com/Ansu216/Anisu, newest first.",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** The author's GitHub avatar, or the first letter of the name when the commit has no account. */
@Composable
private fun CommitAvatar(commit: RepoCommit) {
    val avatar = commit.avatarUrl
    if (avatar != null) {
        AsyncImage(
            model = avatar,
            contentDescription = commit.authorName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AnsuColors.BackgroundElevated),
        )
    } else {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AnsuColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = commit.authorName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AnsuColors.Accent,
            )
        }
    }
}

private fun openCommit(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
