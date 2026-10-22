@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.BuildConfig
import com.ansu.anime.R
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.data.update.UpdateCheckResult
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.GlassRowValue
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors

private const val DEVELOPER_NAME = "Ansuman Sahu"
private const val DEVELOPER_HANDLE = "Ansu216"
private const val REPO_URL = "https://github.com/Ansu216/Anisu"
private const val DEVELOPER_URL = "https://github.com/$DEVELOPER_HANDLE"

/**
 * About: the identity header, then black-glass groups — this build, the developer, the ways out of the
 * app and the credits.
 *
 * The header used to end with **two** pills, `Version <name>` next to `<code> · <channel>`, which read
 * as if the app carried two different versions. There is exactly **one** line naming the installed
 * build now (the "Version" row) and the version code gets its own labelled row underneath, so it is
 * clear that the name and the code are two different things: the name is what the project publishes,
 * the code is the counter Android compares to decide whether a build is newer.
 */
@Composable
fun AboutScreen(container: AppContainer, navController: NavHostController) {
    val state by container.updateManager.state.collectAsStateWithLifecycle()
    val updateAvailable = state.result is UpdateCheckResult.Available
    val context = LocalContext.current

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleLarge,
                        color = AnsuColors.TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            HeroCard()

            GlassGroupLabel("This build")
            GlassGroup {
                GlassRow(
                    icon = Icons.Filled.Info,
                    title = "Version",
                    description = BuildConfig.VERSION_NAME,
                    showDivider = false,
                )
                GlassRow(
                    icon = Icons.Filled.Numbers,
                    title = "Version Code",
                    trailing = { GlassRowValue(BuildConfig.VERSION_CODE.toString()) },
                )
                GlassRow(
                    icon = Icons.Filled.SystemUpdate,
                    title = "Updates",
                    description = if (updateAvailable) "A new build is ready to install" else "Channel and automatic checks",
                    trailing = { if (updateAvailable) NewBuildBadge() },
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "About → Updates opened")
                        navController.navigate(Dest.UPDATES)
                    },
                )
                GlassRow(
                    icon = Icons.Filled.Group,
                    title = "Contributors",
                    description = "Who builds and tests Anisu",
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "About → Contributors opened")
                        navController.navigate(Dest.CONTRIBUTORS)
                    },
                )
            }

            GlassGroupLabel("The developer")
            GlassGroup {
                GlassRow(
                    title = DEVELOPER_NAME,
                    description = "Lead developer · @$DEVELOPER_HANDLE",
                    showDivider = false,
                    leading = { DeveloperAvatar() },
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "About → developer profile opened")
                        openUrl(context, DEVELOPER_URL)
                    },
                )
            }

            GlassGroupLabel("Links")
            GlassGroup {
                GlassRow(
                    icon = Icons.Filled.Code,
                    title = "Repository",
                    description = REPO_URL.removePrefix("https://"),
                    showDivider = false,
                    trailing = { LinkChevron() },
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "About → repository opened")
                        openUrl(context, REPO_URL)
                    },
                )
                GlassRow(
                    icon = Icons.Filled.NewReleases,
                    title = "Releases",
                    description = "$REPO_URL/releases".removePrefix("https://"),
                    trailing = { LinkChevron() },
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "About → releases opened")
                        openUrl(context, "$REPO_URL/releases")
                    },
                )
                GlassRow(
                    icon = Icons.Filled.BugReport,
                    title = "Report An Issue",
                    description = "$REPO_URL/issues".removePrefix("https://"),
                    trailing = { LinkChevron() },
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "About → issue tracker opened")
                        openUrl(context, "$REPO_URL/issues")
                    },
                )
            }

            GlassGroupLabel("Credits")
            Text(
                text = "Anisu aggregates metadata from the public AniList API and plays media through " +
                    "third-party sources and extensions. It is not affiliated with, endorsed by or " +
                    "sponsored by AniList or by any provider you connect.",
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                text = "Anime and manga metadata © AniList. Package ${BuildConfig.APPLICATION_ID}.",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 28.dp),
            )
        }
    }
}

/** The identity header: the logo, the name and the tagline, over a soft accent glow. No version here. */
@Composable
private fun HeroCard() {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tintAlpha = 0.4f,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(AnsuColors.Accent.copy(alpha = 0.22f), AnsuColors.BackgroundElevated),
                    ),
                )
                .padding(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(AnsuColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    // A full-colour raster, so drawn with Image (a tint would paint it a single colour).
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    color = AnsuColors.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                )
            }
        }
    }
}

/** The "a new build is here" pill, shown on the Updates row while one is waiting to be installed. */
@Composable
private fun NewBuildBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AnsuColors.ScoreGreen)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text("NEW", color = AnsuColors.OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** The developer's GitHub picture, so the row carries a face instead of a generic icon on the right. */
@Composable
private fun DeveloperAvatar() {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(AnsuColors.BackgroundElevated),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn under the picture, so the row still has a face while Coil is still fetching it.
        Icon(Icons.Filled.Person, contentDescription = null, tint = AnsuColors.TextTertiary, modifier = Modifier.size(20.dp))
        AsyncImage(
            model = "https://github.com/$DEVELOPER_HANDLE.png?size=200",
            contentDescription = "Developer avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The mark that says a row leaves the app. */
@Composable
private fun LinkChevron() {
    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = AnsuColors.TextTertiary)
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
