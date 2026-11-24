@file:Suppress("ForbiddenImport")

package com.clibeats.data.link

import com.clibeats.data.importer.SpotifySong
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpotifyEmbedTest {
    private fun page(entity: String) =
        "<html><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
            "{\"props\":{\"pageProps\":{\"state\":{\"data\":{\"entity\":$entity}}}}}</script></html>"

    @Test
    fun track() {
        val html =
            page(
                """{"type":"track","name":"Night Sky","artists":[{"name":"Lumen"},{"name":"Ray"}],"duration":209573}""",
            )

        val playlist = SpotifyEmbed.parse(html)

        assertThat(playlist?.name).isEqualTo("Night Sky")
        assertThat(playlist?.songs).containsExactly(SpotifySong("Night Sky", listOf("Lumen", "Ray"), "", 209_573))
    }

    @Test
    fun playlist_splitsTheArtistLine() {
        val html =
            page(
                """{"type":"playlist","name":"Mix","trackList":[""" +
                    """{"title":"Song A","subtitle":"Singer, Chorus","duration":1000},""" +
                    """{"title":"Song B","subtitle":"Solo","duration":2000}]}""",
            )

        val playlist = SpotifyEmbed.parse(html)!!

        assertThat(playlist.name).isEqualTo("Mix")
        assertThat(playlist.songs.map { it.artists })
            .containsExactly(listOf("Singer", "Chorus"), listOf("Solo"))
            .inOrder()
        assertThat(playlist.songs[0].album).isEmpty()
    }

    @Test
    fun album_songsCarryTheAlbumName() {
        val html =
            page("""{"type":"album","name":"Big Album","trackList":[{"title":"A","subtitle":"Band","duration":1}]}""")

        assertThat(SpotifyEmbed.parse(html)!!.songs.single().album).isEqualTo("Big Album")
    }

    @Test
    fun pageWithoutData_isNull() {
        assertThat(SpotifyEmbed.parse("<html>Page not found</html>")).isNull()
    }
}
