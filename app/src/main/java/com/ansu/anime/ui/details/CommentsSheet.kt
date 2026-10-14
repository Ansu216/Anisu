package com.ansu.anime.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.ansu.anime.anilist.AniListComment
import com.ansu.anime.anilist.AniListThread
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.theme.AnsuColors

/** How many levels of replies are indented; deeper replies stay at the last level so the text keeps its width. */
private const val MAX_INDENT_LEVELS = 3

/**
 * The comments of a show, as a bottom sheet. They are AniList's forum comments: first the show's discussion threads, then
 * the comments of the chosen thread with their replies. Reading needs no account; posting, replying and liking use the
 * signed-in AniList account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(container: AppContainer, mediaId: Int, mediaTitle: String, onDismiss: () -> Unit) {
    val viewModel: CommentsViewModel = viewModel(
        key = "comments-$mediaId",
        factory = viewModelFactory { initializer { CommentsViewModel(container.aniListRepository, mediaId, mediaTitle) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by container.aniListRepository.isLoggedIn.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            val thread = state.thread
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (thread != null) {
                    IconButton(onClick = viewModel::closeThread) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to discussions", tint = AnsuColors.TextPrimary)
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = if (thread == null) 12.dp else 0.dp)) {
                    Text(
                        text = if (thread == null) "Comments" else thread.title,
                        color = AnsuColors.TextPrimary,
                        fontSize = if (thread == null) 20.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (thread == null) "Discussions on AniList" else "${thread.replyCount} replies on AniList",
                        color = AnsuColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = AnsuColors.TextPrimary)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (thread == null) {
                    ThreadList(state, signedIn, viewModel)
                } else {
                    CommentList(state, signedIn, viewModel)
                }
            }

            if (thread != null) {
                Composer(
                    state = state,
                    thread = thread,
                    signedIn = signedIn,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = { viewModel.send(draft) { draft = "" } },
                    onCancelReply = { viewModel.setReplyTarget(null) },
                )
            }
        }
    }
}

@Composable
private fun ThreadList(state: CommentsUiState, signedIn: Boolean, viewModel: CommentsViewModel) {
    val threads = state.threads.orEmpty()
    when {
        state.loadingThreads && threads.isEmpty() -> CenteredSpinner()
        state.threadsFailed && threads.isEmpty() -> Message(
            text = "Could not load the discussions.",
            action = "Try again",
            onAction = viewModel::loadThreads,
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (threads.isEmpty()) {
                item {
                    Text(
                        text = "Nobody has started a discussion about this show on AniList yet.",
                        color = AnsuColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(threads, key = { it.id }) { thread ->
                ThreadRow(thread, onClick = { viewModel.openThread(thread) })
            }
            item {
                if (signedIn) {
                    Button(
                        onClick = viewModel::startDiscussion,
                        enabled = !state.sending,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.OnAccent),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Start a discussion", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Sign in with AniList (My Space) to start a discussion or comment.",
                        color = AnsuColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: AniListThread, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AnsuColors.BackgroundElevated)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            text = thread.title,
            color = AnsuColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append("${thread.replyCount} replies")
                thread.userName?.let { append(" · by $it") }
                if (thread.isLocked) append(" · locked")
            },
            color = AnsuColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CommentList(state: CommentsUiState, signedIn: Boolean, viewModel: CommentsViewModel) {
    val rows = remember(state.comments) { flatten(state.comments) }
    when {
        state.loadingComments && state.comments.isEmpty() -> CenteredSpinner()
        state.commentsFailed -> Message("Could not load the comments.", "Try again", viewModel::retryComments)
        state.comments.isEmpty() -> Message("No comments yet. Be the first to write one.", null, {})
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(rows, key = { it.comment.id }) { row ->
                CommentRow(
                    row = row,
                    canInteract = signedIn && state.thread?.isLocked != true,
                    onLike = { viewModel.toggleLike(row.comment) },
                    onReply = { viewModel.setReplyTarget(row.comment) },
                )
            }
            if (state.hasNextPage) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.loadingComments) {
                            CircularProgressIndicator(color = AnsuColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        } else {
                            TextButton(onClick = viewModel::loadMore) { Text("Load more comments") }
                        }
                    }
                }
            }
        }
    }
}

/** A comment and how deep it sits in its thread (0 = top level). */
private class CommentLine(val comment: AniListComment, val depth: Int)

private fun flatten(comments: List<AniListComment>, depth: Int = 0): List<CommentLine> =
    comments.flatMap { listOf(CommentLine(it, depth)) + flatten(it.replies, depth + 1) }

@Composable
private fun CommentRow(row: CommentLine, canInteract: Boolean, onLike: () -> Unit, onReply: () -> Unit) {
    val comment = row.comment
    Row(modifier = Modifier.fillMaxWidth().padding(start = (minOf(row.depth, MAX_INDENT_LEVELS) * 22).dp)) {
        AsyncImage(
            model = comment.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(34.dp).clip(CircleShape).background(AnsuColors.BackgroundElevated),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.userName, color = AnsuColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "  ·  ${timeAgo(comment.createdAt)}",
                    color = AnsuColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = plainComment(comment.text),
                color = AnsuColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (canInteract) Modifier.clickable(onClick = onLike) else Modifier)
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                ) {
                    Icon(
                        imageVector = if (comment.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (comment.isLiked) "Unlike" else "Like",
                        tint = if (comment.isLiked) AnsuColors.Accent else AnsuColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    if (comment.likeCount > 0) {
                        Text("  ${comment.likeCount}", color = AnsuColors.TextSecondary, fontSize = 12.sp)
                    }
                }
                if (canInteract) {
                    TextButton(onClick = onReply) { Text("Reply", fontSize = 12.sp, color = AnsuColors.TextSecondary) }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    state: CommentsUiState,
    thread: AniListThread,
    signedIn: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelReply: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp, top = 4.dp)) {
        when {
            !signedIn -> Text(
                text = "Sign in with AniList (My Space) to write comments.",
                color = AnsuColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(8.dp),
            )
            thread.isLocked -> Text(
                text = "This discussion is locked.",
                color = AnsuColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(8.dp),
            )
            else -> {
                state.replyingTo?.let { target ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = "Replying to ${target.userName}",
                            color = AnsuColors.TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onCancelReply, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel reply", tint = AnsuColors.TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        placeholder = { Text("Write a comment…") },
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.sending) {
                        CircularProgressIndicator(color = AnsuColors.Accent, strokeWidth = 2.dp, modifier = Modifier.padding(12.dp).size(24.dp))
                    } else {
                        IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (draft.isNotBlank()) AnsuColors.Accent else AnsuColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AnsuColors.Accent)
    }
}

@Composable
private fun Message(text: String, action: String?, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = AnsuColors.TextSecondary, fontSize = 14.sp)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** "5m ago", "3h ago", "2d ago", then the plain date. [epochSeconds] of 0 (unknown) gives an empty string. */
private fun timeAgo(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val seconds = (System.currentTimeMillis() / 1000 - epochSeconds).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        seconds < 30 * 86_400L -> "${seconds / 86_400}d ago"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(epochSeconds * 1000))
    }
}

/**
 * AniList comments are markdown with a few site-specific tags. This keeps the words: images, videos and links lose their
 * markup (a link keeps its text), spoilers are marked as such, and plain HTML tags and emphasis marks are dropped.
 */
private fun plainComment(raw: String): String = raw
    .replace(Regex("img\\d*\\([^)]*\\)", RegexOption.IGNORE_CASE), "[image]")
    .replace(Regex("(webm|youtube)\\([^)]*\\)", RegexOption.IGNORE_CASE), "[video]")
    .replace(Regex("~!([\\s\\S]*?)!~"), "[spoiler: $1]")
    .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "[image]")
    .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("<[^>]+>"), "")
    .replace(Regex("[*_~`#]{1,3}"), "")
    .trim()
