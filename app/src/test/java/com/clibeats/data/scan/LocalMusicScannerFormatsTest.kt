package com.clibeats.data.scan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalMusicScannerFormatsTest {
    @Test
    fun `major formats are picked up by name even without an audio mime type`() {
        listOf("a.mp3", "a.m4a", "a.flac", "a.ogg", "a.opus", "a.wav", "a.aac", "A.FLAC").forEach { name ->
            assertThat(LocalMusicScanner.isAudio("application/octet-stream", name)).isTrue()
        }
    }

    @Test
    fun `aiff is skipped because it cannot be played`() {
        assertThat(LocalMusicScanner.isAudio("audio/x-aiff", "song.aiff")).isFalse()
        assertThat(LocalMusicScanner.isAudio("application/octet-stream", "song.aif")).isFalse()
    }

    @Test
    fun `other audio mime types are kept`() {
        assertThat(LocalMusicScanner.isAudio("audio/mpeg", "track")).isTrue()
        assertThat(LocalMusicScanner.isAudio("image/jpeg", "cover.jpg")).isFalse()
    }
}
