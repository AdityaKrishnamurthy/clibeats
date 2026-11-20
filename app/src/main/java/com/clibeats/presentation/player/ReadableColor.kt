package com.clibeats.presentation.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Colour for small text drawn over the album-tinted Now Playing background (the quality line).
 *
 * The album's vivid colour often has the same hue as the background it sits on (a pink cover gives a pink
 * background and a pink accent), which is hard to read. This keeps the album hue when it can be made readable,
 * swaps red (reads as an error) for amber, never washes out to near-white, and falls back to a light amber when
 * the album colour can't reach [MIN_CONTRAST] against every colour in [backgrounds].
 */
internal fun readableTextColor(
    accent: Color,
    backgrounds: List<Color>,
    lightTheme: Boolean,
): Color {
    if (backgrounds.isEmpty()) return accent
    val (hue, saturation, value) = accent.toHsv()
    return if (lightTheme) {
        darkenUntilReadable(hue, saturation, value, backgrounds)
    } else {
        lightenUntilReadable(if (isRed(hue)) AMBER_HUE else hue, saturation, value, backgrounds)
    }
}

private fun lightenUntilReadable(
    hue: Float,
    startSaturation: Float,
    startValue: Float,
    backgrounds: List<Color>,
): Color {
    var saturation = startSaturation.coerceIn(MIN_SATURATION, MAX_SATURATION)
    var value = max(startValue, DARK_MIN_VALUE)
    while (true) {
        val candidate = hsvColor(hue, saturation, value)
        if (minContrast(candidate, backgrounds) >= MIN_CONTRAST) return candidate
        if (value < 1f) {
            value = min(1f, value + STEP)
        } else if (saturation > MIN_SATURATION) {
            saturation = max(MIN_SATURATION, saturation - STEP)
        } else {
            break
        }
    }
    // The album hue can't be made readable here (e.g. a bright cover in that same hue): light amber.
    return FALLBACK_LIGHT.takeIf { minContrast(it, backgrounds) >= MIN_CONTRAST } ?: FALLBACK_BRIGHTEST
}

private fun darkenUntilReadable(
    hue: Float,
    saturation: Float,
    startValue: Float,
    backgrounds: List<Color>,
): Color {
    var value = startValue
    while (value > LIGHT_MIN_VALUE) {
        val candidate = hsvColor(hue, max(saturation, MIN_SATURATION), value)
        if (minContrast(candidate, backgrounds) >= MIN_CONTRAST) return candidate
        value -= STEP
    }
    return hsvColor(hue, max(saturation, MIN_SATURATION), LIGHT_MIN_VALUE)
}

/** WCAG contrast ratio (1..21) between two opaque colours. */
internal fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + LUMINANCE_OFFSET) / (min(la, lb) + LUMINANCE_OFFSET)
}

private fun minContrast(
    color: Color,
    backgrounds: List<Color>,
): Float = backgrounds.minOf { contrastRatio(color, it) }

private fun isRed(hue: Float): Boolean = hue < RED_HUE_LIMIT || hue > FULL_CIRCLE - RED_HUE_LIMIT

private data class Hsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

@Suppress("MagicNumber") // standard RGB -> HSV sextant maths
private fun Color.toHsv(): Hsv {
    val maxC = max(red, max(green, blue))
    val minC = min(red, min(green, blue))
    val delta = maxC - minC
    val hue =
        when {
            delta == 0f -> 0f
            maxC == red -> SEXTANT * (((green - blue) / delta).mod(HUE_SEXTANTS))
            maxC == green -> SEXTANT * ((blue - red) / delta + 2f)
            else -> SEXTANT * ((red - green) / delta + 4f)
        }
    return Hsv(hue, if (maxC == 0f) 0f else delta / maxC, maxC)
}

@Suppress("MagicNumber") // standard HSV -> RGB sextant maths
private fun hsvColor(
    hue: Float,
    saturation: Float,
    value: Float,
): Color {
    val c = value * saturation
    val x = c * (1f - abs((hue / SEXTANT).mod(2f) - 1f))
    val m = value - c
    val (r, g, b) =
        when ((hue / SEXTANT).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    return Color(r + m, g + m, b + m)
}

/** Text needs 4.5:1 to stay readable at this small size. */
internal const val MIN_CONTRAST = 4.5f
private const val LUMINANCE_OFFSET = 0.05f
private const val STEP = 0.04f
private const val MIN_SATURATION = 0.35f // below this it reads as grey/white
private const val MAX_SATURATION = 0.8f // above this light colours look neon
private const val DARK_MIN_VALUE = 0.88f
private const val LIGHT_MIN_VALUE = 0.2f
private const val AMBER_HUE = 42f
private const val RED_HUE_LIMIT = 15f
private const val FULL_CIRCLE = 360f
private const val SEXTANT = 60f
private const val HUE_SEXTANTS = 6f
private val FALLBACK_LIGHT = Color(0xFFFFD54F) // light amber
private val FALLBACK_BRIGHTEST = Color(0xFFFFF3C4) // pale amber, for very bright backgrounds
