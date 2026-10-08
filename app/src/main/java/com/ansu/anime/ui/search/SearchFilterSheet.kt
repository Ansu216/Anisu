@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ansu.anime.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.anilist.AniListSearchFilters
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.launch

/**
 * Everything the filter sheet offers. The second half of each pair is what AniList expects
 * (an enum name, or the exact genre/tag name); the first half is what the user reads.
 */
internal object SearchFilterOptions {
    val sorts: List<Pair<String, String?>> = listOf(
        "Best match" to null,
        "Popularity" to "POPULARITY_DESC",
        "Top rated" to "SCORE_DESC",
        "Trending" to "TRENDING_DESC",
        "Newest" to "START_DATE_DESC",
        "Oldest" to "START_DATE",
        "Title A-Z" to "TITLE_ROMAJI",
        "Most episodes" to "EPISODES_DESC",
    )

    val formats: List<Pair<String, String>> = listOf(
        "TV" to "TV",
        "TV Short" to "TV_SHORT",
        "Movie" to "MOVIE",
        "Special" to "SPECIAL",
        "OVA" to "OVA",
        "ONA" to "ONA",
        "Music" to "MUSIC",
    )

    val statuses: List<Pair<String, String>> = listOf(
        "Airing" to "RELEASING",
        "Finished" to "FINISHED",
        "Upcoming" to "NOT_YET_RELEASED",
        "Hiatus" to "HIATUS",
        "Cancelled" to "CANCELLED",
    )

    val seasons: List<Pair<String, String>> = listOf(
        "Winter" to "WINTER",
        "Spring" to "SPRING",
        "Summer" to "SUMMER",
        "Fall" to "FALL",
    )

    val genres: List<String> = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo",
        "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life",
        "Sports", "Supernatural", "Thriller",
    )

    /** AniList tags, grouped the way the sheet shows them: one collapsible category each. */
    val tagGroups: List<Pair<String, List<String>>> = listOf(
        "Demographic" to listOf("Shounen", "Shoujo", "Seinen", "Josei", "Kids"),
        "Themes" to listOf(
            "Isekai", "Time Loop", "Survival", "Revenge", "Reincarnation", "Time Manipulation",
            "Coming of Age", "Tragedy", "Super Power", "Magic", "Swordplay", "Gods", "Youkai",
            "Vampire", "Ninja", "Samurai", "Necromancy",
        ),
        "Setting" to listOf(
            "School", "Medieval", "Post-Apocalyptic", "Space", "Cyberpunk", "Steampunk",
            "Urban Fantasy", "Historical", "Rural", "Military", "Dungeon", "Virtual World",
            "Alternate Universe", "Wilderness",
        ),
        "Cast & characters" to listOf(
            "Anti-Hero", "Ensemble Cast", "Female Protagonist", "Male Protagonist", "Villainess",
            "Detective", "Robots", "Cute Girls Doing Cute Things", "Found Family", "Kuudere", "Tsundere",
        ),
        "Sports & games" to listOf(
            "Football", "Basketball", "Baseball", "Tennis", "Volleyball", "Boxing", "Cycling",
            "Swimming", "Racing", "Martial Arts", "Card Battle", "Chess", "Go", "Shogi",
        ),
    )
}

/** One removable chip shown under the search bar for a filter that is currently on. */
internal data class ActiveFilter(val label: String, val remove: (AniListSearchFilters) -> AniListSearchFilters)

internal fun activeFilterChips(f: AniListSearchFilters): List<ActiveFilter> = buildList {
    f.sort?.let { s ->
        val name = SearchFilterOptions.sorts.firstOrNull { it.second == s }?.first ?: s
        add(ActiveFilter("Sort: $name") { it.copy(sort = null) })
    }
    SearchFilterOptions.formats.filter { it.second in f.formats }.forEach { (label, value) ->
        add(ActiveFilter(label) { it.copy(formats = it.formats - value) })
    }
    SearchFilterOptions.statuses.filter { it.second in f.statuses }.forEach { (label, value) ->
        add(ActiveFilter(label) { it.copy(statuses = it.statuses - value) })
    }
    f.season?.let { s ->
        val name = SearchFilterOptions.seasons.firstOrNull { it.second == s }?.first ?: s
        add(ActiveFilter(name) { it.copy(season = null) })
    }
    f.year?.let { y -> add(ActiveFilter(y.toString()) { it.copy(year = null) }) }
    f.genres.forEach { g -> add(ActiveFilter(g) { it.copy(genres = it.genres - g) }) }
    f.tags.forEach { t -> add(ActiveFilter(t) { it.copy(tags = it.tags - t) }) }
}

private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value

/**
 * The bottom sheet behind the filter button. Changes are kept in a draft and only reach the
 * search when "Show results" is tapped; swiping the sheet away discards them.
 */
@Composable
fun SearchFilterSheet(
    filters: AniListSearchFilters,
    onApply: (AniListSearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(filters) }
    val currentYear = remember { java.time.Year.now().value }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AnsuColors.BackgroundElevated,
        contentColor = AnsuColors.TextPrimary,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AnsuColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { draft = AniListSearchFilters() },
                    enabled = draft.isActive,
                    colors = ButtonDefaults.textButtonColors(contentColor = AnsuColors.Accent),
                ) {
                    Text("Reset")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                FilterSection(title = "Sort by") {
                    ChipFlow {
                        SearchFilterOptions.sorts.forEach { (label, value) ->
                            OptionChip(label, selected = draft.sort == value) { draft = draft.copy(sort = value) }
                        }
                    }
                }

                FilterSection(title = "Format", trailing = countLabel(draft.formats.size)) {
                    ChipFlow {
                        SearchFilterOptions.formats.forEach { (label, value) ->
                            OptionChip(label, selected = value in draft.formats) {
                                draft = draft.copy(formats = draft.formats.toggled(value))
                            }
                        }
                    }
                }

                FilterSection(title = "Status", trailing = countLabel(draft.statuses.size)) {
                    ChipFlow {
                        SearchFilterOptions.statuses.forEach { (label, value) ->
                            OptionChip(label, selected = value in draft.statuses) {
                                draft = draft.copy(statuses = draft.statuses.toggled(value))
                            }
                        }
                    }
                }

                FilterSection(title = "Season") {
                    ChipFlow {
                        SearchFilterOptions.seasons.forEach { (label, value) ->
                            OptionChip(label, selected = draft.season == value) {
                                draft = draft.copy(season = if (draft.season == value) null else value)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((currentYear + 1 downTo 1980).toList()) { year ->
                            OptionChip(year.toString(), selected = draft.year == year) {
                                draft = draft.copy(year = if (draft.year == year) null else year)
                            }
                        }
                    }
                }

                FilterSection(title = "Genres", trailing = countLabel(draft.genres.size)) {
                    ChipFlow {
                        SearchFilterOptions.genres.forEach { genre ->
                            OptionChip(genre, selected = genre in draft.genres) {
                                draft = draft.copy(genres = draft.genres.toggled(genre))
                            }
                        }
                    }
                }

                FilterSection(title = "Tags", trailing = countLabel(draft.tags.size)) {
                    SearchFilterOptions.tagGroups.forEachIndexed { index, (category, tags) ->
                        TagCategory(
                            title = category,
                            tags = tags,
                            selected = draft.tags,
                            startExpanded = index == 0 || tags.any { it in filters.tags },
                            onToggle = { draft = draft.copy(tags = draft.tags.toggled(it)) },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(
                    onClick = { scope.launch { sheetState.hide(); onApply(draft) } },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.OnAccent),
                    shape = RoundedCornerShape(25.dp),
                ) {
                    Text("Show results", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun countLabel(count: Int): String? = if (count > 0) "$count selected" else null

@Composable
private fun FilterSection(
    title: String,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
            if (trailing != null) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = AnsuColors.TextTertiary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** Selected chips are white with dark text and the rest are dark, like the episode-group chips. */
@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) AnsuColors.Accent else AnsuColors.SurfaceGlassBase.copy(alpha = 0.6f))
            .border(1.dp, if (selected) Color.Transparent else AnsuColors.StrokeGlass, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) AnsuColors.OnAccent else AnsuColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** One tag category: a header that folds its chips away, with a count of how many are picked. */
@Composable
private fun TagCategory(
    title: String,
    tags: List<String>,
    selected: Set<String>,
    startExpanded: Boolean,
    onToggle: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(startExpanded) }
    val pickedCount = tags.count { it in selected }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AnsuColors.SurfaceGlassBase.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = AnsuColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (pickedCount > 0) {
                Text(
                    "$pickedCount selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = AnsuColors.TextSecondary,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = AnsuColors.TextSecondary,
            )
        }
        if (expanded) {
            FlowRow(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    OptionChip(tag, selected = tag in selected) { onToggle(tag) }
                }
            }
        }
    }
}
