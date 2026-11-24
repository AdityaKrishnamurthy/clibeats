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

    // The cases below follow real import misses (names changed).

    @Test
    fun sameTitleAndLength_butAnotherLatinArtist_isNotMatched() {
        val s = song("Lights", listOf("Arden", "Mae"), 196_000)

        assertThat(TrackMatcher.pick(s, listOf(yt("w", "Lights", "Corvo", 197_000)))).isNull()
    }

    @Test
    fun remixOnSpotify_doesNotTakeTheOriginal() {
        val s = song("Nadiya Hoovu (Remix)", listOf("K. Mohan"), 256_000)
        val original = yt("o", "Nadiya Hoovu", "K. Mohan", 257_000)

        assertThat(TrackMatcher.pick(s, listOf(original))).isNull()
    }

    @Test
    fun plainTitle_prefersTheOriginalOverATwinVersion() {
        val s = song("Coral", listOf("AURA"), 174_000)
        val twin = yt("t", "Coral (Twin Version)", "AURA", 174_000)
        val original = yt("o", "Coral", "AURA", 175_000)

        assertThat(TrackMatcher.pick(s, listOf(twin, original))?.id).isEqualTo("o")
    }

    @Test
    fun japaneseTvSize_matchesEnglishTvSize() {
        val s = song("sunrise! (TVサイズ)", listOf("Ken Ito"), 90_000)

        assertThat(TrackMatcher.pick(s, listOf(yt("d", "sunrise! (TV Size)", "Ken Ito", 90_500)))?.id)
            .isEqualTo("d")
    }

    @Test
    fun kannadaSong_neverTakesTheHindiDub() {
        val s =
            song(
                "Raajana",
                listOf("Hari Venu", "Mohan Das", "Ravi Ram"),
                225_000,
                album = "Film Four [KANNADA]",
            )
        // Real results: the Hindi "Raajan" shares a credit and the length.
        val hindiNoLabel = yt("h", "Raajan", "Vikas Shah & Mohan Das", 226_000)
        val hindiLabelled = yt("h2", "Raajana", "Hari Venu", 225_000).copy(album = "Film Four (Hindi)")
        val kannada = yt("k", "Raajana", "Hari Venu & Mohan Das", 225_500).copy(album = "Film Four")

        assertThat(TrackMatcher.pick(s, listOf(hindiNoLabel, hindiLabelled))).isNull()
        assertThat(TrackMatcher.pick(s, listOf(hindiNoLabel, hindiLabelled, kannada))?.id).isEqualTo("k")
    }

    @Test
    fun filmSong_creditedToAnotherSinger_matchesOnTheSameFilm() {
        val s =
            song(
                "Ninna Nage",
                listOf("S. Bhat", "A. Jay"),
                252_000,
                album = "Film Five (Original Motion Picture Soundtrack)",
            )

        val sameFilm = yt("v", "Ninna Nage", "Kiran Kher", 253_000).copy(album = "Film Five")
        val otherFilm = yt("o", "Ninna Nage", "Kiran Kher", 253_000).copy(album = "Something Else")
        assertThat(TrackMatcher.pick(s, listOf(otherFilm))).isNull()
        assertThat(TrackMatcher.pick(s, listOf(otherFilm, sameFilm))?.id).isEqualTo("v")
    }

    @Test
    fun artistLineWithSeveralNames_matchesAnyOfThem() {
        val s = song("Amma Nanna", listOf("Pran's", "A. Jay"), 107_000)

        assertThat(TrackMatcher.pick(s, listOf(yt("a", "Amma Nanna", "Pran Jogi & A. Jay", 108_000)))?.id)
            .isEqualTo("a")
    }

    @Test
    fun sameSingerSameLength_butAnotherEnglishLetterTitle_isNotMatched() {
        val s = song("Elli Hadu Elli", listOf("S. P. Venkataraman"), 285_000)
        val other = yt("x", "Kanasu Ondu Bere", "S.P.Venkatraman, V. Chandra", 283_000)

        assertThat(TrackMatcher.pick(s, listOf(other))).isNull()
    }

    @Test
    fun roughlySimilarTitle_needsTheSameLength() {
        val s = song("Raajana", listOf("Hari Venu", "Asha Rao"), 225_000)
        val chapterOneSong = yt("c1", "Rajan", "Asha Rao, Mohan Das, Hari Venu", 218_000)

        assertThat(TrackMatcher.pick(s, listOf(chapterOneSong))).isNull()
    }

    @Test
    fun spotifyDuoName_matchesYouTubesSplitCredits() {
        val s = song("一生懸命", listOf("MIKA with SunBand"), 236_626)

        assertThat(TrackMatcher.pick(s, listOf(yt("g", "一生懸命 - Isshokenmei", "MIKA & SunBand", 237_000)))?.id)
            .isEqualTo("g")
    }

    @Test
    fun songWithoutAudioRelease_takesTheLabelsVideoInTheSameLanguage() {
        val s = song("Raajana", listOf("Hari Venu", "Mohan Das"), 225_037, album = "Film Four [KANNADA]")
        val lyricalKannada =
            yt("k", "Raajana Lyrical(Kannada) | Film Four | Star Cast", "Label Kannada", 226_000)
        val hindiDub = yt("h", "Raajan", "Vikas Shah & Mohan Das", 226_000)

        assertThat(TrackMatcher.pick(s, listOf(hindiDub, lyricalKannada))?.id).isEqualTo("k")
    }

    @Test
    fun closestVersion_onlyWhenAllowed() {
        val s = song("Nadiya Hoovu (Remix)", listOf("K. Mohan"), 256_932)
        val original = yt("o", "Nadiya Hoovu", "K. Mohan", 257_000)

        assertThat(TrackMatcher.pick(s, listOf(original))).isNull()
        assertThat(TrackMatcher.pick(s, listOf(original), allowClosest = true)?.id).isEqualTo("o")
        // Never the other way round: a remix doesn't stand in for the original.
        val plain = song("Nadiya Hoovu", listOf("K. Mohan"), 256_932)
        val remix = yt("r", "Nadiya Hoovu (Remix)", "K. Mohan", 257_000)
        assertThat(TrackMatcher.pick(plain, listOf(remix), allowClosest = true)).isNull()
    }

    @Test
    fun remixLabelOnlyOnSpotify_matchesTheSameAlbumTrackAtTheSameLength() {
        val s =
            song(
                "Nadiya Hoovu (Remix)",
                listOf("K. Mohan"),
                256_932,
                album = "Film One (Original Motion Picture Soundtrack)",
            )
        val sameTrack = yt("a", "Nadiya Hoovu", "S. Prakash", 257_000).copy(album = "Film One")
        val otherAlbum =
            yt("b", "Nadiya Hovu", "S. Prakash", 257_000).copy(album = "Film Three")

        assertThat(TrackMatcher.pick(s, listOf(otherAlbum, sameTrack))?.id).isEqualTo("a")
        assertThat(TrackMatcher.pick(s, listOf(otherAlbum))).isNull()
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
