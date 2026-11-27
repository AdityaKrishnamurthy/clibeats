@file:Suppress("MagicNumber")

package com.clibeats.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * A theme: the app's backgrounds and surfaces tinted one way, in a light and a dark version (AMOLED uses the dark
 * one on pure black). Picking one also sets its accent; the accent can still be changed afterwards.
 * [CLASSIC] is CLIBeats' original neutral look and keeps whatever accent is picked.
 */
@Suppress("LongParameterList")
enum class ThemePalette(
    val displayName: String,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color,
    val lightBackground: Color,
    val lightSurface: Color,
    val lightSurfaceVariant: Color,
    /** The theme's own accent; null keeps the picked accent. */
    val accent: AccentColor?,
    /** Its colour for the swatches (and the accent it brings, for custom-coloured themes). */
    val swatch: Color,
) {
    CLASSIC(
        "Classic",
        Color(0xFF0C0C0C), Color(0xFF121212), Color(0xFF1A1A1A),
        Color(0xFFF4F4EF), Color(0xFFFFFFFF), Color(0xFFE8E8E3),
        accent = null, swatch = Color(0xFF1DB954),
    ),
    BERRY(
        "Berry",
        Color(0xFF1A1016), Color(0xFF22151D), Color(0xFF2D1C27),
        Color(0xFFFBF3F7), Color(0xFFFFFFFF), Color(0xFFF2E3EB),
        accent = AccentColor.MAGENTA, swatch = Color(0xFFE2457F),
    ),
    GROVE(
        "Grove",
        Color(0xFF0E1813), Color(0xFF14211A), Color(0xFF1B2C22),
        Color(0xFFF2F7F3), Color(0xFFFFFFFF), Color(0xFFE2EEE5),
        accent = AccentColor.GREEN, swatch = Color(0xFF3DDC84),
    ),
    OCEAN(
        "Ocean",
        Color(0xFF0C141E), Color(0xFF111D2B), Color(0xFF172739),
        Color(0xFFF1F6FB), Color(0xFFFFFFFF), Color(0xFFE0EBF6),
        accent = AccentColor.CYAN, swatch = Color(0xFF4FA8FF),
    ),
    EMBER(
        "Ember",
        Color(0xFF1A120D), Color(0xFF241912), Color(0xFF302219),
        Color(0xFFFBF5F1), Color(0xFFFFFFFF), Color(0xFFF2E6DD),
        accent = AccentColor.ORANGE, swatch = Color(0xFFFF8A4C),
    ),
    IRIS(
        "Iris",
        Color(0xFF14111D), Color(0xFF1B172A), Color(0xFF241E38),
        Color(0xFFF6F4FC), Color(0xFFFFFFFF), Color(0xFFE9E4F7),
        accent = AccentColor.LAVENDER, swatch = Color(0xFFA78BFA),
    ),
    ;

    companion object {
        fun fromName(name: String?): ThemePalette = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}
