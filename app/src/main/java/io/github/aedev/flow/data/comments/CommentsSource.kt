package io.github.aedev.flow.data.comments

import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.innertube.pages.CommentPostErrorHint
import io.github.aedev.flow.innertube.pages.CommentPostResponseException
import io.ktor.client.plugins.ClientRequestException

/** Where a [CommentsPager] reads one video's comments from. */
internal interface CommentsSource {
    /** The first page in the order [sortToken] names, or the section's own default when it is null. */
    suspend fun firstPage(
        videoId: String,
        sortToken: String?,
    ): CommentsPageResult

    suspend fun nextPage(
        videoId: String,
        continuation: String,
    ): CommentsPageResult

    suspend fun replies(
        videoId: String,
        continuation: String,
    ): CommentsPageResult

    /** YouTube's own Timed list, read through any token of the section; empty when there is none. */
    suspend fun timed(
        videoId: String,
        sectionToken: String,
    ): CommentsPageResult

    suspend fun postComment(
        videoId: String,
        createCommentParams: String,
        commentText: String,
    ): CommentPostResult = CommentPostResult.Failed(CommentPostFailure.NETWORK)
}

/** A video's comment section, opened from the watch response the player already holds. */
internal class VideoCommentsSource(
    private val repository: YouTubeRepository,
) : CommentsSource {
    override suspend fun firstPage(
        videoId: String,
        sortToken: String?,
    ) = repository.getVideoComments(videoId, sortToken)

    override suspend fun nextPage(
        videoId: String,
        continuation: String,
    ) = repository.getMoreVideoComments(videoId, continuation)

    override suspend fun replies(
        videoId: String,
        continuation: String,
    ) = repository.getVideoCommentReplies(videoId, continuation)

    override suspend fun timed(
        videoId: String,
        sectionToken: String,
    ) = repository.getTimedComments(videoId, sectionToken)

    override suspend fun postComment(
        videoId: String,
        createCommentParams: String,
        commentText: String,
    ) = repository.postVideoComment(videoId, createCommentParams, commentText)
}

internal fun Throwable.toCommentPostFailure(): CommentPostFailure {
    val message = generateSequence(this) { it.cause }.mapNotNull(Throwable::message).joinToString(" ").lowercase()
    val causes = generateSequence(this) { it.cause }.toList()
    val responseError = causes.filterIsInstance<CommentPostResponseException>().firstOrNull()
    val status =
        responseError?.statusCode
            ?: causes
                .filterIsInstance<ClientRequestException>()
                .firstOrNull()
                ?.response
                ?.status
                ?.value
    return when {
        responseError?.hint == CommentPostErrorHint.COMMENTS_DISABLED -> CommentPostFailure.COMMENTS_DISABLED
        responseError?.hint == CommentPostErrorHint.RATE_LIMITED -> CommentPostFailure.RATE_LIMITED
        responseError?.hint == CommentPostErrorHint.SPAM_BLOCK -> CommentPostFailure.SPAM_BLOCK
        status == 429 || "rate limit" in message || "too many comments" in message -> CommentPostFailure.RATE_LIMITED
        "comments are disabled" in message || "comments turned off" in message -> CommentPostFailure.COMMENTS_DISABLED
        "spam" in message || "blocked as spam" in message -> CommentPostFailure.SPAM_BLOCK
        status == 401 || status == 403 -> CommentPostFailure.COOKIES_EXPIRED
        else -> CommentPostFailure.NETWORK
    }
}
