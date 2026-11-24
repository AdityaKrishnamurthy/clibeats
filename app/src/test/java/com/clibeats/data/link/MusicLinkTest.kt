package com.clibeats.data.link

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicLinkTest {
    @Test
    fun youTubeMusicAndYouTubeSongLinks() {
        val song = MusicLink.YouTubeSong("YBOUD_JKorg")
        assertThat(MusicLink.parse("https://music.youtube.com/watch?v=YBOUD_JKorg&si=abc")).isEqualTo(song)
        assertThat(MusicLink.parse("https://www.youtube.com/watch?v=YBOUD_JKorg")).isEqualTo(song)
        assertThat(MusicLink.parse("https://m.youtube.com/watch?feature=share&v=YBOUD_JKorg")).isEqualTo(song)
        assertThat(MusicLink.parse("https://youtu.be/YBOUD_JKorg?t=30")).isEqualTo(song)
        assertThat(MusicLink.parse("https://youtube.com/shorts/YBOUD_JKorg")).isEqualTo(song)
        // A song played from a playlist is the song.
        assertThat(MusicLink.parse("https://music.youtube.com/watch?v=YBOUD_JKorg&list=PLabc")).isEqualTo(song)
    }

    @Test
    fun youTubePlaylistsAndAlbums() {
        assertThat(MusicLink.parse("https://music.youtube.com/playlist?list=PL4fGSI1pDJn6puJ"))
            .isEqualTo(MusicLink.YouTubePage("VLPL4fGSI1pDJn6puJ"))
        assertThat(MusicLink.parse("https://music.youtube.com/browse/MPREb_3JuW517soDD"))
            .isEqualTo(MusicLink.YouTubePage("MPREb_3JuW517soDD"))
    }

    @Test
    fun spotifyLinks() {
        assertThat(MusicLink.parse("https://open.spotify.com/track/0OGw7V8wAI6OsZIQ12JkTP?si=x"))
            .isEqualTo(MusicLink.SpotifyTrack("0OGw7V8wAI6OsZIQ12JkTP"))
        assertThat(MusicLink.parse("https://open.spotify.com/intl-de/album/0OGw7V8wAI6OsZIQ12JkTP"))
            .isEqualTo(MusicLink.SpotifyCollection("album", "0OGw7V8wAI6OsZIQ12JkTP"))
        assertThat(MusicLink.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
            .isEqualTo(MusicLink.SpotifyCollection("playlist", "37i9dQZF1DXcBWIGoYBM5M"))
        val short = "https://spotify.link/AbCdEf"
        assertThat(MusicLink.parse(short)).isEqualTo(MusicLink.SpotifyShort(short))
    }

    @Test
    fun otherPages_areNotSongLinks() {
        assertThat(MusicLink.parse("https://open.spotify.com/artist/0OGw7V8wAI6OsZIQ12JkTP")).isNull()
        assertThat(MusicLink.parse("https://www.youtube.com/@SomeChannel")).isNull()
        assertThat(MusicLink.parse("https://example.com/watch?v=YBOUD_JKorg")).isNull()
        assertThat(MusicLink.parse("not a link")).isNull()
    }

    @Test
    fun sharedText_findsTheLinkInIt() {
        val text = "Listen to Night Sky by Lumen: https://open.spotify.com/track/0OGw7V8wAI6OsZIQ12JkTP."
        // The full stop after the link isn't a valid id character, but the id is still read.
        assertThat(MusicLink.findIn(text)).isEqualTo(MusicLink.SpotifyTrack("0OGw7V8wAI6OsZIQ12JkTP"))
    }
}
