package com.clibeats.presentation.theme

import androidx.compose.ui.graphics.Color

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
    // Listed by hue: two rows of five in Settings. Saved by name, so the order can change freely.
    GREEN(Color(0xFF1DB954), "Green"),
    TEAL(Color(0xFF1DE9B6), "Teal"),
    BLUE(Color(0xFF2979FF), "Blue"),
    INDIGO(Color(0xFF7C83FF), "Indigo"),
    PURPLE(Color(0xFF9C27B0), "Purple"),
    PINK(Color(0xFFE91E63), "Pink"),
    RED(Color(0xFFFF5252), "Red"),
    ORANGE(Color(0xFFFF9100), "Orange"),
    AMBER(Color(0xFFFFC107), "Amber"),
    WHITE(Color(0xFFFFFFFF), "White"),
    ;

    companion object {
        val default: AccentColor = GREEN

        /** Resolve from the hex string stored in DataStore; falls back to [default]. */
        fun fromHex(hex: String): AccentColor =
            entries.firstOrNull { it.color.value.toString(16).uppercase() == hex.uppercase() }
                ?: default
    }
}
