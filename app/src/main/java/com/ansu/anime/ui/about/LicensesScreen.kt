@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.theme.AnsuColors

/** One service Anisu uses: what it gives the app, the credit line it asks for, and where to read its terms. */
private data class Credit(
    val name: String,
    /** Domain whose icon is shown as the logo (fetched when the screen opens). */
    val domain: String,
    /** Letter(s) and colour of the badge that sits behind the logo and stays visible when offline. */
    val badge: String,
    val badgeColor: Long,
    val usedFor: String,
    val attribution: String,
    val url: String,
)

private val dataCredits = listOf(
    Credit(
        name = "AniList",
        domain = "anilist.co",
        badge = "A", badgeColor = 0xFF02A9FF,
        usedFor = "Sign-in, your lists and favourites, discovery, titles, scores, schedules, comments and discussions.",
        attribution = "Anime data and images belong to AniList and their respective owners. Used through the AniList GraphQL API under its terms of use.",
        url = "https://anilist.co",
    ),
    Credit(
        name = "MyAnimeList · Jikan",
        domain = "jikan.moe",
        badge = "J", badgeColor = 0xFF2E51A2,
        usedFor = "English titles, promo videos and trailers, and the MyAnimeList ids that other services key on.",
        attribution = "Data © MyAnimeList, served by Jikan, an unofficial open-source (MIT licensed) MyAnimeList API.",
        url = "https://jikan.moe",
    ),
    Credit(
        name = "TMDB",
        domain = "themoviedb.org",
        badge = "TM", badgeColor = 0xFF01B4E4,
        usedFor = "Posters and artwork for shows and movies.",
        attribution = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
        url = "https://www.themoviedb.org",
    ),
    Credit(
        name = "ani.zip",
        domain = "ani.zip",
        badge = "Z", badgeColor = 0xFF7C4DFF,
        usedFor = "Episode titles, thumbnails, summaries and clear-logo artwork, matched to AniList ids.",
        attribution = "Community-run mapping service. Episode data and images belong to their original owners.",
        url = "https://ani.zip",
    ),
    Credit(
        name = "Kitsu",
        domain = "kitsu.app",
        badge = "K", badgeColor = 0xFFF75239,
        usedFor = "Id mappings that help find the right artwork for a show.",
        attribution = "Mapping data from the Kitsu API. Kitsu and its logo belong to Kitsu Inc.",
        url = "https://kitsu.app",
    ),
    Credit(
        name = "AniSkip",
        domain = "aniskip.com",
        badge = "S", badgeColor = 0xFFE53935,
        usedFor = "Skip intro and skip outro buttons in the player.",
        attribution = "Intro and outro timestamps are community-contributed to AniSkip and shared freely.",
        url = "https://aniskip.com",
    ),
    Credit(
        name = "Anime News Network",
        domain = "animenewsnetwork.com",
        badge = "N", badgeColor = 0xFF1B5E8C,
        usedFor = "Headlines in the News feed.",
        attribution = "Headlines and summaries belong to Anime News Network and link back to the original articles.",
        url = "https://www.animenewsnetwork.com",
    ),
)

private val softwareCredits = listOf(
    Credit(
        name = "Aniyomi",
        domain = "aniyomi.org",
        badge = "AY", badgeColor = 0xFF3F51B5,
        usedFor = "Anisu's extension system loads Aniyomi-compatible extensions.",
        attribution = "Aniyomi is open source under the Apache License 2.0. Extensions are made by their own authors, and Anisu does not host any video.",
        url = "https://aniyomi.org",
    ),
    Credit(
        name = "Open-source libraries",
        domain = "github.com",
        badge = "{ }", badgeColor = 0xFF424242,
        usedFor = "Jetpack Compose, Media3 (ExoPlayer), OkHttp, Coil, Kotlin and kotlinx.serialization.",
        attribution = "Each library is open source under its own licence (mostly Apache 2.0) and belongs to its authors.",
        url = "https://github.com/Ansu216/Anisu",
    ),
)

/**
 * License and attribution: a credit card for every service the app reads from, with its logo, what Anisu uses it for
 * and the credit line it asks for. Logos are the services' own icons, fetched when the screen opens; a coloured badge
 * sits behind each one so the list still looks right offline.
 */
@Composable
fun LicensesScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Licenses",
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
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Anisu is made possible by these free services and open-source projects. Thank you to everyone who builds and maintains them.",
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )

            Spacer(Modifier.height(8.dp))
            GlassGroupLabel("Data and services")
            dataCredits.forEach { CreditCard(it, context) }

            GlassGroupLabel("Software")
            softwareCredits.forEach { CreditCard(it, context) }

            Text(
                text = "Anisu is an independent project and is not affiliated with, endorsed by or sponsored by any of the services above. " +
                    "Names, logos and trademarks belong to their respective owners and are used here only to give credit.",
                style = MaterialTheme.typography.bodySmall,
                color = AnsuColors.TextTertiary,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun CreditCard(credit: Credit, context: Context) {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(20.dp),
        tintAlpha = 0.5f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openUrl(context, credit.url) }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(credit)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = credit.name,
                        color = AnsuColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = credit.domain,
                        color = AnsuColors.TextTertiary,
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open ${credit.name}",
                    tint = AnsuColors.TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = credit.usedFor,
                color = AnsuColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = credit.attribution,
                color = AnsuColors.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** The service's icon over a coloured letter badge: the badge shows while the icon loads or when offline. */
@Composable
private fun Logo(credit: Credit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(credit.badgeColor)),
        contentAlignment = Alignment.Center,
    ) {
        Text(credit.badge, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        AsyncImage(
            model = "https://www.google.com/s2/favicons?domain=${credit.domain}&sz=128",
            contentDescription = "${credit.name} logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize().background(Color.White.copy(alpha = 0f)),
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
