package com.ansu.anime.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.ansu.anime.anilist.AniListComment
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.anilist.AniListThread
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.details.CommentsSheet
import com.ansu.anime.ui.details.plainComment
import com.ansu.anime.ui.details.timeAgo
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** How many of the most-liked comments the portrait player shows before "View all". */
private const val PREVIEW_COMMENTS = 3

data class EpisodeCommentsState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** The episode's discussion thread on AniList; null when nobody has opened one. */
    val thread: AniListThread? = null,
    val comments: List<AniListComment> = emptyList(),
    val creating: Boolean = false,
    /** How many discussions on AniList are about this episode (usually one; the busiest is shown). */
    val threadCount: Int = 0,
)

/**
 * The comments of one episode. AniList has no per-episode comments of its own, so this finds the show's forum thread
 * that is about this episode (see [AniListRepository.getEpisodeThreads]) and reads its comments.
 */
class EpisodeCommentsViewModel(
    private val repository: AniListRepository,
    private val mediaId: Int,
    private val mediaTitle: String,
    private val episode: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(EpisodeCommentsState())
    val state: StateFlow<EpisodeCommentsState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = EpisodeCommentsState(loading = true)
        viewModelScope.launch {
            val threads = repository.getEpisodeThreads(mediaId, episode)
            if (threads == null) {
                _state.value = EpisodeCommentsState(loading = false, failed = true)
                return@launch
            }
            val thread = threads.firstOrNull()
            val page = thread?.let { repository.getThreadComments(it.id, 1) }
            if (thread != null && page == null) {
                // The discussion exists but its comments did not load: say so, never "no comments yet".
                _state.value = EpisodeCommentsState(loading = false, failed = true)
                return@launch
            }
            val comments = page?.comments.orEmpty()
            _state.value = EpisodeCommentsState(loading = false, thread = thread, comments = comments, threadCount = threads.size)
        }
    }

    /** Opens a discussion for this episode (the reader is signed in) and shows it. */
    fun startDiscussion() {
        if (_state.value.creating) return
        _state.value = _state.value.copy(creating = true)
        viewModelScope.launch {
            val title = "$mediaTitle - Episode $episode Discussion"
            val id = repository.startThread(
                mediaId = mediaId,
                title = title,
                body = "What did you think of episode $episode of $mediaTitle? Share your thoughts below.",
            )
            _state.value = if (id != null) {
                EpisodeCommentsState(loading = false, thread = AniListThread(id, title, 0, repository.viewer.value?.name, false), threadCount = 1)
            } else {
                _state.value.copy(creating = false)
            }
        }
    }
}

/**
 * "Comments" under the episode description in the portrait player: the most-liked comments of this episode's
 * AniList discussion, with "View all" opening the full thread (read, reply, like) in the comments sheet. When the
 * episode has no discussion yet it says so, and a signed-in reader can start one.
 */
@Composable
fun EpisodeCommentsSection(
    container: AppContainer,
    mediaId: Int,
    mediaTitle: String,
    episode: Int,
    modifier: Modifier = Modifier,
) {
    val viewModel: EpisodeCommentsViewModel = viewModel(
        key = "episode-comments-$mediaId-$episode",
        factory = viewModelFactory { initializer { EpisodeCommentsViewModel(container.aniListRepository, mediaId, mediaTitle, episode) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by container.aniListRepository.isLoggedIn.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }
    val thread = state.thread

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("Comments", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (thread != null && thread.replyCount > 0) {
                Text("  ${thread.replyCount}", color = AnsuColors.TextSecondary, fontSize = 14.sp)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .then(if (thread != null) Modifier.clickable { sheetOpen = true } else Modifier)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> CircularProgressIndicator(
                    color = AnsuColors.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp).align(Alignment.CenterHorizontally),
                )
                state.failed -> {
                    Text("Couldn't load the comments.", color = AnsuColors.TextSecondary, fontSize = 14.sp)
                    TextButton(onClick = viewModel::load) { Text("Try again") }
                }
                thread == null -> {
                    Text(
                        "Nobody has opened a discussion for episode $episode on AniList yet.",
                        color = AnsuColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                    if (signedIn) {
                        TextButton(onClick = viewModel::startDiscussion, enabled = !state.creating) {
                            Text(if (state.creating) "Starting…" else "Start the discussion")
                        }
                    } else {
                        Text("Sign in with AniList (My Space) to start one.", color = AnsuColors.TextTertiary, fontSize = 13.sp)
                    }
                }
                else -> {
                    val top = state.comments.sortedByDescending { it.likeCount }.take(PREVIEW_COMMENTS)
                    if (top.isEmpty()) {
                        Text("No comments yet. Be the first to write one.", color = AnsuColors.TextSecondary, fontSize = 14.sp)
                    }
                    top.forEach { PreviewComment(it) }
                    if (state.threadCount > 1) {
                        Text(
                            "Showing the busiest of ${state.threadCount} discussions about this episode.",
                            color = AnsuColors.TextTertiary,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = if (state.comments.isEmpty()) "Open discussion" else "View all comments",
                        color = AnsuColors.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (sheetOpen && thread != null) {
        CommentsSheet(
            container = container,
            mediaId = mediaId,
            mediaTitle = mediaTitle,
            onDismiss = {
                sheetOpen = false
                // Replies and likes written in the sheet show in the preview when it closes.
                viewModel.load()
            },
            initialThread = thread,
        )
    }
}

@Composable
private fun PreviewComment(comment: AniListComment) {
    Row(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = comment.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(AnsuColors.BackgroundElevated),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.userName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("  ·  ${timeAgo(comment.createdAt)}", color = AnsuColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
            Text(
                text = plainComment(comment.text),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (comment.likeCount > 0 || comment.replies.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (comment.likeCount > 0) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = AnsuColors.TextSecondary, modifier = Modifier.size(13.dp))
                        Text(" ${comment.likeCount}", color = AnsuColors.TextSecondary, fontSize = 12.sp)
                    }
                    if (comment.replies.isNotEmpty()) {
                        Text(
                            text = (if (comment.likeCount > 0) "   ·   " else "") + "${comment.replies.size} ${if (comment.replies.size == 1) "reply" else "replies"}",
                            color = AnsuColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
