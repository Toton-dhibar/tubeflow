package io.github.aedev.flow.innertube.pages

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.utils.CommentRequestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class CommentRequestParsingTest {
    @Test
    fun `next response supplies the video and reply params`() {
        val response = fixture("next_response.json")
        val replyRenderer =
            response.jsonObject["onResponseReceivedEndpoints"]!!
                .jsonArray[0]
                .jsonObject["appendContinuationItemsAction"]!!
                .jsonObject["continuationItems"]!!
                .jsonArray[1]
                .jsonObject["commentThreadRenderer"]!!
                .jsonObject["replies"]!!
                .jsonObject["commentRepliesRenderer"]

        assertThat(response.findCommentSubmitParams()).isEqualTo("video-create-params")
        assertThat(replyRenderer.findCommentReplyParams()).isEqualTo("reply-create-params")
    }

    @Test
    fun `create comment response parses the returned comment`() {
        val comment = fixture("create_comment_response.json").toCreatedComment("video-id")

        assertThat(comment?.id).isEqualTo("created-comment-id")
        assertThat(comment?.text).isEqualTo("Fixture comment")
        assertThat(comment?.author).isEqualTo("Fixture author")
    }

    @Test
    fun `request builder keeps context params and user text in the expected fields`() {
        val context = Json.parseToJsonElement("""{"client":{"clientName":"WEB"}}""")

        val request =
            CommentRequestBuilder
                .build(context, "video-create-params", "Comment body")
                .jsonObject

        assertThat(request["context"]).isEqualTo(context)
        assertThat(request["createCommentParams"]?.toString()).isEqualTo("\"video-create-params\"")
        assertThat(request["commentText"]?.toString()).isEqualTo("\"Comment body\"")
    }

    private fun fixture(name: String) =
        Json.parseToJsonElement(
            checkNotNull(javaClass.classLoader?.getResourceAsStream("comments/$name")) {
                "Missing comment fixture: $name"
            }.bufferedReader().use { it.readText() },
        )
}
