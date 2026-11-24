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
    val deep = palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch ?: return null
    // The accent must be a colour that covers a real part of the cover. Palette's "vibrant" pick can be a
    // tiny patch (a blue ring on Hoshizora, a purple glint on a dark cover), which then coloured the
    // whole screen. Among colourful swatches, the one with the most area times colour wins.
    val total = palette.swatches.sumOf { it.population }.coerceAtLeast(1)
    val vivid =
        palette.swatches
            .filter { it.population >= total * MIN_AREA_SHARE && saturationOf(it) >= MIN_SATURATION }
            .maxByOrNull { it.population * saturationOf(it) }
    val hsv = FloatArray(HSV_SIZE)
    if (vivid == null) {
        // No colour worth borrowing (dark or black-and-white cover): soft silver, never the theme accent.
        android.graphics.Color.colorToHSV((palette.dominantSwatch ?: deep).rgb, hsv)
        hsv[1] = SILVER_SATURATION
        hsv[2] = if (lightTheme) LIGHT_SILVER_VALUE else DARK_SILVER_VALUE
    } else {
        android.graphics.Color.colorToHSV(vivid.rgb, hsv)
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

private fun saturationOf(swatch: Palette.Swatch): Float {
    val hsv = FloatArray(HSV_SIZE)
    android.graphics.Color.colorToHSV(swatch.rgb, hsv)
    return hsv[1]
}

private const val PALETTE_SOURCE_PX = 128
private const val HSV_SIZE = 3
private const val PALETTE_COLORS = 16
private const val MIN_SATURATION = 0.18f
private const val ACCENT_MIN_SATURATION = 0.45f
private const val DARK_MIN_VALUE = 0.78f
private const val LIGHT_MIN_VALUE = 0.38f
private const val LIGHT_MAX_VALUE = 0.58f

// A colour must cover at least this share of the cover to become the accent.
private const val MIN_AREA_SHARE = 0.05f

// Covers without such a colour: soft silver.
private const val SILVER_SATURATION = 0.05f
private const val DARK_SILVER_VALUE = 0.86f
private const val LIGHT_SILVER_VALUE = 0.40f
