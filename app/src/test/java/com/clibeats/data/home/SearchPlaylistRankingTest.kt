package com.clibeats.data.home

import com.clibeats.domain.model.CollectionKind
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeShelf
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchPlaylistRankingTest {
    private fun playlist(
        id: String,
        title: String,
        subtitle: String = "",
        kind: CollectionKind = CollectionKind.PLAYLIST,
    ) = HomeItem.Collection(id, title, subtitle, null, kind)

    @Test
    fun `home playlists matching the search come first, marked, without repeats`() {
        val found = listOf(playlist("VL1", "Tamil Hits 2026", "Redlist"), playlist("VL2", "Tamil kuthu"))
        val home =
            listOf(
                playlist("spotify:in-ta", "Top 50 – Tamil", "Spotify"),
                playlist("VL2", "Tamil kuthu", "Girish"),
                playlist("VL9", "Workout"),
            )

        val ranked = rankPlaylistsWithHome("tamil", found, home)

        assertThat(ranked.map { it.browseId }).containsExactly("spotify:in-ta", "VL2", "VL1").inOrder()
        assertThat(ranked[0].subtitle).isEqualTo("$ON_HOME · Spotify")
        assertThat(ranked[2].subtitle).isEqualTo("Redlist")
    }

    @Test
    fun `every word of the search must match a home playlist`() {
        val home = listOf(playlist("a", "Top 50 – India"), playlist("b", "Top 50 – Global"))
        assertThat(rankPlaylistsWithHome("top 50 india", emptyList(), home).map { it.browseId }).containsExactly("a")
        assertThat(rankPlaylistsWithHome("x", emptyList(), home)).isEmpty()
    }

    @Test
    fun `only playlists are taken from home rows`() {
        val shelves =
            listOf(
                HomeShelf(
                    "Row",
                    "",
                    listOf(playlist("p", "Mix"), playlist("al", "Album", kind = CollectionKind.ALBUM)),
                ),
            )
        assertThat(homePlaylists(shelves).map { it.browseId }).containsExactly("p")
    }
}
