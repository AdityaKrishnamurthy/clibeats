package com.clibeats.presentation.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SongTableRowTest {
    @Test
    fun `songRowSemantics formats title and artist by default`() {
        assertThat(songRowSemantics(trackTitle = "Midnight City", artist = "M83"))
            .isEqualTo("Midnight City by M83")
    }

    @Test
    fun `songRowSemantics appends now playing, selection, and status when present`() {
        val description =
            songRowSemantics(
                trackTitle = "Intro",
                artist = "The xx",
                isNowPlaying = true,
                selected = true,
                status = "42%",
            )
        assertThat(description).isEqualTo("Intro by The xx, playing now, selected, 42%")
    }

    @Test
    fun `songRowSemantics omits unselected flag`() {
        val description =
            songRowSemantics(
                trackTitle = "Intro",
                artist = "The xx",
                isNowPlaying = false,
                selected = false,
                status = null,
            )
        assertThat(description).isEqualTo("Intro by The xx")
    }
}
