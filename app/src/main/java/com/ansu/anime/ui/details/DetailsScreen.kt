package com.ansu.anime.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.anilist.AniListMedia
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.util.SynopsisBlock
import com.ansu.anime.core.util.formatEpisodeNumber
import com.ansu.anime.core.util.parseSynopsis
import com.ansu.anime.data.repository.ListStatus
import com.ansu.anime.data.repository.pickHeroImage
import com.ansu.anime.data.prefs.TitleLanguage
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.GenreChip
import com.ansu.anime.ui.components.PersonCard
import com.ansu.anime.ui.components.PosterGlassLabel
import com.ansu.anime.ui.components.StatItem
import com.ansu.anime.ui.components.TitleLogo
import com.ansu.anime.ui.theme.AnsuColors

/**
 * Details page — a JJK-reference-style layout: banner hero with back button,
 * white "Play Now" + frosted circular like button, score/episodes/year/format
 * stats, genre row, expandable synopsis, episode list, cast, crew, related
 * shows. Data comes from [DetailsViewModel]: episodes are resolved from the
 * anime's real extension/addon source, AniList details load in parallel.
 */
@Composable
fun DetailsScreen(
    container: AppContainer,
    navController: NavHostController,
    onEpisodeSelected: (SEpisode) -> Unit,
) {
    val viewModel: DetailsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DetailsViewModel(container.extensionManager, container.addonManager, container.aniListRepository, container.episodeMetadataRepository, container.localListRepository, container.artworkRepository, container.selectionHolder)
            }
        },
    )
    val anime by viewModel.anime.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val titleLanguage by container.appearancePrefs.titleLanguage.collectAsStateWithLifecycle()
    var expandSynopsis by remember { mutableStateOf(false) }
    var selectedPerson by remember { mutableStateOf<PersonDetail?>(null) }
    var selectedGroup by remember(anime?.id) { mutableIntStateOf(0) }
    // Only the first few episodes of a group show until "More episodes" is tapped; reset per show and per group.
    var episodesExpanded by remember(anime?.id, selectedGroup) { mutableStateOf(false) }

    val details = state.aniListDetails
    // Episodes that have not aired yet: shown as compact rows and not playable.
    // The source/extension list can run past what the show really has (a movie returned with 12 "episodes",
    // a 13-episode cour followed by stray numbers), so AniList's own episode count and format trim it.
    val episodes = remember(state.episodes, details?.episodes, details?.format) {
        trimToAniListCount(state.episodes, details?.episodes, details?.format)
    }
    val upcomingIds = remember(episodes, details?.status, details?.nextAiringEpisode) {
        upcomingEpisodeIds(episodes, details?.status, details?.nextAiringEpisode)
    }

    // Where the viewer left off in this title, so the main button continues instead of restarting at Ep. 1.
    val resumeEntries by container.continueWatchingRepository.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val playTarget = remember(resumeEntries, anime?.anilistId, episodes, upcomingIds) {
        val resume = anime?.anilistId?.let { id -> resumeEntries.firstOrNull { it.anilistId == id } }
        resolvePlayTarget(episodes.filter { it.id !in upcomingIds }, resume)
    }

    // Hand the playable episodes to the player (previous/next and its "more episodes" list).
    LaunchedEffect(episodes, upcomingIds) {
        container.selectionHolder.selectEpisodes(episodes.filter { it.id !in upcomingIds })
    }

    // One line per opened title, so an exported report shows what the user was looking at.
    LaunchedEffect(anime?.id) {
        anime?.let { container.diagnostics.log(LogCategory.CLICK, "Details opened: ${it.title}") }
    }

    Box(modifier = Modifier.fillMaxSize().background(AnsuColors.Background)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                // Art of this exact entry: a first season keeps the series backdrop, while later seasons,
                // movies and specials use their own banner/cover so they no longer all look the same.
                // The title comes from AniList's details in the chosen language once they have loaded.
                val shownTitle = details?.title ?: anime?.title.orEmpty()
                DetailsHero(
                    title = shownTitle,
                    imageUrl = pickHeroImage(
                        title = shownTitle,
                        format = details?.format ?: anime?.format,
                        hasPrequel = details?.hasPrequel == true,
                        entryBanner = details?.bannerUrl ?: anime?.bannerUrl,
                        // English: the Western-release poster ani.zip has, if any; Romaji: AniList's own (Japanese key art) cover.
                        poster = when (titleLanguage) {
                            TitleLanguage.ENGLISH -> state.artwork.posterUrl ?: anime?.posterUrl ?: details?.posterUrl
                            TitleLanguage.ROMAJI -> anime?.posterUrl ?: details?.posterUrl
                        },
                        artwork = state.artwork,
                    ),
                    logoUrl = state.artwork.logoUrl,
                    lookupDone = state.artworkLoaded,
                    onBack = { navController.popBackStack() },
                )
            }

            item {
                PlayLikeRow(
                    playLabel = playTarget?.label ?: "No episodes yet",
                    playEnabled = playTarget != null,
                    onPlay = {
                        playTarget?.episode?.let { episode ->
                            container.diagnostics.log(LogCategory.CLICK, "Play pressed: ${anime?.title} E${episode.episodeNumber}")
                            onEpisodeSelected(episode)
                        }
                    },
                    isLiked = state.liked,
                    onToggleLike = {
                        container.diagnostics.log(LogCategory.CLICK, "Favourite toggled: ${anime?.title}")
                        viewModel.toggleFavourite()
                    },
                    // Signed in it edits the AniList list; signed out it edits the on-device list.
                    showListButton = anime?.anilistId != null,
                    listStatus = state.shownListStatus,
                    onSetListStatus = {
                        container.diagnostics.log(LogCategory.CLICK, "List status set to $it: ${anime?.title}")
                        viewModel.setListStatus(it)
                    },
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(Icons.Filled.Star, details?.averageScore?.let { "$it%" } ?: "—", "SCORE", tint = AnsuColors.ScoreGreen)
                    StatItem(Icons.AutoMirrored.Filled.ViewList, (details?.episodes ?: episodes.size.takeIf { it > 0 })?.toString() ?: "—", "EPISODES")
                    StatItem(Icons.Filled.CalendarToday, details?.year?.toString() ?: anime?.releaseYear?.toString() ?: "—", "YEAR")
                    StatItem(Icons.Filled.Tv, details?.format ?: "TV", "FORMAT")
                }
            }

            val genres = (details?.genres?.takeIf { it.isNotEmpty() } ?: anime?.genres).orEmpty()
            if (genres.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(genres) { genre -> GenreChip(genre) }
                    }
                }
            }

            val synopsisBlocks = parseSynopsis(details?.description ?: anime?.description)
            if (synopsisBlocks.isNotEmpty()) {
                item {
                    Synopsis(
                        blocks = synopsisBlocks,
                        expanded = expandSynopsis,
                        onToggle = { expandSynopsis = !expandSynopsis },
                    )
                }
            }

            item { DetailsSectionHeader("Episodes") }
            if (state.isLoading) {
                item { CircularProgressIndicator(color = AnsuColors.Accent, modifier = Modifier.padding(20.dp)) }
            } else if (episodes.isEmpty()) {
                item {
                    Text(
                        state.error ?: "No episodes found yet.",
                        color = AnsuColors.TextTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            } else {
                val groups = episodes.chunked(EPISODE_GROUP_SIZE)
                val groupIndex = selectedGroup.coerceIn(0, groups.lastIndex)
                // Nothing to switch between when none of the episodes can be played yet.
                if (groups.size > 1 && upcomingIds.size < episodes.size) {
                    item {
                        EpisodeGroupChips(
                            groups = groups,
                            selected = groupIndex,
                            onSelect = { selectedGroup = it },
                        )
                    }
                }
                val fullGroup = groups[groupIndex]
                val collapsed = !episodesExpanded && fullGroup.size > EPISODE_PREVIEW_COUNT
                val visible = if (collapsed) fullGroup.take(EPISODE_PREVIEW_COUNT) else fullGroup
                // Upcoming episodes with no announced date carry no information; they collapse into one line.
                val undated = visible.filter { it.id in upcomingIds && it.airDate.isNullOrBlank() }
                itemsIndexed(visible.filterNot { it in undated }, key = { index, episode -> "ep:${episode.id}#$index" }) { _, episode ->
                    if (episode.id in upcomingIds) {
                        UpcomingEpisodeRow(episode)
                    } else {
                        EpisodeRow(
                            episode = episode,
                            synopsis = episode.description,
                            onClick = {
                                container.diagnostics.log(LogCategory.CLICK, "Episode selected: ${anime?.title} E${episode.episodeNumber}")
                                onEpisodeSelected(episode)
                            },
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    }
                }
                if (undated.isNotEmpty()) {
                    item { UndatedUpcomingRow(undated) }
                }
                if (collapsed) {
                    item(key = "more-episodes") {
                        MoreEpisodesChip(
                            remaining = fullGroup.size - EPISODE_PREVIEW_COUNT,
                            onClick = { episodesExpanded = true },
                        )
                    }
                }
            }

            details?.franchise?.takeIf { it.isNotEmpty() }?.let { franchise ->
                item { DetailsSectionHeader("More from this Show") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(franchise, key = { index, relation -> "fr:${relation.media.id}#$index" }) { _, relation ->
                            RelatedPoster(
                                media = relation.media,
                                badge = relation.label,
                                onClick = {
                                    container.selectionHolder.selectAnime(relation.media.toSAnime())
                                    navController.navigate(com.ansu.anime.ui.navigation.Dest.DETAILS)
                                },
                            )
                        }
                    }
                }
            }

            details?.characters?.takeIf { it.isNotEmpty() }?.let { characters ->
                item { DetailsSectionHeader("Characters") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(characters, key = { index, character -> "ch:${character.id}#$index" }) { _, character ->
                            PersonCard(
                                imageUrl = character.imageUrl,
                                name = character.name,
                                role = character.role.lowercase().replaceFirstChar { it.uppercase() },
                                onClick = { selectedPerson = PersonDetail.Character(character) },
                            )
                        }
                    }
                }
            }

            details?.staff?.takeIf { it.isNotEmpty() }?.let { staff ->
                item { DetailsSectionHeader("Staff") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(staff, key = { index, member -> "st:${member.id}#$index" }) { _, member ->
                            PersonCard(
                                imageUrl = member.imageUrl,
                                name = member.name,
                                role = member.role,
                                onClick = { selectedPerson = PersonDetail.Staff(member) },
                            )
                        }
                    }
                }
            }

            details?.related?.takeIf { it.isNotEmpty() }?.let { related ->
                item { DetailsSectionHeader("More Like This") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(related, key = { index, media -> "rl:${media.id}#$index" }) { _, media ->
                            RelatedPoster(
                                media = media,
                                onClick = {
                                    container.selectionHolder.selectAnime(media.toSAnime())
                                    navController.navigate(com.ansu.anime.ui.navigation.Dest.DETAILS)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedPerson?.let { person ->
        CharacterStaffSheet(person = person, onDismiss = { selectedPerson = null })
    }
}

/**
 * Banner hero with back button and the title lettering. The frame is 16:9, so a horizontal
 * banner/backdrop fills it edge to edge without being cropped. The title is the show's logo when
 * one exists and plain text otherwise.
 */
@Composable
private fun DetailsHero(title: String, imageUrl: String?, logoUrl: String?, lookupDone: Boolean, onBack: () -> Unit) {
    // Taller than the art's 16:9 so the fade has room; the banner melts into the page like the home hero.
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(AnsuColors.BackgroundElevated),
        )
        // Long, eased fade into the page background (no hard bottom edge), same colour the home hero ends in.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to Color.Black.copy(alpha = 0.08f),
                    0.6f to AnsuColors.Background.copy(alpha = 0.55f),
                    0.82f to AnsuColors.Background.copy(alpha = 0.92f),
                    1f to AnsuColors.Background,
                ),
            ),
        )
        // Keeps the status-bar icons legible over bright artwork, as on the home hero.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(96.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AnsuColors.TextPrimary)
        }
        TitleLogo(
            title = title,
            logoUrl = logoUrl,
            lookupDone = lookupDone,
            alignment = Alignment.CenterStart,
            textAlign = TextAlign.Start,
            fontSize = 24.sp,
            uppercase = true,
            maxLogoWidth = 320.dp,
            maxLogoHeight = 110.dp,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

/**
 * The synopsis laid out as short paragraphs, labelled lists and a credit line instead of one wall of
 * text. Collapsed it shows the opening paragraph (4 lines); expanded it shows everything with gaps.
 */
@Composable
private fun Synopsis(blocks: List<SynopsisBlock>, expanded: Boolean, onToggle: () -> Unit) {
    val canExpand = blocks.size > 1 || (blocks.first() as? SynopsisBlock.Paragraph)?.text.orEmpty().length > 200
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (!expanded) {
            Text(
                text = when (val first = blocks.first()) {
                    is SynopsisBlock.Paragraph -> first.text
                    is SynopsisBlock.Label -> first.text
                    is SynopsisBlock.Bullet -> first.text
                    is SynopsisBlock.Source -> first.text
                },
                color = AnsuColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            blocks.forEachIndexed { index, block ->
                val previous = blocks.getOrNull(index - 1)
                when (block) {
                    is SynopsisBlock.Paragraph -> {
                        if (previous != null) Spacer(Modifier.height(12.dp))
                        Text(text = block.text, color = AnsuColors.TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                    is SynopsisBlock.Label -> {
                        if (previous != null) Spacer(Modifier.height(18.dp))
                        Text(text = block.text, color = AnsuColors.TextPrimary, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                    }
                    is SynopsisBlock.Bullet -> {
                        Spacer(Modifier.height(if (previous is SynopsisBlock.Bullet) 6.dp else 8.dp))
                        Row {
                            Text(text = "\u2022", color = AnsuColors.TextTertiary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.width(16.dp))
                            Text(text = block.text, color = AnsuColors.TextSecondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    is SynopsisBlock.Source -> {
                        Spacer(Modifier.height(14.dp))
                        Text(text = block.text, color = AnsuColors.TextTertiary, fontSize = 12.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic)
                    }
                }
            }
        }
        if (canExpand) {
            Text(
                text = if (expanded) "Show Less" else "Read More",
                color = AnsuColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp).clickable(onClick = onToggle),
            )
        }
    }
}

/** White "Play" CTA with a circular frosted-glass like button to its right. */
@Composable
private fun PlayLikeRow(
    playLabel: String,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    showListButton: Boolean,
    listStatus: String?,
    onSetListStatus: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlay,
            enabled = playEnabled,
            modifier = Modifier.weight(1f).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.OnAccent),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AnsuColors.OnAccent)
            Text(playLabel, color = AnsuColors.OnAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        FrostedGlassCard(modifier = Modifier.size(50.dp), shape = CircleShape, tintAlpha = 0.5f) {
            IconButton(onClick = onToggleLike, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favourite",
                    tint = if (isLiked) AnsuColors.Accent else AnsuColors.TextPrimary,
                )
            }
        }
        if (showListButton) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                FrostedGlassCard(modifier = Modifier.size(50.dp), shape = CircleShape, tintAlpha = 0.5f) {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (listStatus != null) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = ListStatus.label(listStatus),
                            tint = if (listStatus != null) AnsuColors.Accent else AnsuColors.TextPrimary,
                        )
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ListStatus.ALL.forEach { status ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    ListStatus.label(status),
                                    fontWeight = if (status == listStatus) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = { menuOpen = false; onSetListStatus(status) },
                        )
                    }
                    if (listStatus != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from list") },
                            onClick = { menuOpen = false; onSetListStatus(null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsSectionHeader(title: String) {
    Text(
        text = title,
        color = AnsuColors.TextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 12.dp),
    )
}

/** Episodes per chip: shows longer than this are split into 1-50, 51-100, … pages. */
private const val EPISODE_GROUP_SIZE = 50

/** Episodes shown per group before the "More episodes" chip is tapped. */
private const val EPISODE_PREVIEW_COUNT = 6

/** Pill at the end of the collapsed episode list; tapping it reveals the rest of the group. */
@Composable
private fun MoreEpisodesChip(remaining: Int, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(AnsuColors.Accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = "More episodes ($remaining)",
                color = AnsuColors.OnAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Horizontally scrolling pill row that switches between groups of [EPISODE_GROUP_SIZE] episodes. */
@Composable
private fun EpisodeGroupChips(groups: List<List<SEpisode>>, selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem(selected) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        itemsIndexed(groups) { index, group ->
            val start = index * EPISODE_GROUP_SIZE + 1
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) AnsuColors.Accent else AnsuColors.SurfaceGlassBase)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "$start-${start + group.size - 1}",
                    color = if (isSelected) AnsuColors.OnAccent else AnsuColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: SEpisode, synopsis: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(AnsuColors.BackgroundElevated),
        ) {
            if (episode.thumbnailUrl != null) {
                AsyncImage(model = episode.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = AnsuColors.TextPrimary,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.name.takeIf { it.isNotBlank() && !it.startsWith("Episode") } ?: "Untitled",
                color = AnsuColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EpisodeBadge(episode)
                if (!episode.airDate.isNullOrBlank()) {
                    Text(text = episode.airDate, color = AnsuColors.TextTertiary, fontSize = 12.sp)
                }
            }
            if (!synopsis.isNullOrBlank()) {
                Text(
                    text = synopsis,
                    color = AnsuColors.TextTertiary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** The episode the main button plays and the text on it. */
private class PlayTarget(val episode: SEpisode, val label: String)

/**
 * Picks what the main button does: continue the episode that was left unfinished, move on to the next one
 * after a finished episode, or start at the first episode when nothing was watched yet.
 */
private fun resolvePlayTarget(playable: List<SEpisode>, resume: com.ansu.anime.data.db.ContinueWatchingEntity?): PlayTarget? {
    val sorted = playable.sortedBy { it.episodeNumber }
    val first = sorted.firstOrNull() ?: return null
    fun start(episode: SEpisode) = PlayTarget(episode, "Play Ep. ${episode.episodeNumber.formatEpisodeNumber()}")
    if (resume == null) return start(first)
    val last = sorted.firstOrNull { it.id == resume.episodeId } ?: sorted.firstOrNull { it.episodeNumber == resume.episodeNumber }
        ?: return start(first)
    val finished = resume.durationSeconds > 0 && resume.positionSeconds >= resume.durationSeconds * 0.9
    if (!finished) {
        val number = last.episodeNumber.formatEpisodeNumber()
        val time = if (resume.positionSeconds > 5) " · ${formatClock(resume.positionSeconds)}" else ""
        return PlayTarget(last, "Continue Ep. $number$time")
    }
    val next = sorted.firstOrNull { it.episodeNumber > last.episodeNumber }
    return if (next != null) {
        PlayTarget(next, "Continue Ep. ${next.episodeNumber.formatEpisodeNumber()}")
    } else {
        PlayTarget(first, "Rewatch from Ep. ${first.episodeNumber.formatEpisodeNumber()}")
    }
}

private fun formatClock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Drops source episodes beyond what AniList says the show has. A movie keeps one entry. If trimming would
 * leave nothing (the source numbers episodes differently, e.g. absolute numbering), the list is left alone.
 */
private fun trimToAniListCount(episodes: List<SEpisode>, total: Int?, format: String?): List<SEpisode> {
    val limit = when {
        total != null && total > 0 -> total
        format == "MOVIE" -> 1
        else -> return episodes
    }
    val trimmed = episodes.filter { it.episodeNumber <= limit }
    return if (trimmed.isEmpty()) episodes else trimmed
}

/**
 * Ids of the episodes that have not aired yet. An episode is upcoming when its air date is after
 * today, and so is every later episode with no date at all (a show airs in order, so once one
 * episode is in the future the rest are too). A show whose episodes simply have no metadata, with
 * no future date anywhere, is left fully playable.
 */
private fun upcomingEpisodeIds(episodes: List<SEpisode>, status: String?, nextAiringEpisode: Int?): Set<String> {
    // AniList knows the release status even when the source or metadata has no air dates:
    // an unreleased show has no playable episodes, and an airing show's episodes from the next one on are not out yet.
    if (status == "NOT_YET_RELEASED") return episodes.map { it.id }.toSet()
    if (status == "RELEASING" && nextAiringEpisode != null) {
        val fromAniList = episodes.filter { it.episodeNumber >= nextAiringEpisode }.map { it.id }.toSet()
        if (fromAniList.isNotEmpty()) return fromAniList
    }
    val today = java.time.LocalDate.now()
    val result = mutableSetOf<String>()
    var seenUpcoming = false
    for (episode in episodes.sortedBy { it.episodeNumber }) {
        val date = episode.airDate?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        val upcoming = if (date != null) date.isAfter(today) else seenUpcoming
        if (upcoming) {
            seenUpcoming = true
            result += episode.id
        }
    }
    return result
}

/** One slim line for an episode that has not aired: no empty thumbnail, just the badge and air date. */
@Composable
private fun UpcomingEpisodeRow(episode: SEpisode) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpisodeBadge(episode)
        val title = episode.name.takeIf { it.isNotBlank() && !it.startsWith("Episode") }
        Column(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(text = title, color = AnsuColors.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(text = "Airs ${episode.airDate}", color = AnsuColors.TextTertiary, fontSize = 12.sp)
        }
    }
}

/** A single summary line for upcoming episodes with no announced date, e.g. "E5-E12 - Not yet scheduled". */
@Composable
private fun UndatedUpcomingRow(episodes: List<SEpisode>) {
    val first = episodes.first().episodeNumber.formatEpisodeNumber()
    val last = episodes.last().episodeNumber.formatEpisodeNumber()
    Text(
        text = (if (episodes.size == 1) "E$first" else "E$first-E$last") + " · Not yet scheduled",
        color = AnsuColors.TextTertiary,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun EpisodeBadge(episode: SEpisode) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AnsuColors.SurfaceGlassBase)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "E${episode.episodeNumber.formatEpisodeNumber()}",
            color = AnsuColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RelatedPoster(media: AniListMedia, onClick: () -> Unit, badge: String? = null) {
    Column(modifier = Modifier.width(120.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(AnsuColors.BackgroundElevated),
        ) {
            if (media.posterUrl != null) {
                AsyncImage(model = media.posterUrl, contentDescription = media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (badge != null) {
                PosterGlassLabel(
                    text = badge,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp),
                )
            }
        }
        Text(
            text = media.title,
            color = AnsuColors.TextPrimary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun AniListMedia.toSAnime(): SAnime = SAnime(
    id = id.toString(),
    title = title,
    posterUrl = posterUrl,
    bannerUrl = bannerUrl,
    description = description,
    genres = genres,
    releaseYear = year,
    rating = averageScore?.div(10.0),
    anilistId = id,
    origin = MediaOrigin.Extension(sourceId = 1L, urlPath = id.toString()),
)
