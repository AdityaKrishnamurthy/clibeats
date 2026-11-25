package com.clibeats.presentation.playlist

import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistSearchTest {
    private fun track(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String = "",
    ) = Track(id, title, artist, album, 200_000L, null, null, "youtube_music")

    private val songs =
        listOf(
            track("1", "Beyoncé Medley", artist = "Cover Band"),
            track("2", "Night Drive", artist = "Synth Artist", album = "Midnight"),
            track("3", "ಬೆಳಗೆದ್ದು", artist = "Kannada Singer"),
        )

    @Test
    fun `blank query keeps every song in order`() {
        assertThat(filterSongs(songs, "  ")).isEqualTo(songs)
    }

    @Test
    fun `matches title, artist or album, ignoring case and accents`() {
        assertThat(filterSongs(songs, "beyonce").map { it.id }).containsExactly("1")
        assertThat(filterSongs(songs, "SYNTH").map { it.id }).containsExactly("2")
        assertThat(filterSongs(songs, "midnight").map { it.id }).containsExactly("2")
        assertThat(filterSongs(songs, "ಬೆಳ").map { it.id }).containsExactly("3")
    }

    @Test
    fun `every word must match, in any field`() {
        assertThat(filterSongs(songs, "night synth").map { it.id }).containsExactly("2")
        assertThat(filterSongs(songs, "night kannada")).isEmpty()
    }
}
