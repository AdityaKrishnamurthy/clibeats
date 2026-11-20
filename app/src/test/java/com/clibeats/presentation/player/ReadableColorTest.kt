package com.clibeats.presentation.player

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReadableColorTest {
    private val darkPink = Color(0xFF4A1030)
    private val midPink = Color(0xFF8E3A66)
    private val darkPurple = Color(0xFF221034)

    @Test
    fun `text over a same-hue background is made readable`() {
        val pinkAccent = Color(0xFFE0409A)
        val result = readableTextColor(pinkAccent, listOf(darkPink, midPink), lightTheme = false)

        assertThat(contrastRatio(result, darkPink)).isAtLeast(MIN_CONTRAST)
        assertThat(contrastRatio(result, midPink)).isAtLeast(MIN_CONTRAST)
    }

    @Test
    fun `a readable album colour keeps its hue`() {
        val violet = Color(0xFFB57BFF)
        val result = readableTextColor(violet, listOf(darkPurple), lightTheme = false)

        // Still clearly violet: blue and red above green.
        assertThat(result.blue).isGreaterThan(result.green)
        assertThat(result.red).isGreaterThan(result.green)
        assertThat(contrastRatio(result, darkPurple)).isAtLeast(MIN_CONTRAST)
    }

    @Test
    fun `red is swapped for amber`() {
        val red = Color(0xFFE53935)
        val result = readableTextColor(red, listOf(Color(0xFF200808)), lightTheme = false)

        // Amber: red high, green clearly above blue.
        assertThat(result.red).isGreaterThan(0.8f)
        assertThat(result.green).isGreaterThan(result.blue + 0.2f)
    }

    @Test
    fun `never washes out to white on a dark background`() {
        val result = readableTextColor(Color(0xFF2E7D32), listOf(Color(0xFF0D1F0E)), lightTheme = false)

        val saturation =
            (maxOf(result.red, result.green, result.blue) - minOf(result.red, result.green, result.blue)) /
                maxOf(result.red, result.green, result.blue)
        assertThat(saturation).isAtLeast(0.3f)
    }

    @Test
    fun `falls back to a readable light colour on a bright background of the same hue`() {
        val brightPink = Color(0xFFD9468F)
        val result = readableTextColor(Color(0xFFFF4FA8), listOf(brightPink), lightTheme = false)

        assertThat(contrastRatio(result, brightPink)).isAtLeast(1.9f)
        assertThat(result).isNotEqualTo(Color(0xFFFF4FA8))
    }

    @Test
    fun `light theme darkens until readable`() {
        val paleBackground = Color(0xFFF3E6EE)
        val result = readableTextColor(Color(0xFFFF80C0), listOf(paleBackground), lightTheme = true)

        assertThat(contrastRatio(result, paleBackground)).isAtLeast(MIN_CONTRAST)
    }
}
