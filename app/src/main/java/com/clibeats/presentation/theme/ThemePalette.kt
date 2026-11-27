@file:Suppress("MagicNumber")

package com.clibeats.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A theme: the app's backgrounds and surfaces tinted one way, in a light and a dark version (AMOLED uses the dark
 * one on pure black). Picking one also sets its accent; the accent can still be changed afterwards.
 * [CLASSIC] is CLIBeats' original neutral look. A custom theme ([custom]) is made from any one colour.
 * Saved by [name]: a preset's name, or `#RRGGBB` for a custom theme.
 */
@Suppress("LongParameterList")
class ThemePalette private constructor(
    val name: String,
    val displayName: String,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color,
    val lightBackground: Color,
    val lightSurface: Color,
    val lightSurfaceVariant: Color,
    /** Its colour for the swatches, and the accent it brings. */
    val swatch: Color,
) {
    val isCustom: Boolean get() = name.startsWith("#")

    override fun equals(other: Any?): Boolean = other is ThemePalette && other.name == name

    override fun hashCode(): Int = name.hashCode()

    companion object {
        val CLASSIC =
            ThemePalette(
                "CLASSIC", "Classic",
                Color(0xFF0C0C0C), Color(0xFF121212), Color(0xFF1A1A1A),
                Color(0xFFF4F4EF), Color(0xFFFFFFFF), Color(0xFFE8E8E3),
                // Neutral grey with a soft silver-white accent: a clean default that no other theme looks like.
                swatch = Color(0xFFE4E4E7),
            )
        val BERRY =
            ThemePalette(
                "BERRY", "Berry",
                Color(0xFF1A1016), Color(0xFF22151D), Color(0xFF2D1C27),
                Color(0xFFFBF3F7), Color(0xFFFFFFFF), Color(0xFFF2E3EB),
                swatch = Color(0xFFE2457F),
            )
        val GROVE =
            ThemePalette(
                "GROVE", "Grove",
                Color(0xFF0E1813), Color(0xFF14211A), Color(0xFF1B2C22),
                Color(0xFFF2F7F3), Color(0xFFFFFFFF), Color(0xFFE2EEE5),
                swatch = Color(0xFF3DDC84),
            )
        val OCEAN =
            ThemePalette(
                "OCEAN", "Ocean",
                Color(0xFF0C141E), Color(0xFF111D2B), Color(0xFF172739),
                Color(0xFFF1F6FB), Color(0xFFFFFFFF), Color(0xFFE0EBF6),
                swatch = Color(0xFF4FA8FF),
            )
        val EMBER =
            ThemePalette(
                "EMBER", "Ember",
                Color(0xFF1A120D), Color(0xFF241912), Color(0xFF302219),
                Color(0xFFFBF5F1), Color(0xFFFFFFFF), Color(0xFFF2E6DD),
                swatch = Color(0xFFFF8A4C),
            )
        val IRIS =
            ThemePalette(
                "IRIS", "Iris",
                Color(0xFF14111D), Color(0xFF1B172A), Color(0xFF241E38),
                Color(0xFFF6F4FC), Color(0xFFFFFFFF), Color(0xFFE9E4F7),
                swatch = Color(0xFFA78BFA),
            )

        /** The ready-made themes, in the order Settings shows them. */
        val entries: List<ThemePalette> = listOf(CLASSIC, BERRY, GROVE, OCEAN, EMBER, IRIS)

        /** A theme made from one [color]: backgrounds and panels softly tinted with it, light and dark. */
        fun custom(color: Color): ThemePalette =
            ThemePalette(
                Accent.toHex(color), "Custom",
                lerp(Color(0xFF0C0C0C), color, 0.07f),
                lerp(Color(0xFF121212), color, 0.10f),
                lerp(Color(0xFF1A1A1A), color, 0.15f),
                lerp(Color(0xFFF7F7F5), color, 0.05f),
                Color(0xFFFFFFFF),
                lerp(Color(0xFFEAEAE6), color, 0.12f),
                swatch = color,
            )

        fun fromName(name: String?): ThemePalette =
            when {
                name == null -> CLASSIC
                name.startsWith("#") -> Accent.parseHex(name)?.let(::custom) ?: CLASSIC
                else -> entries.firstOrNull { it.name == name } ?: CLASSIC
            }
    }
}
