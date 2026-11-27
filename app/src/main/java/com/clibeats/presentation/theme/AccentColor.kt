package com.clibeats.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Preset accent colours the user can choose from in Settings.
 *
 * Each entry carries a [Color] used for borders, progress bars, highlights,
 * and the [displayName] shown in the UI.
 */
@Suppress("MagicNumber")
enum class AccentColor(
    val color: Color,
    val displayName: String,
) {
    // Bright, terminal-style colours in a loop around the hue wheel: two rows of five in Settings with
    // "Custom" last. Saved by name, so the order can change freely.
    GREEN(Color(0xFF1DB954), "Green"),
    LIME(Color(0xFFB2FF59), "Lime"),
    AMBER(Color(0xFFFFC107), "Amber"),
    ORANGE(Color(0xFFFF9100), "Orange"),
    MAGENTA(Color(0xFFFF4FD8), "Magenta"),
    LAVENDER(Color(0xFFB388FF), "Lavender"),
    CYAN(Color(0xFF18D8FF), "Cyan"),
    TEAL(Color(0xFF1DE9B6), "Teal"),
    WHITE(Color(0xFFFFFFFF), "White"),
}

/**
 * The accent in use: one of the [AccentColor] presets, or a custom colour ([preset] = null).
 * Saved as the preset's name, or as `#RRGGBB` for a custom colour.
 */
data class Accent(
    val color: Color,
    val preset: AccentColor?,
) {
    val displayName: String get() = preset?.displayName ?: "Custom ${toHex(color)}"
    val storedName: String get() = preset?.name ?: toHex(color)

    companion object {
        /** New installs: the Classic theme's silver-white (anyone who picked an accent keeps theirs). */
        val DEFAULT = Accent(AccentColor.WHITE.color, AccentColor.WHITE)

        /** Presets dropped in 2.3.1: anyone who picked one keeps that exact colour as a custom colour. */
        @Suppress("MagicNumber")
        private val RETIRED =
            mapOf(
                "BLUE" to Color(0xFF2979FF),
                "INDIGO" to Color(0xFF7C83FF),
                "PURPLE" to Color(0xFF9C27B0),
                "PINK" to Color(0xFFE91E63),
                "RED" to Color(0xFFFF5252),
            )

        fun fromStored(name: String?): Accent =
            when {
                name == null -> DEFAULT
                name.startsWith("#") -> parseHex(name)?.let { Accent(it, null) } ?: DEFAULT
                name in RETIRED -> Accent(RETIRED.getValue(name), null)
                else -> AccentColor.entries.firstOrNull { it.name == name }?.let { Accent(it.color, it) } ?: DEFAULT
            }

        @Suppress("MagicNumber")
        fun toHex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

        /** `#RRGGBB` or `RRGGBB` → opaque colour; null if it isn't one. */
        @Suppress("MagicNumber")
        fun parseHex(text: String): Color? =
            text
                .trim()
                .removePrefix("#")
                .takeIf { it.length == 6 }
                ?.toLongOrNull(16)
                ?.let { Color(0xFF000000 or it) }
    }
}
