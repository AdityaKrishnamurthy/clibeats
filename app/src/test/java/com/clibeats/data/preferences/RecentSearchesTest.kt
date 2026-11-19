package com.clibeats.data.preferences

import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentSearchesTest {
    private fun track(id: String) = Track(id, "Song $id", "Artist", "Album", 1_000L, null, null, "youtube_music")

    @Test
    fun `newest pick comes first and an older copy is removed`() {
        val list = RecentSearches.add(listOf(track("a"), track("b")), track("b"))
        assertThat(list.map { it.id }).containsExactly("b", "a").inOrder()
    }

    @Test
    fun `list keeps at most the limit`() {
        var list = emptyList<Track>()
        repeat(RecentSearches.MAX_TRACKS + 5) { list = RecentSearches.add(list, track("t$it")) }
        assertThat(list).hasSize(RecentSearches.MAX_TRACKS)
        assertThat(list.first().id).isEqualTo("t${RecentSearches.MAX_TRACKS + 4}")
    }

    @Test
    fun `tracks survive encode and decode, broken data gives an empty list`() {
        val tracks = listOf(track("a"), track("b"))
        assertThat(RecentSearches.decode(RecentSearches.encode(tracks))).isEqualTo(tracks)
        assertThat(RecentSearches.decode("{broken")).isEmpty()
        assertThat(RecentSearches.decode(null)).isEmpty()
    }
}
