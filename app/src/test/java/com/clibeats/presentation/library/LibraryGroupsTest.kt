package com.clibeats.presentation.library

import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryGroupsTest {
    private fun song(
        id: String,
        artist: String,
        album: String = "",
        art: String? = "art_$id",
    ) = Track(id, "Song $id", artist, album, 180_000L, art, null, "youtube_music")

    @Test
    fun `group credits are split into their artists`() {
        assertThat(splitArtists("A.R. Rahman, Ravi Basrur & Karthik"))
            .containsExactly("A.R. Rahman", "Ravi Basrur", "Karthik").inOrder()
        assertThat(splitArtists("Justice feat. Tame Impala")).containsExactly("Justice", "Tame Impala").inOrder()
        assertThat(splitArtists("Song Artist (feat. Guest)")).containsExactly("Song Artist", "Guest").inOrder()
        assertThat(splitArtists("Artist A; Artist B")).containsExactly("Artist A", "Artist B").inOrder()
    }

    @Test
    fun `names with a plain slash or 'and' stay whole, blank becomes Unknown Artist`() {
        assertThat(splitArtists("AC/DC")).containsExactly("AC/DC")
        assertThat(splitArtists("Florence and the Machine")).containsExactly("Florence and the Machine")
        assertThat(splitArtists("  ")).containsExactly(UNKNOWN_ARTIST)
    }

    @Test
    fun `a song counts for every artist on it, most played songs first`() {
        val tracks =
            listOf(
                song("1", "Armaan Malik"),
                song("2", "Armaan Malik & Shreya Ghoshal"),
                song("3", "shreya ghoshal"),
            )
        val plays = mapOf("1" to 2, "2" to 5, "3" to 1)
        val artists = buildArtists(tracks, plays, GroupSort.MOST_PLAYED)

        assertThat(artists.map { it.name }).containsExactly("Armaan Malik", "Shreya Ghoshal").inOrder()
        val armaan = artists.first()
        assertThat(armaan.trackCount).isEqualTo(2)
        assertThat(armaan.playCount).isEqualTo(7)
        assertThat(armaan.tracks.map { it.id }).containsExactly("2", "1").inOrder()
        assertThat(artists[1].trackCount).isEqualTo(2) // "Shreya Ghoshal" and "shreya ghoshal" are one artist
    }

    @Test
    fun `sorting by name and by song count`() {
        val tracks = listOf(song("1", "Zed"), song("2", "Abe"), song("3", "Abe"))
        assertThat(buildArtists(tracks, emptyMap(), GroupSort.A_TO_Z).map { it.name })
            .containsExactly("Abe", "Zed").inOrder()
        assertThat(buildArtists(tracks, mapOf("1" to 9), GroupSort.MOST_SONGS).map { it.name })
            .containsExactly("Abe", "Zed").inOrder()
        assertThat(buildArtists(tracks, mapOf("1" to 9), GroupSort.MOST_PLAYED).map { it.name })
            .containsExactly("Zed", "Abe").inOrder()
    }

    @Test
    fun `albums group by title, keep soundtracks whole, and skip songs without an album`() {
        val tracks =
            listOf(
                song("1", "Singer A", "Hebbuli"),
                song("2", "Singer B", "Hebbuli"),
                song("3", "Band X", "Greatest Hits"),
                song("4", "Band X", "Greatest Hits"),
                song("5", "Band Y", "Greatest Hits"),
                song("6", "Band Y", "Greatest Hits"),
                song("7", "Loner", ""),
            )
        val albums = buildAlbums(tracks, emptyMap(), GroupSort.A_TO_Z)

        assertThat(albums.map { it.title to it.trackCount })
            .containsExactly("Greatest Hits" to 2, "Greatest Hits" to 2, "Hebbuli" to 2)
        assertThat(albums.map { it.key }.toSet()).hasSize(3)
        assertThat(albums.first { it.title == "Hebbuli" }.artworkUrl).isEqualTo("art_1")
    }

    @Test
    fun `stats label leaves plays out while there are none`() {
        assertThat(statsLabel(1, 0)).isEqualTo("1 song")
        assertThat(statsLabel(12, 1)).isEqualTo("12 songs · 1 play")
        assertThat(statsLabel(12, 48)).isEqualTo("12 songs · 48 plays")
    }
}
