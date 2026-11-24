package com.clibeats.presentation.playlist

import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SongSortTest {
    private fun t(
        title: String,
        artist: String,
    ) = Track("$title-$artist", title, artist, "", 1_000, null, null, "youtube_music")

    private val songs =
        listOf(
            t("breeze", "Hikari"),
            t("Above", "YUKI"),
            t("Ásh", "adler"),
            t("Coral", "Adler"),
        )

    @Test
    fun title_ignoresCaseAndAccents() {
        assertThat(sortSongs(songs, SongSort.TITLE_ASC).map { it.title })
            .containsExactly("Above", "Ásh", "breeze", "Coral").inOrder()
        assertThat(sortSongs(songs, SongSort.TITLE_DESC).map { it.title })
            .containsExactly("Coral", "breeze", "Ásh", "Above").inOrder()
    }

    @Test
    fun artist_keepsTitleOrderWithinTheSameArtist() {
        assertThat(sortSongs(songs, SongSort.ARTIST_ASC).map { it.title })
            .containsExactly("Ásh", "Coral", "breeze", "Above").inOrder()
        assertThat(sortSongs(songs, SongSort.ARTIST_DESC).map { it.artist })
            .containsExactly("YUKI", "Hikari", "Adler", "adler").inOrder()
    }
}
