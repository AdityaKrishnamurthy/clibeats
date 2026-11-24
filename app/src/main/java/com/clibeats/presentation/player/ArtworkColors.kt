package com.clibeats.presentation.player

import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Colours taken from the album art: [accent] for controls, [tint] for the background, and [coverColors]
 * (the main colours of the cover itself, which shows blurred behind the screen) to keep text readable.
 */
data class ArtworkColors(
    val accent: Color,
    val tint: Color,
    val coverColors: List<Color> = emptyList(),
)

/**
 * Picks colours from [artworkUrl] (the small cached thumbnail is enough). Keeps the previous song's
 * colours until the new ones are ready, so the screen fades between them instead of flashing the theme.
 * Returns null only when there is no artwork (the theme colours are used then).
 */
@Composable
fun rememberArtworkColors(
    artworkUrl: String?,
    lightTheme: Boolean,
): ArtworkColors? {
    val context = LocalContext.current
    var colors by remember { mutableStateOf<ArtworkColors?>(null) }
    LaunchedEffect(artworkUrl, lightTheme) {
        if (artworkUrl == null) {
            colors = null
            return@LaunchedEffect
        }
        val request =
            ImageRequest
                .Builder(context)
                .data(artworkUrl)
                .size(PALETTE_SOURCE_PX)
                .allowHardware(false)
                .build()
        val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        colors =
            bitmap?.let {
                withContext(Dispatchers.Default) {
                    colorsFrom(Palette.from(it).maximumColorCount(PALETTE_COLORS).generate(), lightTheme)
                }
            }
    }
    return colors
}

@Suppress("ReturnCount")
private fun colorsFrom(
    palette: Palette,
    lightTheme: Boolean,
): ArtworkColors? {
    val vivid =
        palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch
    val deep = palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch ?: vivid
    if (vivid == null || deep == null) return null
    val hsv = FloatArray(HSV_SIZE)
    android.graphics.Color.colorToHSV(vivid.rgb, hsv)
    if (hsv[1] < MIN_SATURATION) {
        softAccent(palette, vivid, lightTheme, hsv)
    } else {
        // Bright enough to read on a dark screen, dark enough on a light one.
        hsv[1] = hsv[1].coerceAtLeast(ACCENT_MIN_SATURATION)
        hsv[2] =
            if (lightTheme) hsv[2].coerceIn(LIGHT_MIN_VALUE, LIGHT_MAX_VALUE) else hsv[2].coerceAtLeast(DARK_MIN_VALUE)
    }
    val cover = listOfNotNull(palette.dominantSwatch, vivid, palette.lightVibrantSwatch, palette.mutedSwatch)
    return ArtworkColors(
        accent = Color(android.graphics.Color.HSVToColor(hsv)),
        tint = Color(deep.rgb),
        coverColors = cover.map { Color(it.rgb) }.distinct(),
    )
}

/**
 * For a dark or nearly colourless cover (a dark cover, Hoshizora) there is no vivid colour to borrow, and
 * the theme accent clashed with it (purple on black). Writes into [hsv] a soft colour from the cover itself:
 * the hue of its most colourful part, kept pale. A cover with no colour at all gets silver.
 */
private fun softAccent(
    palette: Palette,
    vivid: Palette.Swatch,
    lightTheme: Boolean,
    hsv: FloatArray,
) {
    val sizeable = palette.swatches.filter { it.population >= vivid.population / HINT_MIN_SHARE }
    val hint = sizeable.maxByOrNull { it.hsl[1] } ?: vivid
    val hintHsv = FloatArray(HSV_SIZE).also { android.graphics.Color.colorToHSV(hint.rgb, it) }
    hsv[0] = hintHsv[0]
    hsv[1] = if (hintHsv[1] >= HINT_SATURATION) MUTED_ACCENT_SATURATION else SILVER_SATURATION
    hsv[2] = if (lightTheme) LIGHT_MUTED_VALUE else DARK_MUTED_VALUE
}

private const val PALETTE_SOURCE_PX = 128
private const val HSV_SIZE = 3
private const val PALETTE_COLORS = 16
private const val MIN_SATURATION = 0.18f
private const val ACCENT_MIN_SATURATION = 0.45f
private const val DARK_MIN_VALUE = 0.78f
private const val LIGHT_MIN_VALUE = 0.38f
private const val LIGHT_MAX_VALUE = 0.58f

// Colourless covers: a pale tint of the cover's hue, or silver when there is no hue to speak of.
private const val HINT_MIN_SHARE = 8
private const val HINT_SATURATION = 0.12f
private const val MUTED_ACCENT_SATURATION = 0.32f
private const val SILVER_SATURATION = 0.06f
private const val DARK_MUTED_VALUE = 0.88f
private const val LIGHT_MUTED_VALUE = 0.38f
