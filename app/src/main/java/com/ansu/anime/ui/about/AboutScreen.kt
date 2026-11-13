@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.BuildConfig
import com.ansu.anime.R
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.data.update.RepoStats
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.GlassRowValue
import com.ansu.anime.ui.components.HeavyGlassCard
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DEVELOPER_NAME = "Ansuman Sahu"
private const val DEVELOPER_HANDLE = "Ansu216"
private const val REPO_URL = "https://github.com/Ansu216/Anisu"
private const val TELEGRAM_URL = "https://t.me/anisu_anime_app"
private const val DEVELOPER_URL = "https://github.com/$DEVELOPER_HANDLE"
private const val ISSUES_URL = "$REPO_URL/issues"

/** The people shown in the Contributors row's little avatar stack, in order. */
private val CONTRIBUTOR_HANDLES = listOf("PiBOH", "PrussianBlueStar", "Am1-A", "S4Melophile")

/**
 * About, rebuilt on the vivi-music layout: an identity hero, the developer and the people who help,
 * the one support row that carries the GitHub star count, a Links row that opens a welcome-style
 * panel, and the two download counters. There is no version-code row, no install date and no
 * changelog/credits text — the credits Anisu owes AniList and the sources now live in
 * License & Attribution (`LicensesScreen`), which the links panel opens.
 *
 * Seven quick taps on the logo still unlock the hidden options (Cast and Tablet mode), the same
 * gesture About has always had.
 */
@Composable
fun AboutScreen(container: AppContainer, navController: NavHostController) {
    val onNightly = BuildConfig.RELEASE_CHANNEL == "nightly"
    val context = LocalContext.current
    var stats by remember { mutableStateOf<RepoStats?>(null) }
    var statsLoading by remember { mutableStateOf(true) }
    var nightlyDownloads by remember { mutableStateOf<Long?>(null) }
    var nightlyLoading by remember { mutableStateOf(false) }
    var showLinks by remember { mutableStateOf(false) }
    // Seven quick taps on the logo unlock the hidden options (Cast and Tablet mode), counted within two seconds.
    var logoTaps by remember { mutableStateOf(0) }
    var lastLogoTap by remember { mutableStateOf(0L) }
    val toastHolder = remember { arrayOfNulls<android.widget.Toast>(1) }
    fun showToast(text: String) {
        toastHolder[0]?.cancel()
        toastHolder[0] = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    // Asked once when About opens; GitHubStats keeps the answer for the rest of the app run.
    LaunchedEffect(Unit) {
        stats = container.gitHubStats.load()
        statsLoading = false
    }

    // A stable build never asks the counter: no row, no request.
    LaunchedEffect(Unit) {
        if (!onNightly) return@LaunchedEffect
        nightlyLoading = true
        nightlyDownloads = container.nightlyDownloads.total()
        nightlyLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {},
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
                Text(
                    text = "About",
                    style = MaterialTheme.typography.headlineLarge,
                    color = AnsuColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 10.dp),
                )

                HeroCard(
                    onLogoTap = {
                        val now = android.os.SystemClock.elapsedRealtime()
                        logoTaps = if (now - lastLogoTap > 2_000L) 1 else logoTaps + 1
                        lastLogoTap = now
                        val allUnlocked = container.playerPrefs.castUnlocked.value && container.appearancePrefs.tabletUnlocked.value
                        when {
                            logoTaps >= 7 -> {
                                logoTaps = 0
                                if (allUnlocked) {
                                    showToast("Hidden options are already enabled")
                                } else {
                                    container.playerPrefs.unlockCast()
                                    container.appearancePrefs.unlockTablet()
                                    showToast("Cast and Tablet mode unlocked. Find them in Player and streaming and Appearance")
                                }
                            }
                            logoTaps >= 4 && !allUnlocked -> showToast("${7 - logoTaps} more taps to unlock hidden options")
                        }
                    },
                    onGithub = {
                        container.diagnostics.log(LogCategory.CLICK, "About → repository opened")
                        openUrl(context, REPO_URL)
                    },
                )

                GlassGroupLabel("The developer")
                GlassGroup {
                    DeveloperRow(onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "About → developer profile opened")
                        openUrl(context, DEVELOPER_URL)
                    })
                    ContributorsRow(onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "About → Contributors opened")
                        navController.navigate(Dest.CONTRIBUTORS)
                    })
                }

                GlassGroupLabel("Support")
                GlassGroup {
                    GlassRow(
                        icon = Icons.Filled.Favorite,
                        title = "Support Anisu",
                        description = "Star the repo, join the channel",
                        showDivider = false,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = AnsuColors.Accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                CounterValue(stats?.stars, statsLoading)
                            }
                        },
                        onClick = {
                            container.diagnostics.log(LogCategory.CLICK, "About → repository stars opened")
                            openUrl(context, REPO_URL)
                        },
                    )
                }

                GlassGroupLabel("Links")
                GlassGroup {
                    GlassRow(
                        icon = Icons.Filled.Link,
                        title = "Anisu links",
                        description = "Telegram, repository, licence, issues",
                        showDivider = false,
                        trailing = { LinkChevron() },
                        onClick = {
                            container.diagnostics.log(LogCategory.CLICK, "About → links panel opened")
                            showLinks = true
                        },
                    )
                }

                GlassGroupLabel("The project")
                GlassGroup {
                    GlassRow(
                        icon = Icons.Filled.Download,
                        title = "Downloads",
                        description = "APK downloads from every release",
                        showDivider = false,
                        trailing = { CounterValue(stats?.downloads, statsLoading) },
                        onClick = {
                            container.diagnostics.log(LogCategory.CLICK, "About → downloads opened")
                            openUrl(context, "$REPO_URL/releases")
                        },
                    )
                    if (onNightly) {
                        GlassRow(
                            icon = Icons.Filled.Download,
                            title = "Nightly Downloads",
                            description = "Nightly builds downloaded through the app",
                            trailing = { CounterValue(nightlyDownloads, nightlyLoading) },
                            onClick = {
                                container.diagnostics.log(LogCategory.CLICK, "About → nightly builds opened")
                                openUrl(context, "$REPO_URL/tree/apk-nightly")
                            },
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }

        AnimatedVisibility(visible = showLinks, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
            LinksOverlay(
                stars = stats?.stars,
                onDismiss = { showLinks = false },
                onOpen = { url, label ->
                    container.diagnostics.log(LogCategory.CLICK, "About → $label opened")
                    openUrl(context, url)
                },
                onLicense = {
                    showLinks = false
                    container.diagnostics.log(LogCategory.CLICK, "About → License opened")
                    navController.navigate(Dest.LICENSES)
                },
            )
        }
    }
}

/** The identity card: the logo (tap seven times), the name and the installed build, and the GitHub button. */
@Composable
private fun HeroCard(onLogoTap: () -> Unit, onGithub: () -> Unit) {
    HeavyGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        tintAlpha = 0.4f,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(AnsuColors.Accent.copy(alpha = 0.16f), Color.Transparent),
                    ),
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AnsuColors.Background)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLogoTap() },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = AnsuColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${BuildConfig.VERSION_NAME} • ${if (BuildConfig.RELEASE_CHANNEL == "nightly") "Nightly build" else "Release build"}",
                        color = AnsuColors.TextSecondary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AnsuColors.AccentSoft)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onGithub() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Code, contentDescription = "GitHub repository", tint = AnsuColors.TextPrimary, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

/** The lead developer row: the GitHub avatar on a 7-sided Material cookie, the name, and the badge. */
@Composable
private fun DeveloperRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(Cookie7Shape)
                .background(AnsuColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = AnsuColors.TextTertiary, modifier = Modifier.size(22.dp))
            AsyncImage(
                model = "https://github.com/$DEVELOPER_HANDLE.png?size=200",
                contentDescription = "Developer avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = DEVELOPER_NAME,
                color = AnsuColors.Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            LeadDeveloperBadge()
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = AnsuColors.TextTertiary, modifier = Modifier.size(18.dp))
    }
}

/** The official Material 3 Expressive "Lead developer" badge: a checked pill on a tonal surface. */
@Composable
private fun LeadDeveloperBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AnsuColors.AccentSoft)
            .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(16.dp).clip(CircleShape).background(AnsuColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = AnsuColors.OnAccent, modifier = Modifier.size(11.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text("Lead developer", color = AnsuColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The contributors row, with the people's GitHub avatars stacked on the right like the mockup. */
@Composable
private fun ContributorsRow(onClick: () -> Unit) {
    GlassRow(
        icon = Icons.Filled.Group,
        title = "Contributors",
        description = "Who builds and tests Anisu",
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    CONTRIBUTOR_HANDLES.forEach { handle ->
                        AsyncImage(
                            model = "https://github.com/$handle.png?size=80",
                            contentDescription = handle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(AnsuColors.BackgroundElevated)
                                .border(2.dp, AnsuColors.BackgroundElevated, CircleShape),
                        )
                    }
                }
                Text(
                    text = "›",
                    color = AnsuColors.TextTertiary,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        },
    )
}

/**
 * One of the counters on the page — stars, release downloads, nightly downloads. The number counts up
 * once it arrives, shows `…` while it is on its way and `—` when the source could not be asked, so a
 * failed lookup never reads as a real zero.
 */
@Composable
private fun CounterValue(value: Long?, loading: Boolean) {
    val animated by animateIntAsState(
        targetValue = (value ?: 0L).toInt(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "aboutCounter",
    )
    if (value == null) {
        GlassRowValue(if (loading) "…" else "—")
        return
    }
    val formatter = remember { NumberFormat.getInstance() }
    GlassRowValue(formatter.format(animated), color = AnsuColors.Accent, bold = true)
}

/** The mark that says a row leaves the app or opens a panel. */
@Composable
private fun LinkChevron() {
    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = AnsuColors.TextTertiary)
}

/**
 * The Links row's panel, in the style of [com.ansu.anime.ui.components.WelcomeOverlay]: a dimmed screen,
 * a frosted card with a slowly turning cookie under the logo, then Telegram, Repository, License and
 * Issues. Tapping the dim area or Close dismisses it.
 */
@Composable
private fun LinksOverlay(
    stars: Long?,
    onDismiss: () -> Unit,
    onOpen: (String, String) -> Unit,
    onLicense: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        HeavyGlassCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(32.dp),
            tintAlpha = 0.9f,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to AnsuColors.Accent.copy(alpha = 0.20f),
                            0.65f to Color.Transparent,
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RotatingCookieBadge()
                    Text(
                        text = "Anisu links",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = AnsuColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = "The code, the builds, the licence and the place we hang out.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnsuColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
                    )
                    GlassRow(
                        title = "Telegram",
                        description = TELEGRAM_URL.removePrefix("https://"),
                        showDivider = false,
                        leading = {
                            Image(
                                painter = painterResource(R.drawable.ic_telegram),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                        trailing = { LinkChevron() },
                        onClick = { onOpen(TELEGRAM_URL, "Telegram opened") },
                    )
                    GlassRow(
                        icon = Icons.Filled.Code,
                        title = "Repository",
                        description = REPO_URL.removePrefix("https://"),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = AnsuColors.Accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                CounterValue(stars, loading = false)
                            }
                        },
                        onClick = { onOpen(REPO_URL, "repository opened") },
                    )
                    GlassRow(
                        icon = Icons.Filled.Gavel,
                        title = "License",
                        description = "GNU GPLv3 · read inside the app",
                        trailing = { Text("›", color = AnsuColors.TextTertiary, fontSize = 20.sp) },
                        onClick = onLicense,
                    )
                    GlassRow(
                        icon = Icons.Filled.BugReport,
                        title = "Report an issue",
                        description = ISSUES_URL.removePrefix("https://"),
                        trailing = { LinkChevron() },
                        onClick = { onOpen(ISSUES_URL, "issue tracker opened") },
                    )
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AnsuColors.TextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp),
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** The app logo on a slowly turning 7-sided Material cookie, the panel's equivalent of the welcome badge. */
@Composable
private fun RotatingCookieBadge() {
    val turn by rememberInfiniteTransition(label = "linksShape").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart),
        label = "linksShapeTurn",
    )
    val accent = AnsuColors.Accent
    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 2.dp.toPx()
            rotate(degrees = turn, pivot = center) {
                drawPath(cookie7Path(center.x, center.y, radius), accent.copy(alpha = 0.30f))
            }
        }
        Image(
            painter = painterResource(R.drawable.splash_logo_a),
            contentDescription = "Anisu",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp),
        )
    }
}

/**
 * A closed 7-lobed "cookie" path in pixel space: a circle whose radius swells at seven points, the
 * shape Material 3 calls `Cookie7Sided`. Used to clip the lead developer's avatar.
 */
private fun cookie7Path(cx: Float, cy: Float, radius: Float): Path {
    val steps = 180
    val path = Path()
    for (i in 0 until steps) {
        val angle = (i * 2.0 * PI / steps).toFloat()
        val swell = 1f + 0.10f * cos(7f * angle)
        val x = cx + cos(angle) * radius * swell / 1.10f
        val y = cy + sin(angle) * radius * swell / 1.10f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** A [Shape] that clips to [cookie7Path], so an image or a box can be drawn as the 7-sided cookie. */
private object Cookie7Shape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = size.minDimension / 2f
        return Outline.Generic(cookie7Path(size.width / 2f, size.height / 2f, radius))
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
