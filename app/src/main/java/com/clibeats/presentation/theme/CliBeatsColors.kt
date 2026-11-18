@file:Suppress("ktlint:standard:multiline-expression-wrapping", "MagicNumber")

package com.clibeats.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Static fallback colours (used before theme is resolved) ────────────────

val CliBeatsBackgroundDark = Color(0xFF0C0C0C)
val CliBeatsBackgroundAmoled = Color(0xFF000000)
val CliBeatsSurfaceDark = Color(0xFF121212)
val CliBeatsSurfaceAmoled = Color(0xFF0A0A0A)
val CliBeatsSurfaceVariantDark = Color(0xFF1A1A1A)
val CliBeatsAccentDefault = Color(0xFF1DB954)

val CliBeatsTextPrimaryDark = Color(0xFFFFFFFF)
val CliBeatsTextSecondaryDark = Color(0xFFB3B3B3)
val CliBeatsTextDisabledDark = Color(0xFF535353)
val CliBeatsDividerDark = Color(0xFF2A2A2A)
val CliBeatsBorderInactiveDark = Color(0xFF333333)
val CliBeatsBorderActive = Color(0xFF1DB954) // overridden at runtime by LocalAccentColor
val CliBeatsDestructive = Color(0xFFE53935)
val CliBeatsDestructiveSurface = Color(0xFF3B1515)

// ── CompositionLocals ──────────────────────────────────────────────────────

/** Provides the currently active accent [Color] to the composition tree. */
val LocalAccentColor = staticCompositionLocalOf { CliBeatsAccentDefault }

/** Provides the currently active [CliBeatsThemeMode]. */
val LocalThemeMode = staticCompositionLocalOf { CliBeatsThemeMode.DARK }

// ── Dynamic colour scheme builder ──────────────────────────────────────────

// Light palette ("paper terminal"): same layout and accents, dark text on off-white.
val CliBeatsBackgroundLight = Color(0xFFF4F4EF)
val CliBeatsSurfaceLight = Color(0xFFFFFFFF)
val CliBeatsSurfaceVariantLight = Color(0xFFE8E8E3)
val CliBeatsTextPrimaryLight = Color(0xFF141414)
val CliBeatsTextSecondaryLight = Color(0xFF555555)
val CliBeatsDividerLight = Color(0xFFD0D0CB)
val CliBeatsBorderInactiveLight = Color(0xFFB5B5B0)

/**
 * Builds the Material3 colour scheme from the given [accent] and [mode].
 * Call this whenever either changes (theme engine drives it).
 */
fun buildCliBeatsColorScheme(
    accent: Color,
    mode: CliBeatsThemeMode,
) = if (mode == CliBeatsThemeMode.LIGHT) {
    lightColorScheme(
        background = CliBeatsBackgroundLight,
        surface = CliBeatsSurfaceLight,
        surfaceVariant = CliBeatsSurfaceVariantLight,
        primary = accent,
        onPrimary = Color(0xFFFFFFFF),
        secondaryContainer = CliBeatsSurfaceVariantLight,
        onSecondaryContainer = accent,
        onBackground = CliBeatsTextPrimaryLight,
        onSurface = CliBeatsTextPrimaryLight,
        onSurfaceVariant = CliBeatsTextSecondaryLight,
        outline = CliBeatsDividerLight,
        outlineVariant = CliBeatsBorderInactiveLight,
        // Dialogs, menus and sheets read these; unset they fall back to Material's purple-grey.
        surfaceTint = CliBeatsSurfaceLight,
        surfaceBright = CliBeatsSurfaceLight,
        surfaceDim = CliBeatsBackgroundLight,
        surfaceContainerLowest = CliBeatsSurfaceLight,
        surfaceContainerLow = CliBeatsSurfaceLight,
        surfaceContainer = CliBeatsSurfaceLight,
        surfaceContainerHigh = CliBeatsSurfaceLight,
        surfaceContainerHighest = CliBeatsSurfaceVariantLight,
        error = CliBeatsDestructive,
        errorContainer = Color(0xFFFDE7E7),
    )
} else {
    val background = if (mode == CliBeatsThemeMode.AMOLED) CliBeatsBackgroundAmoled else CliBeatsBackgroundDark
    val surface = if (mode == CliBeatsThemeMode.AMOLED) CliBeatsSurfaceAmoled else CliBeatsSurfaceDark
    darkColorScheme(
        background = background,
        surface = surface,
        surfaceVariant = CliBeatsSurfaceVariantDark,
        primary = accent,
        onPrimary = Color(0xFF000000),
        secondaryContainer = CliBeatsSurfaceVariantDark,
        onSecondaryContainer = accent,
        onBackground = CliBeatsTextPrimaryDark,
        onSurface = CliBeatsTextPrimaryDark,
        onSurfaceVariant = CliBeatsTextSecondaryDark,
        outline = CliBeatsDividerDark,
        outlineVariant = CliBeatsBorderInactiveDark,
        // Dialogs, menus and sheets read these; unset they fall back to Material's purple-grey.
        surfaceTint = surface,
        surfaceBright = CliBeatsSurfaceVariantDark,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = CliBeatsSurfaceVariantDark,
        error = CliBeatsDestructive,
        errorContainer = CliBeatsDestructiveSurface,
    )
}

/** Legacy static scheme (used for previews / tests). */
val CliBeatsColorScheme = buildCliBeatsColorScheme(CliBeatsAccentDefault, CliBeatsThemeMode.DARK)

// Theme-aware colours: screens use these names everywhere, so they must follow the
// selected mode (Dark/AMOLED/Light) and accent instead of being fixed constants.
val CliBeatsBackground: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

val CliBeatsSurface: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface

val CliBeatsAccent: Color
    @Composable @ReadOnlyComposable
    get() = LocalAccentColor.current

val CliBeatsTextPrimary: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onBackground

val CliBeatsTextSecondary: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val CliBeatsTextDisabled: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)

val CliBeatsDivider: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outline

val CliBeatsBorderInactive: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outlineVariant

val CliBeatsSurfaceVariant: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceVariant

private const val DISABLED_ALPHA = 0.38f
