@file:Suppress(
    "MaxLineLength",
    "MaximumLineLength",
    "ArgumentListWrapping",
    "PropertyWrapping",
    "ForbiddenImport",
    "ktlint:standard:property-wrapping",
    "ktlint:standard:max-line-length",
    "ktlint:standard:argument-list-wrapping",
)

package com.clibeats.data.lyrics.online

import com.clibeats.data.lyrics.needsPronunciation
import com.clibeats.data.lyrics.sameText
import com.clibeats.domain.model.Lyrics
import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Shapes copied from real answers (LyricsPlus "Backlight", BetterLyrics / LRCLIB "Shape of You"). */
class OnlineLyricsParsersTest {
    @Test
    fun `LyricsPlus Apple syllables with translation and pronunciation`() {
        val body =
            """
            {"type":"Word","metadata":{"source":"Apple"},"lyrics":[
              {"time":27057,"duration":5317,"text":"散々な思い出","element":{"key":"L1"},
               "translation":{"lang":"en-US","text":"The terrible memories"},
               "transliteration":{"lang":"ja-Latn","text":"sanzan na omoide"},
               "syllabus":[{"time":27057,"duration":552,"text":"散"},{"time":27609,"duration":386,"text":"々"},
                           {"time":27995,"duration":322,"text":"な"},{"time":28317,"duration":672,"text":"思い出"}]}
            ]}
            """.trimIndent()
        val lyrics = parseLyricsPlus(body) as Lyrics.Synced
        val line = lyrics.lines.single()
        assertThat(lyrics.source).isEqualTo("LyricsPlus · Apple")
        assertThat(lyrics.hasWordTiming).isTrue()
        assertThat(line.text).isEqualTo("散々な思い出")
        assertThat(line.words.map { it.text }).containsExactly("散", "々", "な", "思い出").inOrder()
        assertThat(line.words[3].endMs).isEqualTo(28_989)
        assertThat(line.translation).isEqualTo("The terrible memories")
        assertThat(line.pronunciation).isEqualTo("sanzan na omoide")
        assertThat(rank(lyrics)).isEqualTo(RANK_APPLE_SYLLABLE)
    }

    @Test
    fun `LyricsPlus QQ credits are dropped and space syllables join the word before`() {
        val body =
            """
            {"type":"Word","metadata":{"source":"QQ Music"},"lyrics":[
              {"time":0,"duration":1500,"text":"Shape of You - Ed Sheeran","syllabus":[{"time":0,"duration":1500,"text":"Shape of You - Ed Sheeran"}]},
              {"time":3016,"duration":1749,"text":"Ed Sheeran/Steve Mac/Johnny McDaid","syllabus":[{"time":3016,"duration":1749,"text":"Ed Sheeran/Steve Mac/Johnny McDaid"}]},
              {"time":9731,"duration":800,"text":"The club","syllabus":[{"time":9731,"duration":196,"text":"The"},
                {"time":9927,"duration":0,"text":" "},{"time":9927,"duration":357,"text":"club"}]}
            ]}
            """.trimIndent()
        val lyrics = parseLyricsPlus(body, artist = "Ed Sheeran") as Lyrics.Synced
        assertThat(lyrics.lines.map { it.text }).containsExactly("The club")
        assertThat(lyrics.lines.single().words.map { it.text }).containsExactly("The ", "club").inOrder()
        assertThat(rank(lyrics)).isEqualTo(3)
    }

    @Test
    fun `Apple TTML word timing, spaces between spans, background vocals left out`() {
        val ttml =
            """<tt itunes:timing="Word"><body dur="3:53.713"><div>""" +
                """<p begin="9.731" end="12.105"><span begin="9.731" end="9.927">The</span> <span begin="9.927" end="10.284">club</span>""" +
                """<span ttm:role="x-bg"><span begin="10.3" end="10.5">(ooh)</span></span></p>""" +
                """<p begin="1:12.105" end="1:14.063"><span begin="1:12.105" end="1:12.310">So</span></p></div></body></tt>"""
        val lyrics = parseTtml(ttml, "BetterLyrics · Apple") as Lyrics.Synced
        assertThat(lyrics.lines.map { it.text }).containsExactly("The club", "So").inOrder()
        assertThat(lyrics.lines[0].words.map { it.text }).containsExactly("The ", "club").inOrder()
        assertThat(lyrics.lines[1].startMs).isEqualTo(72_105)
        assertThat(ttmlDurationSec(ttml)).isWithin(0.01).of(233.713)
        assertThat(betterLyricsTtml("""{"ttml":"${ttml.replace("\"", "\\\"")}"}""")).isEqualTo(ttml)
    }

    @Test
    fun `LRCLIB prefers synced lyrics, falls back to plain, skips instrumentals`() {
        val synced = parseLrclib("""{"duration":234.0,"syncedLyrics":"[00:09.73] The club\n[00:12.10] So the bar","plainLyrics":"x"}""")
        assertThat((synced as Lyrics.Synced).lines.map { it.text }).containsExactly("The club", "So the bar").inOrder()
        assertThat(synced.source).isEqualTo("LRCLIB")
        val plain = parseLrclib("""{"duration":234.0,"syncedLyrics":null,"plainLyrics":"The club\nSo the bar"}""")
        assertThat(plain).isInstanceOf(Lyrics.Plain::class.java)
        assertThat(parseLrclib("""{"instrumental":true,"plainLyrics":"x"}""")).isNull()
    }

    @Test
    fun `LRCLIB search keeps a match close in length, synced first`() {
        val body =
            """[{"duration":300.0,"syncedLyrics":"[00:01.00]Live version"},
               {"duration":235.0,"plainLyrics":"Plain studio"},
               {"duration":233.0,"syncedLyrics":"[00:01.00]Studio"}]"""
        val lyrics = parseLrclibSearch(body, durationSec = 234) as Lyrics.Synced
        assertThat(lyrics.lines.single().text).isEqualTo("Studio")
        assertThat(parseLrclibSearch("""[{"duration":300.0,"syncedLyrics":"[00:01.00]x"}]""", 234)).isNull()
    }

    @Test
    fun `lyrics ovh plain text`() {
        val lyrics = parseLyricsOvh("""{"lyrics":"Paroles de la chanson X par Y\r\nThe club isn't\r\n\r\nSo the bar"}""")
        assertThat((lyrics as Lyrics.Plain).text).isEqualTo("The club isn't\n\nSo the bar")
        assertThat(parseLyricsOvh("""{"error":"No lyrics found"}""")).isNull()
    }

    @Test
    fun `search uses a clean title and the first artist`() {
        val track =
            Track(
                "JGwWNGJdvx8",
                "Shape of You (Official Music Video) [4K]",
                "Ed Sheeran - Topic",
                "÷",
                233_700,
                null,
                null,
                "youtube_music",
            )
        val query = queryFor(track)
        assertThat(query.title).isEqualTo("Shape of You")
        assertThat(query.artist).isEqualTo("Ed Sheeran")
        assertThat(query.durationSec).isEqualTo(233)
        assertThat(query.videoId).isEqualTo("JGwWNGJdvx8")
        assertThat(mainArtist("Alan Walker, Au/Ra & Tomine Harket")).isEqualTo("Alan Walker")
        assertThat(cleanTitle("Backlight")).isEqualTo("Backlight")
    }

    @Test
    fun `pronunciation only for non-Latin lines, translations equal to the line are dropped`() {
        assertThat(needsPronunciation("散々な思い出")).isTrue()
        assertThat(needsPronunciation("ನಿನ್ನ ನೋಡಿದ")).isTrue()
        assertThat(needsPronunciation("Shape of you, café!")).isFalse()
        assertThat(sameText("Shape of you!", "shape of YOU")).isTrue()
        assertThat(sameText("The memories", "思い出")).isFalse()
    }
}
