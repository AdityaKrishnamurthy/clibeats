@file:Suppress("ForbiddenImport")

package com.clibeats.data.provider.mapper

import com.clibeats.data.provider.dto.BrowseResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** Shapes taken from real YouTube Music responses (Charts page for India, and a chart playlist). */
class ChartsMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun response(contents: String) = json.decodeFromString<BrowseResponse>("""{"contents":$contents}""")

    private fun chartItem(
        title: String,
        id: String,
    ) = """{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"$title"}]},
        "navigationEndpoint":{"browseEndpoint":{"browseId":"$id"}}}}"""

    private fun charts(vararg items: String) =
        response(
            """{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":
            {"contents":[{"musicCarouselShelfRenderer":{"contents":[${items.joinToString(",")}]}}]}}}}]}}""",
        )

    @Test
    fun `daily top music videos is preferred over trending`() {
        val page =
            charts(
                chartItem("Trending 20 India", "VLOLAK5uy_trending"),
                chartItem("Daily Top Music Videos - India", "VLPL_daily"),
                chartItem("Top 100 Music Videos India", "VLPL_top100"),
            )
        assertThat(page.chartPlaylistBrowseId()).isEqualTo("VLPL_daily")
    }

    @Test
    fun `falls back to the top 100, then the first chart`() {
        assertThat(
            charts(chartItem("Trending 20 India", "VLT"), chartItem("Top 100 Music Videos India", "VL100"))
                .chartPlaylistBrowseId(),
        ).isEqualTo("VL100")
        assertThat(charts(chartItem("Some Chart", "VLX")).chartPlaylistBrowseId()).isEqualTo("VLX")
        assertThat(charts().chartPlaylistBrowseId()).isNull()
    }

    @Test
    fun `playlist items become tracks with the duration from the fixed column`() {
        val page =
            response(
                """{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{"contents":[
                {"musicPlaylistShelfRenderer":{"contents":[
                  {"musicResponsiveListItemRenderer":{
                    "flexColumns":[
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Guzarish"}]}}},
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Satinder Sartaaj"}]}}}],
                    "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"4:05"}]}}}],
                    "playlistItemData":{"videoId":"abc123"},
                    "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"small"},{"url":"big"}]}}}}},
                  {"musicResponsiveListItemRenderer":{
                    "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"No id"}]}}}]}}
                ]}}]}}}}""",
            )
        val tracks = page.playlistTracks()

        assertThat(tracks).hasSize(1)
        with(tracks[0]) {
            assertThat(id).isEqualTo("abc123")
            assertThat(title).isEqualTo("Guzarish")
            assertThat(artist).isEqualTo("Satinder Sartaaj")
            assertThat(durationMs).isEqualTo(245_000L)
            assertThat(artworkUrl).isEqualTo("big")
        }
    }
}
