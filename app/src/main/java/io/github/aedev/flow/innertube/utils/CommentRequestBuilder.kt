package io.github.aedev.flow.innertube.utils

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object CommentRequestBuilder {
    fun build(
        context: JsonElement,
        createCommentParams: String,
        commentText: String,
    ): JsonObject =
        buildJsonObject {
            put("context", context)
            put("createCommentParams", createCommentParams)
            put("commentText", commentText)
        }
}
