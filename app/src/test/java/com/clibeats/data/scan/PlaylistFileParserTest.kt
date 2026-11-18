package com.clibeats.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFileParserTest {
    private val pathToId =
        mapOf(
            "rock/queen/bohemian.flac" to "s1",
            "rock/queen/killer queen.mp3" to "s2",
            "pop/idol.flac" to "s3",
            "dup/a/same.mp3" to "d1",
            "dup/b/same.mp3" to "d2",
        )
    private val nameToIds =
        pathToId.entries.groupBy({ it.key.substringAfterLast('/') }, { it.value })

    private fun resolve(
        entry: String,
        dir: String = "rock",
    ) = PlaylistFileParser.resolve(entry, dir, pathToId, nameToIds)

    @Test
    fun `detects playlist files by extension and mime`() {
        assertTrue(PlaylistFileParser.isPlaylistFile("", "Mix.M3U8"))
        assertTrue(PlaylistFileParser.isPlaylistFile("audio/x-mpegurl", "x"))
        assertTrue(PlaylistFileParser.isPlaylistFile("", "radio.pls"))
        assertFalse(PlaylistFileParser.isPlaylistFile("audio/flac", "song.flac"))
    }

    @Test
    fun `m3u skips comments, blanks and BOM`() {
        val content = "\uFEFF#EXTM3U\n#EXTINF:123,Queen - Bohemian\nqueen/bohemian.flac\n\n queen/killer queen.mp3 \n"
        assertEquals(
            listOf("queen/bohemian.flac", "queen/killer queen.mp3"),
            PlaylistFileParser.parseEntries("mix.m3u8", content),
        )
    }

    @Test
    fun `pls reads FileN entries`() {
        val content = "[playlist]\nFile1=../pop/idol.flac\nTitle1=Idol\nfile2 = queen/bohemian.flac\nNumberOfEntries=2"
        assertEquals(
            listOf("../pop/idol.flac", "queen/bohemian.flac"),
            PlaylistFileParser.parseEntries("a.pls", content),
        )
    }

    @Test
    fun `resolves relative, parent, backslash and case-insensitive paths`() {
        assertEquals("s1", resolve("queen/bohemian.flac"))
        assertEquals("s2", resolve("Queen\\Killer Queen.MP3"))
        assertEquals("s3", resolve("../pop/idol.flac"))
        assertEquals("s3", resolve("./../pop/./idol.flac"))
    }

    @Test
    fun `resolves absolute paths and file uris by suffix`() {
        assertEquals("s1", resolve("/storage/emulated/0/Music/rock/queen/bohemian.flac"))
        assertEquals("s3", resolve("C:\\Users\\me\\Music\\pop\\idol.flac"))
        assertEquals("s2", resolve("file:///sdcard/Music/rock/queen/killer%20queen.mp3"))
    }

    @Test
    fun `falls back to unique filename only`() {
        assertEquals("s3", resolve("somewhere/else/idol.flac"))
        assertNull(resolve("elsewhere/same.mp3"))
        assertNull(resolve("missing.flac"))
        assertNull(resolve("https://example.com/stream.mp3"))
    }
}
