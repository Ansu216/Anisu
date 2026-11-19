package com.ansu.anime.ui.details

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansu.anime.anilist.AniListComment
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.anilist.AniListThread
import com.ansu.anime.data.upload.ImageUploader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A picture chosen for the comment being written: shown as a thumbnail while it uploads, then sent as a link. */
data class CommentAttachment(
    val localUri: String,
    /** The hosted link once the upload is done. */
    val url: String? = null,
    val uploading: Boolean = true,
    val failed: Boolean = false,
)

data class CommentsUiState(
    /** The show's discussion threads; null until they have loaded (see [threadsFailed]). */
    val threads: List<AniListThread>? = null,
    val loadingThreads: Boolean = true,
    val threadsFailed: Boolean = false,
    /** How many discussions the show has on AniList in all (the list loads 50 at a time and keeps going). */
    val threadTotal: Int = 0,
    val threadsHasNext: Boolean = false,
    val threadsPage: Int = 1,
    val loadingMoreThreads: Boolean = false,
    /** The thread being read; null while the list of threads is showing. */
    val thread: AniListThread? = null,
    val comments: List<AniListComment> = emptyList(),
    val loadingComments: Boolean = false,
    val commentsFailed: Boolean = false,
    val hasNextPage: Boolean = false,
    val page: Int = 1,
    val replyingTo: AniListComment? = null,
    val sending: Boolean = false,
    val attachment: CommentAttachment? = null,
)

/**
 * The comments of one show, which on AniList are the comments of its forum threads: pick a thread, read its comments with
 * their replies, and, when signed in, post, reply and like. Everything goes through the user's own AniList account.
 */
class CommentsViewModel(
    private val repository: AniListRepository,
    private val mediaId: Int,
    private val mediaTitle: String,
    private val uploader: ImageUploader,
    initialThread: AniListThread? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state

    init {
        if (initialThread != null) openThread(initialThread) else loadThreads()
    }

    fun loadThreads() {
        _state.value = _state.value.copy(loadingThreads = true, threadsFailed = false)
        viewModelScope.launch {
            val result = repository.getMediaThreadsPage(mediaId, 1)
            _state.value = _state.value.copy(
                threads = result?.threads.orEmpty(),
                loadingThreads = false,
                threadsFailed = result == null,
                threadTotal = result?.total ?: 0,
                threadsHasNext = result?.hasNextPage == true,
                threadsPage = 1,
            )
        }
    }

    /** Adds the next 50 discussions to the list; the list calls this when the reader reaches its end. */
    fun loadMoreThreads() {
        val s = _state.value
        if (s.loadingMoreThreads || s.loadingThreads || !s.threadsHasNext) return
        _state.value = s.copy(loadingMoreThreads = true)
        viewModelScope.launch {
            val next = s.threadsPage + 1
            val result = repository.getMediaThreadsPage(mediaId, next)
            val current = _state.value
            _state.value = if (result == null) {
                // Stays at the end of what is loaded; reaching the end again tries once more.
                current.copy(loadingMoreThreads = false)
            } else {
                val known = current.threads.orEmpty().map { it.id }.toSet()
                current.copy(
                    threads = current.threads.orEmpty() + result.threads.filter { it.id !in known },
                    loadingMoreThreads = false,
                    threadTotal = result.total,
                    threadsHasNext = result.hasNextPage,
                    threadsPage = next,
                )
            }
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
            attachment = null,
        )
        loadComments(page = 1)
    }

    /** Back from a thread to the list of threads (refreshing it, since the reply counts may have moved). */
    fun closeThread() {
        _state.value = _state.value.copy(thread = null, comments = emptyList(), replyingTo = null, attachment = null)
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

    /** Takes a picture from the phone, uploads it, and keeps its link ready to go out with the next comment. */
    fun attachPicture(uri: Uri) {
        _state.value = _state.value.copy(attachment = CommentAttachment(localUri = uri.toString()))
        viewModelScope.launch {
            val link = uploader.upload(uri)
            val current = _state.value.attachment
            // Removed or replaced while it was uploading: this result is no longer wanted.
            if (current == null || current.localUri != uri.toString()) return@launch
            _state.value = _state.value.copy(
                attachment = current.copy(url = link, uploading = false, failed = link == null),
            )
        }
    }

    fun removeAttachment() {
        _state.value = _state.value.copy(attachment = null)
    }

    /**
     * Posts [text] (and the attached picture, written the way AniList reads a picture: `img(link)`) to the open thread, as a
     * reply when one is being answered, wrapped in `~!...!~` when [spoiler] is on; [onPosted] runs on success so the field can clear.
     */
    fun send(text: String, spoiler: Boolean = false, onPosted: () -> Unit) {
        val s = _state.value
        val thread = s.thread ?: return
        val picture = s.attachment
        if (s.sending || picture?.uploading == true) return
        val pictureLink = picture?.url
        val content = listOfNotNull(text.trim().ifEmpty { null }, pictureLink?.let { "img($it)" }).joinToString("\n\n")
        if (content.isEmpty()) return
        // AniList's spoiler markup: everything between ~! and !~ is hidden until tapped.
        val body = if (spoiler) "~!$content!~" else content
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
            _state.value = _state.value.copy(comments = updated, sending = false, replyingTo = null, attachment = null)
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
