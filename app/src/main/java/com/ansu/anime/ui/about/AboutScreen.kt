@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.BuildConfig
import com.ansu.anime.R
import com.ansu.anime.data.update.UpdateCheckResult
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors

private const val DEVELOPER_NAME = "Ansuman Sahu"
private const val DEVELOPER_HANDLE = "Ansu216"
private const val REPO_URL = "https://github.com/Ansu216/Anisu"

/**
 * About: who Ansu is, what build is installed, and the ways out of the app — the updater, the people
 * behind it, the repository and the issue tracker. Rebuilt in the app's dark glass language, split from
 * the updater so each screen does one thing well.
 */
@Composable
fun AboutScreen(container: AppContainer, navController: NavHostController) {
    val state by container.updateManager.state.collectAsStateWithLifecycle()
    val updateAvailable = state.result is UpdateCheckResult.Available
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IdentityCard()

            ActionRow(
                icon = Icons.Filled.SystemUpdate,
                title = "Updates",
                subtitle = if (updateAvailable) "A new build is ready to install" else "Channel, automatic checks and install",
                badge = updateAvailable,
                onClick = {
                    container.diagnostics.log(
                        com.ansu.anime.core.diagnostics.LogCategory.CLICK,
                        "About → Updates opened",
                    )
                    navController.navigate(Dest.UPDATES)
                },
            )
            ActionRow(
                icon = Icons.Filled.Group,
                title = "Contributors",
                subtitle = "The people who build and test Anisu",
                onClick = {
                    container.diagnostics.log(
                        com.ansu.anime.core.diagnostics.LogCategory.CLICK,
                        "About → Contributors opened",
                    )
                    navController.navigate(Dest.CONTRIBUTORS)
                },
            )

            SectionTitle("Lead developer")
            DeveloperCard()

            SectionTitle("Links")
            LinkRow("GitHub profile", "https://github.com/$DEVELOPER_HANDLE", context)
            LinkRow("Repository", REPO_URL, context)
            LinkRow("Report an issue", "$REPO_URL/issues", context)
            LinkRow("Releases", "$REPO_URL/releases", context)

            SectionTitle("Credits")
            Text(
                text = "Anisu aggregates metadata from the public AniList API and plays media through " +
                    "third-party sources, extensions and Stremio addons. It is not affiliated with, " +
                    "endorsed by or sponsored by AniList or by any provider you connect.",
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
            )
            Text(
                text = "Anime and manga metadata © AniList. Package ${BuildConfig.APPLICATION_ID}.",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun IdentityCard() {
    FrostedGlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tintAlpha = 0.4f) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(AnsuColors.BackgroundElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = AnsuColors.Accent,
                    modifier = Modifier.size(66.dp),
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                color = AnsuColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
            )
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AnsuColors.AccentSoft)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = AnsuColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DeveloperCard() {
    FrostedGlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tintAlpha = 0.4f) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(AnsuColors.BackgroundElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = AnsuColors.TextTertiary, modifier = Modifier.size(30.dp))
                AsyncImage(
                    model = "https://github.com/$DEVELOPER_HANDLE.png?size=200",
                    contentDescription = "Developer avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(DEVELOPER_NAME, style = MaterialTheme.typography.titleMedium, color = AnsuColors.TextPrimary)
                Text(
                    text = "Lead developer · @$DEVELOPER_HANDLE",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, badge: Boolean = false, onClick: () -> Unit) {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        tintAlpha = 0.4f,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AnsuColors.TextPrimary)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
            }
            if (badge) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AnsuColors.ScoreGreen)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("NEW", color = AnsuColors.Background, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AnsuColors.TextTertiary)
        }
    }
}

@Composable
private fun LinkRow(title: String, url: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, url) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
            Text(
                text = url.removePrefix("https://"),
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = AnsuColors.TextTertiary)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = AnsuColors.TextPrimary)
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
