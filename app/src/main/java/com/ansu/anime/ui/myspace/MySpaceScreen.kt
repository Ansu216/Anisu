package com.ansu.anime.ui.myspace

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.util.formatLabel
import com.ansu.anime.data.repository.ListStatus
import com.ansu.anime.data.repository.toSAnime
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.AppBottomBar
import com.ansu.anime.ui.components.backdropSource
import com.ansu.anime.ui.components.rememberBackdropState
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.posterTransitionOrigin
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors

private enum class ListTab(val label: String) {
    Liked("Liked"),
    Watching("Watching"),
    Planning("Planning"),
    Completed("Completed"),
    Paused("Paused"),
    Dropped("Dropped"),
    Repeating("Repeating"),
}

@Composable
fun MySpaceScreen(
    container: AppContainer,
    navController: NavHostController,
    onAnimeSelected: (SAnime) -> Unit,
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
    var isLoading by remember { mutableStateOf(false) }
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

    val backdrop = rememberBackdropState()
    Scaffold(
        containerColor = AnsuColors.Background,
        bottomBar = { AppBottomBar(navController, Dest.MY_SPACE, backdrop) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().backdropSource(backdrop).padding(top = padding.calculateTopPadding())) {
            // Top row: settings gear pinned to the right, as in the sketch.
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                FrostedGlassCard(modifier = Modifier.size(44.dp), shape = CircleShape, tintAlpha = 0.5f) {
                    IconButton(onClick = { navController.navigate(Dest.SETTINGS) }, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = AnsuColors.TextPrimary)
                    }
                }
            }

            ProfileHeader(
                name = viewer?.name,
                avatarUrl = viewer?.avatarUrl,
                isLoggedIn = isLoggedIn,
                onConnect = { navController.navigate(Dest.ANILIST_LOGIN) },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "My List",
                    style = MaterialTheme.typography.titleMedium,
                    color = AnsuColors.TextPrimary,
                )
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

@Composable
private fun ProfileHeader(name: String?, avatarUrl: String?, isLoggedIn: Boolean, onConnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
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
    FrostedGlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onDetails), shape = RoundedCornerShape(16.dp), tintAlpha = 0.4f) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = media.posterUrl,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(64.dp)
                    .height(92.dp)
                    .posterTransitionOrigin(media.posterUrl, 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AnsuColors.BackgroundElevated),
            )
            Column(
                modifier = Modifier.padding(start = 14.dp).weight(1f).height(92.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Column {
                Text(
                    text = media.title,
                    color = AnsuColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Year, episode count and format; whatever AniList did not report is simply left out.
                val meta = listOfNotNull(
                    media.year?.toString(),
                    media.episodes?.let { "$it EP" },
                    formatLabel(media.format),
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, color = AnsuColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                }
              }
                WatchProgress(watched = media.progress, total = media.episodes)
            }
        }
    }
}

/** "11/12" with a thin bar under it: how far through the show the user is. Without a known total it is just the count. */
@Composable
private fun WatchProgress(watched: Int, total: Int?) {
    val known = total?.takeIf { it > 0 }
    if (known == null && watched <= 0) return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = if (known != null) "$watched/$known" else "Ep $watched",
            color = AnsuColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (known != null) {
            val fraction = (watched.toFloat() / known).coerceIn(0f, 1f)
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(AnsuColors.AccentSoft)) {
                Box(modifier = Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(AnsuColors.Accent))
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.TopCenter) {
        Text(message, color = AnsuColors.TextSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}
