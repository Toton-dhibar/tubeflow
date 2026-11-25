package io.github.aedev.flow.data.comments

import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.innertube.pages.VideoCommentSort

/**
 * One page of comments, the continuation that loads the next one, and the orders the section offers.
 *
 * [disabled] marks a video whose uploader turned comments off, as opposed to one nobody has
 * commented on yet.
 */
data class CommentsPageResult(
    val comments: List<Comment> = emptyList(),
    val continuation: String? = null,
    val sortOptions: List<VideoCommentSort> = emptyList(),
    val totalText: String? = null,
    val totalCount: Long? = null,
    val disabled: Boolean = false,
    val createCommentParams: String? = null,
) {
    val hasMore: Boolean get() = continuation != null

    companion object {
        val EMPTY = CommentsPageResult()
        val DISABLED = CommentsPageResult(disabled = true)
    }
}

enum class CommentPostFailure {
    COOKIES_EXPIRED,
    COMMENTS_DISABLED,
    RATE_LIMITED,
    SPAM_BLOCK,
    NETWORK,
    FORM_UNAVAILABLE,
}

internal sealed interface CommentPostResult {
    data class Posted(val comment: Comment) : CommentPostResult

    data class Failed(val failure: CommentPostFailure) : CommentPostResult
}

data class CommentPostUiState(
    val isPosting: Boolean = false,
    val postingTarget: String? = null,
    val failure: CommentPostFailure? = null,
    val failureTarget: String? = null,
    val successTarget: String? = null,
    val successVersion: Int = 0,
    val isCoolingDown: Boolean = false,
)
