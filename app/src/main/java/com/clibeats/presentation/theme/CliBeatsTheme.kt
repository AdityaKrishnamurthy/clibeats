@file:Suppress("ktlint:standard:function-naming")

package com.clibeats.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * CLIBeats Material3 theme.
 *
 * Provides dynamic [accent] colour and [mode] through [CompositionLocalProvider]
 * so all descendants can read [LocalAccentColor] and [LocalThemeMode].
 *
 * Defaults keep backward compatibility (green / DARK) for previews and tests.
 */
@Composable
@Suppress("FunctionNaming")
fun CliBeatsTheme(
    accent: AccentColor = AccentColor.GREEN,
    mode: CliBeatsThemeMode = CliBeatsThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    // Bright accents fail contrast as text on white, so deepen them in Light mode.
    val accentColor =
        if (mode == CliBeatsThemeMode.LIGHT) lerp(accent.color, Color.Black, LIGHT_ACCENT_DARKEN) else accent.color
    val colorScheme = buildCliBeatsColorScheme(accentColor, mode)

    CompositionLocalProvider(
        LocalAccentColor provides accentColor,
        LocalThemeMode provides mode,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CliBeatsTypography,
            shapes = CliBeatsShapes,
            content = content,
        )
    }
}

private const val LIGHT_ACCENT_DARKEN = 0.4f
