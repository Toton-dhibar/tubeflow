package io.github.aedev.flow.innertube.pages.renderer

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class AccountVideoFeedPageTest {
    @Test
    fun `signed in feed parser extracts video rows and continuation`() {
        val response =
            checkNotNull(javaClass.classLoader?.getResourceAsStream("comments/account_feed_response.json"))
                .bufferedReader()
                .use { Json.parseToJsonElement(it.readText()) }
                .toAccountVideoFeedPage()

        assertThat(response.videos.map { it.id }).containsExactly("account-video")
        assertThat(response.videos.single().channelId).isEqualTo("account-channel")
        assertThat(response.continuation).isEqualTo("account-next-page")
    }
}
