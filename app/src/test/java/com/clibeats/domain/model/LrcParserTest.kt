package com.clibeats.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LrcParserTest {
    @Test
    fun `parses centisecond and millisecond timestamps and sorts out of order lines`() {
        val raw =
            """
            [ar:M83]
            [00:12.5]Second line raw?
            [01:02.34]Chorus starts
            [00:05.125]First line
            [00:12.50]Second line
            Not a timestamped line
            """.trimIndent()

        val parsed = LrcParser.parse(raw)

        assertThat(parsed).containsExactly(
            LyricLine(timeMs = 5_125L, text = "First line"),
            LyricLine(timeMs = 12_500L, text = "Second line"),
            LyricLine(timeMs = 62_340L, text = "Chorus starts"),
        ).inOrder()
    }

    @Test
    fun `ignores empty lines and metadata headers`() {
        val parsed =
            LrcParser.parse(
                """
                [ti:Intro]
                [al:Hurry Up]

                [00:00.00]
                """.trimIndent(),
            )
        assertThat(parsed).containsExactly(LyricLine(timeMs = 0L, text = ""))
    }
}
