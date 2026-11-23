package com.clibeats.data.importer

import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackMatcherTest {
    private fun yt(
        id: String,
        title: String,
        artist: String,
        durationMs: Long,
    ) = Track(id, title, artist, "", durationMs, null, null, "youtube_music")

    private fun song(
        title: String,
        artists: List<String>,
        durationMs: Long,
        album: String = "",
    ) = SpotifySong(title, artists, album, durationMs)

    @Test
    fun picksSameTitleArtistAndLength_overALongLoopVideo() {
        val s = song("Nadi Tara", listOf("Ravi Kumar", "Anil Rao"), 321_769)
        val loop = yt("loop", "Nadi Tara", "Ravi Kumar", 3_600_000)
        val right = yt("ok", "Nadi Tara (From \"Film Two\")", "Ravi Kumar", 322_000)

        assertThat(TrackMatcher.pick(s, listOf(loop, right))?.id).isEqualTo("ok")
    }

    @Test
    fun titleInAnotherScript_matchesOnArtistAndExactLength() {
        val s = song("星空花", listOf("MIRA"), 238_586, album = "Hoshizora")

        assertThat(TrackMatcher.pick(s, listOf(yt("g", "Hoshizora", "MIRA", 239_000)))?.id).isEqualTo("g")
    }

    @Test
    fun rejectsKaraokeAndRemixUnlessTheSpotifyTitleSaysSo() {
        val s = song("Kazenouta", listOf("Adler"), 184_898)
        val karaoke = yt("k", "Kazenouta (Karaoke)", "Adler", 185_000)
        val remix = yt("r", "Kazenouta Remix", "Adler", 185_000)

        assertThat(TrackMatcher.pick(s, listOf(karaoke, remix))).isNull()
        val wanted = song("Kazenouta Remix", listOf("Adler"), 184_898)
        assertThat(TrackMatcher.pick(wanted, listOf(remix))?.id).isEqualTo("r")
    }

    @Test
    fun differentSongBySameArtist_isNotMatched() {
        val s = song("Yume Kairo", listOf("Rin"), 221_000)

        assertThat(TrackMatcher.pick(s, listOf(yt("x", "Hanabi", "Rin", 250_000)))).isNull()
    }

    @Test
    fun sameTitleSameLength_matchesEvenWhenTheArtistIsSpelledDifferently() {
        val s = song("Ame", listOf("山田太郎"), 255_000)

        assertThat(TrackMatcher.pick(s, listOf(yt("l", "Ame", "Taro Yamada", 256_000)))?.id).isEqualTo("l")
    }

    @Test
    fun noResults_noMatch() {
        assertThat(TrackMatcher.pick(song("A", listOf("B"), 1_000), emptyList())).isNull()
    }

    @Test
    fun uniqueName_addsANumberWhenTaken() {
        assertThat(PlaylistImporter.uniqueName("Nihon", setOf("Nihon", "Nihon (2)"))).isEqualTo("Nihon (3)")
        assertThat(PlaylistImporter.uniqueName("Nihon", emptySet())).isEqualTo("Nihon")
    }
}
