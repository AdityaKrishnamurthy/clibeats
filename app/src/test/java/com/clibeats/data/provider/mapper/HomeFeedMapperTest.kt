@file:Suppress("ArgumentListWrapping") // JSON fixtures built from string templates

package com.clibeats.data.provider.mapper

import com.clibeats.domain.model.CollectionKind
import com.clibeats.domain.model.HomeItem
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** Shapes taken from YouTube Music's real home feed (FEmusic_home), trimmed to what the parser reads. */
class HomeFeedMapperTest {
    private fun runs(vararg texts: String) = texts.joinToString(",", "{\"runs\":[", "]}") { "{\"text\":\"$it\"}" }

    private fun card(
        title: String,
        subtitle: List<String>,
        endpoint: String,
    ) = """
        {"musicTwoRowItemRenderer":{
          "title":${runs(title)},
          "subtitle":${runs(*subtitle.toTypedArray())},
          "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://img/small"},{"url":"https://img/large"}]}}},
          "navigationEndpoint":$endpoint}}
        """

    private fun browse(
        id: String,
        pageType: String,
    ) = """{"browseEndpoint":{"browseId":"$id","browseEndpointContextSupportedConfigs":
        {"browseEndpointContextMusicConfig":{"pageType":"$pageType"}}}}"""

    private val song =
        """
        {"musicResponsiveListItemRenderer":{
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":${runs("Gehra Hua")}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":${runs("Arijit Singh", " • ", "Dhurandhar")}}}],
          "playlistItemData":{"videoId":"i1o1p_DD6TU"}}}
        """

    private val feed =
        Json.parseToJsonElement(
            """
            {"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{
              "header":{"chipCloudRenderer":{"chips":[
                {"chipCloudChipRenderer":{"text":${runs("Podcasts")},
                  "navigationEndpoint":{"browseEndpoint":{"browseId":"FEmusic_home","params":"pod"}}}},
                {"chipCloudChipRenderer":{"text":${runs("Relax")},
                  "navigationEndpoint":{"browseEndpoint":{"browseId":"FEmusic_home","params":"relax"}}}}]}},
              "contents":[
                {"musicCarouselShelfRenderer":{
                  "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":${runs("Quick picks")}}},
                  "contents":[$song]}},
                {"musicCarouselShelfRenderer":{
                  "header":{"musicCarouselShelfBasicHeaderRenderer":{
                    "title":${runs("Easy Evenings")},"strapline":${runs("COMFY & COZY")}}},
                  "contents":[
                    ${card("Kannada Melodies", listOf("Sonu Nigam"), browse("VLRDCLAK5uy_x", "MUSIC_PAGE_TYPE_PLAYLIST"))},
                    ${card("Jailer 2", listOf("Album", " • ", "Anirudh"), browse("MPREb_abc", "MUSIC_PAGE_TYPE_ALBUM"))},
                    ${card("Some Show", listOf("Podcast"), browse("MPSPx", "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"))},
                    ${card("A Video", listOf("Song", " • ", "Singer", " • ", "3:21"), "{\"watchEndpoint\":{\"videoId\":\"vid1\"}}")}]}},
                {"musicCarouselShelfRenderer":{
                  "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":${runs("Podcasts only")}}},
                  "contents":[${card("Show", listOf("Podcast"), browse("MPSPy", "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"))}]}}
              ],
              "continuations":[{"nextContinuationData":{"continuation":"TOKEN%3D"}}]
            }}}}]}}
            """,
        )

    @Test
    fun `reads every row with its songs and cards, skipping what can't be opened`() {
        val shelves = feed.homeShelves()

        assertThat(shelves.map { it.title }).containsExactly("Quick picks", "Easy Evenings").inOrder()
        val quick = shelves[0]
        assertThat(quick.songs.single().id).isEqualTo("i1o1p_DD6TU")
        assertThat(quick.songs.single().artist).isEqualTo("Arijit Singh")

        val evenings = shelves[1]
        assertThat(evenings.strapline).isEqualTo("COMFY & COZY")
        val (playlist, album, video) = evenings.items
        playlist as HomeItem.Collection
        assertThat(playlist.browseId).isEqualTo("VLRDCLAK5uy_x")
        assertThat(playlist.kind).isEqualTo(CollectionKind.PLAYLIST)
        assertThat(playlist.artworkUrl).isEqualTo("https://img/large")
        assertThat((album as HomeItem.Collection).kind).isEqualTo(CollectionKind.ALBUM)
        video as HomeItem.Song
        assertThat(video.track.id).isEqualTo("vid1")
        assertThat(video.track.artist).isEqualTo("Singer")
        assertThat(video.track.durationMs).isEqualTo(201_000L)
        assertThat(evenings.items).hasSize(3)
    }

    @Test
    fun `mood chips leave out Podcasts`() {
        val moods = feed.homeMoods()
        assertThat(moods.map { it.title }).containsExactly("Relax")
        assertThat(moods.single().params).isEqualTo("relax")
    }

    @Test
    fun `continuation token is kept as sent`() {
        assertThat(feed.feedContinuation()).isEqualTo("TOKEN%3D")
    }
}
