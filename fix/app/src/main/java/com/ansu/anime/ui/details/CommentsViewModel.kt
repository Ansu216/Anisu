package com.ansu.anime.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansu.anime.anilist.AniListComment
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.anilist.AniListThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CommentsUiState(
    /** The show's discussion threads; null until they have loaded (see [threadsFailed]). */
    val threads: List<AniListThread>? = null,
    val loadingThreads: Boolean = true,
    val threadsFailed: Boolean = false,
    /** The thread being read; null while the list of threads is showing. */
    val thread: AniListThread? = null,
    val comments: List<AniListComment> = emptyList(),
    val loadingComments: Boolean = false,
    val commentsFailed: Boolean = false,
    val hasNextPage: Boolean = false,
    val page: Int = 1,
    val replyingTo: AniListComment? = null,
    val sending: Boolean = false,
)

/**
 * The comments of one show, which on AniList are the comments of its forum threads: pick a thread, read its comments with
 * their replies, and, when signed in, post, reply and like. Everything goes through the user's own AniList account.
 */
class CommentsViewModel(
    private val repository: AniListRepository,
    private val mediaId: Int,
    private val mediaTitle: String,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state

    init {
        loadThreads()
    }

    fun loadThreads() {
        _state.value = _state.value.copy(loadingThreads = true, threadsFailed = false)
        viewModelScope.launch {
            val threads = repository.getMediaThreads(mediaId)
            _state.value = _state.value.copy(
                threads = threads.orEmpty(),
                loadingThreads = false,
                threadsFailed = threads == null,
            )
        }
    }

    fun openThread(thread: AniListThread) {
        _state.value = _state.value.copy(
            thread = thread,
            comments = emptyList(),
            page = 1,
            hasNextPage = false,
            replyingTo = null,
            commentsFailed = false,
        )
        loadComments(page = 1)
    }

    /** Back from a thread to the list of threads (refreshing it, since the reply counts may have moved). */
    fun closeThread() {
        _state.value = _state.value.copy(thread = null, comments = emptyList(), replyingTo = null)
        loadThreads()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingComments || !s.hasNextPage) return
        loadComments(page = s.page + 1)
    }

    fun retryComments() = loadComments(page = 1)

    private fun loadComments(page: Int) {
        val thread = _state.value.thread ?: return
        _state.value = _state.value.copy(loadingComments = true, commentsFailed = false)
        viewModelScope.launch {
            val result = repository.getThreadComments(thread.id, page)
            // The reader may have gone back or opened another thread while this was loading.
            if (_state.value.thread?.id != thread.id) return@launch
            _state.value = if (result == null) {
                _state.value.copy(loadingComments = false, commentsFailed = _state.value.comments.isEmpty())
            } else {
                _state.value.copy(
                    loadingComments = false,
                    comments = if (page == 1) result.comments else _state.value.comments + result.comments,
                    hasNextPage = result.hasNextPage,
                    page = page,
                )
            }
        }
    }

    fun setReplyTarget(comment: AniListComment?) {
        _state.value = _state.value.copy(replyingTo = comment)
    }

    /** Posts [text] to the open thread (as a reply when one is being answered); [onPosted] runs on success so the field can clear. */
    fun send(text: String, onPosted: () -> Unit) {
        val s = _state.value
        val thread = s.thread ?: return
        val body = text.trim()
        if (body.isEmpty() || s.sending) return
        val parent = s.replyingTo
        _state.value = s.copy(sending = true)
        viewModelScope.launch {
            val id = repository.postComment(thread.id, body, parent?.id)
            if (id == null) {
                _state.value = _state.value.copy(sending = false)
                return@launch
            }
            val me = repository.viewer.value
            val posted = AniListComment(
                id = id,
                userId = me?.id,
                userName = me?.name ?: "You",
                avatarUrl = me?.avatarUrl,
                text = body,
                likeCount = 0,
                isLiked = false,
                createdAt = System.currentTimeMillis() / 1000,
            )
            // Shown at once where it belongs, instead of reloading a thread whose newest comments sit on a later page.
            val updated = if (parent == null) {
                _state.value.comments + posted
            } else {
                _state.value.comments.mapComments { if (it.id == parent.id) it.copy(replies = it.replies + posted) else it }
            }
            _state.value = _state.value.copy(comments = updated, sending = false, replyingTo = null)
            onPosted()
        }
    }

    /** Hearts or un-hearts a comment; the count changes at once and is put back if AniList refuses. */
    fun toggleLike(comment: AniListComment) {
        fun flip(liked: Boolean) = _state.value.comments.mapComments {
            if (it.id == comment.id) it.copy(isLiked = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) else it
        }
        _state.value = _state.value.copy(comments = flip(!comment.isLiked))
        viewModelScope.launch {
            if (!repository.toggleCommentLike(comment.id)) {
                _state.value = _state.value.copy(comments = _state.value.comments.mapComments {
                    if (it.id == comment.id) comment.copy(replies = it.replies) else it
                })
            }
        }
    }

    /** Opens a discussion about the show (when it has none, or the reader wants a new one) and goes into it. */
    fun startDiscussion() {
        if (_state.value.sending) return
        _state.value = _state.value.copy(sending = true)
        viewModelScope.launch {
            val id = repository.startThread(
                mediaId = mediaId,
                title = "$mediaTitle - Discussion",
                body = "What did you think of $mediaTitle? Share your thoughts below.",
            )
            _state.value = _state.value.copy(sending = false)
            if (id != null) {
                openThread(AniListThread(id, "$mediaTitle - Discussion", 0, repository.viewer.value?.name, false))
            }
        }
    }
}

/** Applies [transform] to every comment in the tree, replies included. */
private fun List<AniListComment>.mapComments(transform: (AniListComment) -> AniListComment): List<AniListComment> =
    map { comment ->
        val changed = transform(comment)
        changed.copy(replies = changed.replies.mapComments(transform))
    }
