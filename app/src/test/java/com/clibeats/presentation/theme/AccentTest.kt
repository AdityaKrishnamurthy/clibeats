package com.clibeats.presentation.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccentTest {
    @Test
    fun `presets are saved and read back by name`() {
        val accent = Accent.fromStored("PINK")
        assertThat(accent.preset).isEqualTo(AccentColor.PINK)
        assertThat(accent.storedName).isEqualTo("PINK")
    }

    @Test
    fun `custom colours round-trip as hex`() {
        val accent = Accent.fromStored("#12AB9F")
        assertThat(accent.preset).isNull()
        assertThat(accent.color).isEqualTo(Color(0xFF12AB9F))
        assertThat(accent.storedName).isEqualTo("#12AB9F")
        assertThat(accent.displayName).isEqualTo("Custom #12AB9F")
    }

    @Test
    fun `people who picked the old Blue preset keep it as a custom colour`() {
        val accent = Accent.fromStored("BLUE")
        assertThat(accent.preset).isNull()
        assertThat(Accent.toHex(accent.color)).isEqualTo("#2979FF")
    }

    @Test
    fun `missing or broken values fall back to green`() {
        assertThat(Accent.fromStored(null)).isEqualTo(Accent.DEFAULT)
        assertThat(Accent.fromStored("#XYZ")).isEqualTo(Accent.DEFAULT)
        assertThat(Accent.fromStored("NOPE")).isEqualTo(Accent.DEFAULT)
        assertThat(Accent.parseHex("1db954")).isEqualTo(Color(0xFF1DB954))
    }
}
