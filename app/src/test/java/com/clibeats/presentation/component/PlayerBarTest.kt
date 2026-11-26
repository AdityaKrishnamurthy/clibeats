package com.clibeats.presentation.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerBarTest {
    @Test
    fun `formatPlayerTime renders zero and negative as 0-00`() {
        assertThat(formatPlayerTime(0L)).isEqualTo("0:00")
        assertThat(formatPlayerTime(-1_500L)).isEqualTo("0:00")
    }

    @Test
    fun `formatPlayerTime pads seconds and includes hours when needed`() {
        assertThat(formatPlayerTime(5_000L)).isEqualTo("0:05")
        assertThat(formatPlayerTime(185_000L)).isEqualTo("3:05")
        assertThat(formatPlayerTime(3_661_000L)).isEqualTo("1:01:01")
    }

    @Test
    fun `ellipsize leaves short strings intact and trims trailing space before ellipsis`() {
        assertThat("Queue".ellipsize(24)).isEqualTo("Queue")
        assertThat("Late Night Coding".ellipsize(17)).isEqualTo("Late Night Coding")
        assertThat("Late Night Vibes Forever".ellipsize(11)).isEqualTo("Late Night…")
    }
}
