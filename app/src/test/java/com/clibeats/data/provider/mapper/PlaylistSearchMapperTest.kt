@file:Suppress("ForbiddenImport")

package com.clibeats.data.provider.mapper

import com.clibeats.data.provider.dto.SearchResponse
import com.clibeats.domain.model.CollectionKind
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** Shape taken from a real YouTube Music playlists-filtered search ("tamil hits"). */
class PlaylistSearchMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun item(
        id: String,
        title: String,
        subtitle: String,
    ) = """{"musicResponsiveListItemRenderer":{
        "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"small.jpg"},{"url":"big.jpg"}]}}},
        "flexColumns":[
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$subtitle"}]}}}],
        "navigationEndpoint":{"browseEndpoint":{"browseId":"$id"}}}}"""

    private fun search(vararg items: String) =
        json.decodeFromString<SearchResponse>(
            """{"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":
            {"contents":[{"musicShelfRenderer":{"contents":[${items.joinToString(",")}]}}]}}}}]}}}""",
        )

    @Test
    fun `playlists become cards with their page id, name, creator and largest cover`() {
        val hits =
            search(
                item("VLPL3oW2tjiIxvTaC6caIGR55W3ssqGvb_LR", "Latest Tamil Hits 2026", "Redlist • 126M views"),
                item("VLPLRMJIvNrS", "Tamil kuthu songs", "Girish Kumar • 5.2M views"),
            ).toPlaylistHits()

        assertThat(hits.map { it.browseId }).containsExactly("VLPL3oW2tjiIxvTaC6caIGR55W3ssqGvb_LR", "VLPLRMJIvNrS")
        assertThat(hits[0].title).isEqualTo("Latest Tamil Hits 2026")
        assertThat(hits[0].subtitle).isEqualTo("Redlist • 126M views")
        assertThat(hits[0].artworkUrl).isEqualTo("big.jpg")
        assertThat(hits[0].kind).isEqualTo(CollectionKind.PLAYLIST)
    }

    @Test
    fun `items that aren't playlist pages and repeats are left out`() {
        val hits =
            search(
                item("MPREb_album", "An album", "Artist"),
                item("VLPL1", "Mix", "Someone"),
                item("VLPL1", "Mix", "Someone"),
            ).toPlaylistHits()

        assertThat(hits.map { it.browseId }).containsExactly("VLPL1")
    }
}
