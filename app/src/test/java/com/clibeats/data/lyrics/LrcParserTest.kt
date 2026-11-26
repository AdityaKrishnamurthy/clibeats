package com.clibeats.data.lyrics

import com.clibeats.domain.model.Lyrics
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Samples copied from the owner's library (SIGN IS B, Zoo, Headlights, Kannmaniye). */
class LrcParserTest {
    private fun synced(raw: String) = LrcParser.parse(raw) as Lyrics.Synced

    @Test
    fun `syllable sync with start and end tags (Japanese, no spaces)`() {
        val lyrics =
            synced(
                "[00:05.771]<00:05.771>サイ<00:06.160><00:06.160>ン<00:06.384><00:06.384>は<00:06.645>" +
                    "<00:06.717>B♡<00:07.549>",
            )
        val line = lyrics.lines.single()
        assertThat(lyrics.hasWordTiming).isTrue()
        assertThat(line.text).isEqualTo("サインはB♡")
        assertThat(line.words.map { it.text }).containsExactly("サイ", "ン", "は", "B♡").inOrder()
        // "は" ends at 6.645, then a gap until "B♡" starts at 6.717.
        assertThat(line.words[2].endMs).isEqualTo(6_645)
        assertThat(line.words[3].startMs).isEqualTo(6_717)
        assertThat(line.words[3].endMs).isEqualTo(7_549)
        assertThat(line.startMs).isEqualTo(5_771)
        assertThat(line.endMs).isEqualTo(7_549)
    }

    @Test
    fun `word sync with header tags and two-digit fractions (Zoo)`() {
        val lyrics =
            synced(
                """
                [ti:Zoo]
                [ar:Disney, Shakira]
                [by:Mornye (source: Zarz-LRC)]

                [00:00.365]<00:00.365>Come <00:00.541><00:00.541>on, <00:00.757><00:00.757>get <00:00.887>
                [03:00.794]<03:00.794>It's <03:00.92><03:00.92>fun <03:01.159>
                """.trimIndent(),
            )
        assertThat(lyrics.lines).hasSize(2)
        assertThat(lyrics.lines[0].text).isEqualTo("Come on, get")
        assertThat(lyrics.lines[0].words.map { it.text }).containsExactly("Come ", "on, ", "get").inOrder()
        // ".92" is 920 ms, not 92.
        assertThat(lyrics.lines[1].words[0].endMs).isEqualTo(180_920)
        assertThat(lyrics.lines[1].words[1].startMs).isEqualTo(180_920)
    }

    @Test
    fun `start-only word tags end at the next word`() {
        val line = synced("[00:01.00]<00:01.00>one <00:01.50>two <00:02.00>three").lines.single()
        assertThat(line.words.map { it.startMs to it.endMs })
            .containsExactly(1_000L to 1_500L, 1_500L to 2_000L, 2_000L to 2_600L)
            .inOrder()
    }

    @Test
    fun `line sync ends each line at the next and keeps blank break lines (Headlights)`() {
        val lyrics =
            synced(
                """
                [00:19.09](Headlights)
                [00:20.17](Headlight, headlight, headlights)
                [01:10.89]
                [02:57.26]Oh-oh-oh, I'll never let it go
                """.trimIndent(),
            )
        assertThat(lyrics.hasWordTiming).isFalse()
        assertThat(lyrics.lines.map { it.text })
            .containsExactly("(Headlights)", "(Headlight, headlight, headlights)", "", "Oh-oh-oh, I'll never let it go")
            .inOrder()
        assertThat(lyrics.lines[0].endMs).isEqualTo(20_170)
        assertThat(lyrics.lines[2].startMs).isEqualTo(70_890)
    }

    @Test
    fun `lines are sorted by time and repeated stamps make one line each`() {
        val lyrics = synced("[00:30.00]B\n[00:10.00][00:50.00]Chorus\n")
        assertThat(lyrics.lines.map { it.startMs to it.text })
            .containsExactly(10_000L to "Chorus", 30_000L to "B", 50_000L to "Chorus")
            .inOrder()
    }

    @Test
    fun `voice labels, background lines and the offset are handled`() {
        val lyrics =
            synced(
                """
                [offset:500]
                [00:10.00]v1:<00:10.00>Hello <00:10.50>world
                [bg: (ooh)]
                [00:12.00]v2: Second
                """.trimIndent(),
            )
        assertThat(lyrics.lines.map { it.text }).containsExactly("Hello world", "Second").inOrder()
        // A positive offset shows lyrics 500 ms sooner.
        assertThat(lyrics.lines[0].startMs).isEqualTo(9_500)
        assertThat(lyrics.lines[0].words[1].startMs).isEqualTo(10_000)
    }

    @Test
    fun `plain lyrics keep their verses (Kannmaniye)`() {
        val lyrics =
            LrcParser.parse("﻿\r\nKanmaniye kayyahiyiye\r\nKanyamaniye\r\n\r\n\r\n\r\nUsiraadalu jaagave illa\r\n") as
                Lyrics.Plain
        assertThat(lyrics.text).isEqualTo("Kanmaniye kayyahiyiye\nKanyamaniye\n\nUsiraadalu jaagave illa")
    }

    @Test
    fun `nothing to show gives null`() {
        assertThat(LrcParser.parse(null)).isNull()
        assertThat(LrcParser.parse("   \n ")).isNull()
        assertThat(LrcParser.parse("[ti:Song]\n[ar:Artist]")).isNull()
    }

    @Test
    fun `the best version wins - syllable over line over plain, then more lines`() {
        val plain = LrcParser.parse("one\ntwo\nthree")!!
        val line = LrcParser.parse("[00:01.00]one\n[00:02.00]two")!!
        val word = LrcParser.parse("[00:01.00]<00:01.00>one <00:01.50>")!!
        val longerLine = LrcParser.parse("[00:01.00]one\n[00:02.00]two\n[00:03.00]three")!!

        assertThat(pickBest(listOf(plain, line, word))).isEqualTo(word)
        assertThat(pickBest(listOf(plain, line))).isEqualTo(line)
        assertThat(pickBest(listOf(line, longerLine))).isEqualTo(longerLine)
        assertThat(pickBest(emptyList())).isNull()
    }
}
