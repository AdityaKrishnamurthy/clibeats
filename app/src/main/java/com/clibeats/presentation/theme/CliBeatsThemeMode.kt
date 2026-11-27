package com.clibeats.presentation.theme

/**
 * Available background theme modes.
 * DARK  → near-black #0C0C0C (original)
 * AMOLED → pure black #000000 (true OLED black, zero battery on OLED panels)
 */
enum class CliBeatsThemeMode(val displayName: String) {
    /** Follows the phone: Light by day settings, Dark when the phone is dark. */
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED"),
    ;

    /** The mode actually shown: [SYSTEM] becomes Light or Dark from the phone's setting. */
    fun resolve(systemDark: Boolean): CliBeatsThemeMode =
        if (this == SYSTEM) {
            if (systemDark) DARK else LIGHT
        } else {
            this
        }
}
