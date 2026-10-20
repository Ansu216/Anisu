package com.ansu.anime.ui.myspace

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.ansu.anime.anilist.AniListMedia
import com.ansu.anime.R
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.data.repository.ListStatus
import com.ansu.anime.data.repository.toSAnime
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.AppBottomBarReserve
import com.ansu.anime.ui.components.BackdropState
import com.ansu.anime.ui.components.backdropSource
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.PosterOriginHandle
import com.ansu.anime.ui.components.posterTransitionAnchor
import com.ansu.anime.ui.components.posterTransitionTrigger
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors

private enum class ListTab(val label: String) {
    Liked("Favourites"),
    Watching("Watching"),
    Planning("Planning"),
    Dropped("Dropped"),
    Completed("Completed"),
    Paused("Paused"),
    Repeating("Repeating"),
}

@Composable
fun MySpaceScreen(
    container: AppContainer,
    navController: NavHostController,
    onAnimeSelected: (SAnime) -> Unit,
    backdrop: BackdropState,
) {
    val viewer by container.aniListRepository.viewer.collectAsStateWithLifecycle()
    val isLoggedIn by container.aniListRepository.isLoggedIn.collectAsStateWithLifecycle()
    val titleLanguage by container.appearancePrefs.titleLanguage.collectAsStateWithLifecycle()

    val savedTab by container.appearancePrefs.mySpaceTab.collectAsStateWithLifecycle()
    val selected = ListTab.entries.firstOrNull { it.name == savedTab } ?: ListTab.Watching
    var liked by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var watching by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var planning by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var completed by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var paused by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var dropped by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var repeating by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    // Starts true when signed in, so the first frame shows the spinner instead of a flash of "Nothing in ... yet".
    var isLoading by remember { mutableStateOf(isLoggedIn) }
    var isSyncing by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Signed out: the lists are the ones saved on this device, kept live.
    val localLiked by container.localListRepository.favourites.collectAsStateWithLifecycle(initialValue = emptyList())
    val localWatching by remember { container.localListRepository.withStatus(ListStatus.CURRENT) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val localPlanning by remember { container.localListRepository.withStatus(ListStatus.PLANNING) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val localCompleted by remember { container.localListRepository.withStatus(ListStatus.COMPLETED) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val localPaused by remember { container.localListRepository.withStatus(ListStatus.PAUSED) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val localDropped by remember { container.localListRepository.withStatus(ListStatus.DROPPED) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val localRepeating by remember { container.localListRepository.withStatus(ListStatus.REPEATING) }.collectAsStateWithLifecycle(initialValue = emptyList())

    // viewer is loaded asynchronously by the repository, so key on it as well as login state.
    LaunchedEffect(isLoggedIn, viewer?.id, reloadKey, titleLanguage) {
        if (isLoggedIn && viewer != null) {
            isLoading = true
            liked = container.aniListRepository.getLiked()
            watching = container.aniListRepository.getCurrentlyWatching().map { it.media.copy(progress = it.progress) }
            planning = container.aniListRepository.getPlanning().map { it.media.copy(progress = it.progress) }
            completed = container.aniListRepository.getCompleted().map { it.media.copy(progress = it.progress) }
            paused = container.aniListRepository.getPaused().map { it.media.copy(progress = it.progress) }
            dropped = container.aniListRepository.getDropped().map { it.media.copy(progress = it.progress) }
            repeating = container.aniListRepository.getRepeating().map { it.media.copy(progress = it.progress) }
            isLoading = false
        } else {
            liked = emptyList(); watching = emptyList(); planning = emptyList(); completed = emptyList()
            paused = emptyList(); dropped = emptyList(); repeating = emptyList()
            isLoading = false
        }
    }

    // The Liked list comes without list progress, so borrow it from whichever list the show also sits in.
    val progressById = remember(watching, planning, completed, paused, dropped, repeating) {
        (watching + planning + completed + paused + dropped + repeating).associate { it.id to it.progress }
    }
    val shown = when (selected) {
        ListTab.Liked -> if (isLoggedIn) liked.map { it.copy(progress = progressById[it.id] ?: 0) } else localLiked
        ListTab.Watching -> if (isLoggedIn) watching else localWatching
        ListTab.Planning -> if (isLoggedIn) planning else localPlanning
        ListTab.Completed -> if (isLoggedIn) completed else localCompleted
        ListTab.Paused -> if (isLoggedIn) paused else localPaused
        ListTab.Dropped -> if (isLoggedIn) dropped else localDropped
        ListTab.Repeating -> if (isLoggedIn) repeating else localRepeating
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = { AppBottomBarReserve() },
    ) { padding ->
        // The background sits inside the recorded layer: without it the layer is transparent wherever no card is
        // drawn, so the nav bar had nothing opaque to blur on this screen.
        Column(modifier = Modifier.fillMaxSize().backdropSource(backdrop).padding(top = padding.calculateTopPadding())) {
            ProfileBanner(
                name = viewer?.name,
                avatarUrl = viewer?.avatarUrl,
                bannerUrl = viewer?.bannerUrl,
                isLoggedIn = isLoggedIn,
                onConnect = { navController.navigate(Dest.ANILIST_LOGIN) },
            )

            // The two shortcuts from the sketch: the settings (unchanged) and the downloaded episodes.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShortcutBox(icon = Icons.Filled.Settings, label = "Settings", onClick = { navController.navigate(Dest.SETTINGS) })
                ShortcutBox(icon = Icons.Filled.Download, label = "Your downloads", onClick = { navController.navigate(Dest.DOWNLOADS) })
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // "My [AniList logo] List", as in the sketch.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "My", style = MaterialTheme.typography.titleMedium, color = AnsuColors.TextPrimary)
                    Image(
                        painter = painterResource(R.drawable.ic_anilist),
                        contentDescription = "AniList",
                        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)),
                    )
                    Text(text = "List", style = MaterialTheme.typography.titleMedium, color = AnsuColors.TextPrimary)
                }
                // Only meaningful with an account: uploads what is saved on this device to AniList.
                if (isLoggedIn) {
                    SyncButton(
                        syncing = isSyncing,
                        onClick = {
                            if (isSyncing) return@SyncButton
                            isSyncing = true
                            scope.launch {
                                val result = container.librarySyncRepository.syncToAniList()
                                isSyncing = false
                                val message = when {
                                    result == null -> "Sign in to AniList to sync"
                                    result.checked == 0 -> "Nothing saved on this device to sync"
                                    result.failed > 0 -> "Synced ${result.changed} item(s), ${result.failed} failed"
                                    result.changed == 0 -> "Already up to date with AniList"
                                    else -> "Synced ${result.changed} item(s) to AniList"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                if (result != null && result.changed > 0) reloadKey++
                            }
                        },
                    )
                }
            }

            // Scrolls sideways instead of squeezing the chips, so a long label like "Completed" never wraps.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ListTab.entries.forEach { tab ->
                    ListChip(text = tab.label, selected = tab == selected, onClick = { container.appearancePrefs.setMySpaceTab(tab.name) })
                }
            }

            when {
                isLoggedIn && isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(color = AnsuColors.Accent)
                }
                shown.isEmpty() -> CenteredMessage(
                    if (isLoggedIn) "Nothing in ${selected.label} yet."
                    else "Nothing in ${selected.label} yet. Open any anime and use the heart or bookmark button to save it on this device.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        top = 4.dp,
                        end = 20.dp,
                        bottom = 4.dp + padding.calculateBottomPadding(),
                    ),
                ) {
                    items(shown, key = { it.id }) { media ->
                        ListItemRow(media = media, onDetails = { onAnimeSelected(media.toSAnime()) })
                    }
                }
            }
        }
    }
}

/**
 * The profile strip at the top: the AniList banner of the account behind the picture and the name. An account
 * with no banner (or no account) gets a slowly colour-changing gradient instead, so the strip is never empty.
 */
@Composable
private fun ProfileBanner(name: String?, avatarUrl: String?, bannerUrl: String?, isLoggedIn: Boolean, onConnect: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        if (bannerUrl != null) {
            AsyncImage(model = bannerUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            ShiftingGradient(modifier = Modifier.fillMaxSize())
        }
        // Darkens the lower part so the name stays readable on any banner, and melts into the page below.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, AnsuColors.Background.copy(alpha = 0.55f), AnsuColors.Background)),
            ),
        )
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(76.dp).clip(CircleShape).background(AnsuColors.SurfaceGlassBase),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl != null) {
                    AsyncImage(model = avatarUrl, contentDescription = "Profile picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.Person, contentDescription = "Profile", tint = AnsuColors.TextTertiary, modifier = Modifier.size(44.dp))
                }
            }
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = name ?: "Guest",
                    style = MaterialTheme.typography.titleMedium,
                    color = AnsuColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // AniList's public API does not expose the account email, so show connection state instead.
                Text(
                    text = if (isLoggedIn) "AniList connected" else "Saved on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                )
            }
            if (!isLoggedIn) {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.OnAccent),
                    shape = RoundedCornerShape(20.dp),
                ) { Text("Connect", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** A soft two-colour gradient whose hue drifts all the way round the colour wheel every half minute. */
@Composable
private fun ShiftingGradient(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bannerGradient")
    val hue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 30_000, easing = LinearEasing), RepeatMode.Restart),
        label = "bannerHue",
    )
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    Color.hsv(hue % 360f, 0.55f, 0.65f),
                    Color.hsv((hue + 70f) % 360f, 0.60f, 0.55f),
                    Color.hsv((hue + 140f) % 360f, 0.55f, 0.50f),
                ),
            ),
        ),
    )
}

/** A full-width frosted box with an icon, a label and an arrow; a tap opens [label]'s screen. */
@Composable
private fun ShortcutBox(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        tintAlpha = 0.4f,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = AnsuColors.TextPrimary, modifier = Modifier.size(22.dp))
            Text(label, color = AnsuColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AnsuColors.TextTertiary)
        }
    }
}

/** Small frosted pill with a sync icon; shows a spinner while a sync is running. */
@Composable
private fun SyncButton(syncing: Boolean, onClick: () -> Unit) {
    FrostedGlassCard(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), tintAlpha = 0.45f) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (syncing) {
                CircularProgressIndicator(color = AnsuColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Icon(Icons.Filled.Sync, contentDescription = null, tint = AnsuColors.TextPrimary, modifier = Modifier.size(16.dp))
            }
            Text(if (syncing) "Syncing" else "Sync", color = AnsuColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Pill chip: white fill when selected, frosted glass otherwise — same language as the hero buttons. */
@Composable
private fun ListChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    if (selected) {
        Box(
            modifier = Modifier.clip(shape).background(AnsuColors.Accent).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) { Text(text, color = AnsuColors.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
    } else {
        FrostedGlassCard(modifier = Modifier.clickable(onClick = onClick), shape = shape, tintAlpha = 0.45f) {
            Text(
                text,
                color = AnsuColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun ListItemRow(media: AniListMedia, onDetails: () -> Unit) {
    // A tap anywhere on the card opens the page out of its poster, not only a tap on the poster itself.
    val originHandle = remember { PosterOriginHandle() }
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth().posterTransitionTrigger(originHandle, media.posterUrl, 10.dp).clickable(onClick = onDetails),
        shape = RoundedCornerShape(16.dp),
        tintAlpha = 0.4f,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = media.posterUrl,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(70.dp)
                    .height(104.dp)
                    .posterTransitionAnchor(originHandle)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AnsuColors.BackgroundElevated),
            )
            Column(
                modifier = Modifier.padding(start = 14.dp).weight(1f).heightIn(min = 104.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Column {
                Text(
                    text = media.title,
                    color = AnsuColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The release date as AniList reports it (a month and day when known, else just the year).
                val released = releaseDateLabel(media)
                if (released != null) {
                    Text(released, color = AnsuColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                }
              }
                WatchProgress(watched = media.progress, total = media.episodes, airedSoFar = media.airedEpisodes)
            }
        }
    }
}

private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Oct 3, 2025" when AniList knows the day, "Oct 2025" with only a month, "2025" with only a year, else null. */
private fun releaseDateLabel(media: AniListMedia): String? {
    val year = media.year ?: return null
    val month = media.startMonth?.takeIf { it in 1..12 } ?: return year.toString()
    val day = media.startDay?.takeIf { it in 1..31 }
    return if (day != null) "${monthNames[month - 1]} $day, $year" else "${monthNames[month - 1]} $year"
}

/**
 * How far through the show the user is, as in the sketch: a play marker and a thin bar, "26/52" above the end of
 * the bar and the percentage under it.
 *
 * A show still airing with no announced total (One Piece) has no "52": its bar is measured against the episodes
 * aired so far ([airedSoFar]), the label stays "Ep N" and no percentage is shown. When even that is unknown the
 * bar is still drawn, as a dimmed full track, so every started title has one and the card keeps its shape.
 */
@Composable
private fun WatchProgress(watched: Int, total: Int?, airedSoFar: Int?) {
    val known = total?.takeIf { it > 0 }
    if (known == null && watched <= 0) return
    val denominator = known ?: airedSoFar?.takeIf { it > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = if (known != null) "$watched/$known" else "Ep $watched",
            color = AnsuColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AnsuColors.Accent, modifier = Modifier.size(14.dp))
            Box(modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape).background(AnsuColors.AccentSoft)) {
                if (denominator != null) {
                    val fraction = (watched.toFloat() / denominator).coerceIn(0f, 1f)
                    Box(modifier = Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(AnsuColors.Accent))
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(AnsuColors.Accent.copy(alpha = 0.35f)))
                }
            }
        }
        if (known != null) {
            Text(
                text = "${(watched.toFloat() / known * 100f).toInt().coerceIn(0, 100)}%",
                color = AnsuColors.TextTertiary,
                fontSize = 11.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.TopCenter) {
        Text(message, color = AnsuColors.TextSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}
