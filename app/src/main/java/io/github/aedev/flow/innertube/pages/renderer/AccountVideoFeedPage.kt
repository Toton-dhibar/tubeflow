package io.github.aedev.flow.innertube.pages.renderer

import io.github.aedev.flow.data.model.Video
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class AccountVideoFeedPage(
    val videos: List<Video>,
    val continuation: String?,
)

internal fun JsonElement.toAccountVideoFeedPage(): AccountVideoFeedPage {
    val videos = LinkedHashMap<String, Video>()

    fun collect(element: JsonElement) {
        when (val item = element.toFeedItem(FeedItemOwner())) {
            is FeedItem.VideoItem -> {
                videos.putIfAbsent(item.video.id, item.video)
                return
            }

            is FeedItem.ShortItem -> {
                videos.putIfAbsent(item.video.id, item.video)
                return
            }

            else -> Unit
        }
        when (element) {
            is JsonObject -> element.values.forEach(::collect)
            is JsonArray -> element.forEach(::collect)
            else -> Unit
        }
    }

    fun continuation(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                val token =
                    (element["continuationCommand"] as? JsonObject)
                        ?.get("token")
                        .let { it as? JsonPrimitive }
                        ?.contentOrNull
                if (!token.isNullOrBlank()) return token
                element.values.firstNotNullOfOrNull(::continuation)?.let { return it }
            }

            is JsonArray -> element.firstNotNullOfOrNull(::continuation)?.let { return it }
            else -> Unit
        }
        return null
    }

    collect(this)
    return AccountVideoFeedPage(videos.values.toList(), continuation(this))
}
