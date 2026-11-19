package com.clibeats.data.playlist

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CliBeatsFileCodecTest {
    @Test
    fun `encode then decode roundtrips`() {
        val file =
            CliBeatsFile(
                version = 1,
                playlists =
                    listOf(
                        CliBeatsPlaylist(
                            name = "Road Trip",
                            tracks =
                                listOf(
                                    CliBeatsTrack(
                                        providerId = "internet_archive",
                                        sourceId = "wonderwall-1995",
                                        title = "Wonderwall",
                                        artist = "Oasis",
                                        album = "Morning Glory",
                                        durationMs = 258_000L,
                                        artworkUrl = "https://archive.org/services/img/wonderwall-1995",
                                        sourceUrl = "https://archive.org/download/wonderwall-1995/Wonderwall.mp3",
                                    ),
                                ),
                        ),
                    ),
            )

        val decoded = CliBeatsFileCodec.decode(CliBeatsFileCodec.encode(file))

        assertThat(decoded.version).isEqualTo(1)
        assertThat(decoded.playlists).hasSize(1)
        val track = decoded.playlists[0].tracks[0]
        assertThat(track.providerId).isEqualTo("internet_archive")
        assertThat(track.sourceId).isEqualTo("wonderwall-1995")
        assertThat(track.title).isEqualTo("Wonderwall")
        assertThat(track.artist).isEqualTo("Oasis")
        assertThat(track.durationMs).isEqualTo(258_000L)
    }

    @Test
    fun `v1 files still decode with the v2 fields defaulted`() {
        val raw =
            """
            {"version":1,"playlists":[{"name":"P","tracks":[{"providerId":"audius","sourceId":"abc",
            "title":"T","artist":"A","album":"","durationMs":0}]}]}
            """.trimIndent()

        val decoded = CliBeatsFileCodec.decode(raw)

        assertThat(decoded.version).isEqualTo(1)
        assertThat(decoded.prefs).isNull()
        assertThat(decoded.albums).isEmpty()
        assertThat(decoded.artists).isEmpty()
    }

    @Test
    fun `v2 roundtrips prefs, albums and artists`() {
        val file =
            CliBeatsFile(
                prefs = CliBeatsPrefs(themeMode = "DARK", accentColorName = "GREEN", onboardingDone = true),
                albums = listOf(CliBeatsAlbum("a1", "Album", "Artist", 2024, null, 9, "audius")),
                artists = listOf(CliBeatsArtist("r1", "Artist", null, "audius")),
            )

        val decoded = CliBeatsFileCodec.decode(CliBeatsFileCodec.encode(file))

        assertThat(decoded.version).isEqualTo(CliBeatsFile.CURRENT_VERSION)
        assertThat(decoded.prefs?.onboardingDone).isTrue()
        assertThat(decoded.albums.single().trackCount).isEqualTo(9)
        assertThat(decoded.artists.single().name).isEqualTo("Artist")
    }

    @Test
    fun `decode ignores unknown keys`() {
        val raw =
            """
            {"version":1,"future_field":true,"playlists":[{"name":"P","tracks":[{"providerId":"audius","sourceId":"abc","title":"T","artist":"A","album":"","durationMs":0,"extra":"x"}]}]}
            """.trimIndent()

        val decoded = CliBeatsFileCodec.decode(raw)

        assertThat(decoded.playlists[0].tracks[0].title).isEqualTo("T")
    }
}
