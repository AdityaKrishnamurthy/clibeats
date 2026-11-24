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
                    val palette = Palette.from(it).maximumColorCount(PALETTE_COLORS).generate()
                    colorsFrom(palette, lightTheme)
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
    // The accent must be a real colour covering a real part of the cover. Palette's "vibrant" pick can be a
    // tiny patch (a blue ring on Hoshizora), and saturation calls near-black "colourful": a dark cover'
    // #101018 is 33% saturated yet looks black, and was brightened into purple. Chroma (how far apart the
    // strongest and weakest of red, green and blue are) measures visible colour; it is ~0 for near-black.
    val total = palette.swatches.sumOf { it.population }.coerceAtLeast(1)
    val vivid =
        palette.swatches
            .filter { it.population >= total * MIN_AREA_SHARE && chromaOf(it) >= MIN_CHROMA }
            .maxByOrNull { it.population * chromaOf(it) }
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

/** 0 (grey, black or white) to 1 (pure colour): the spread between the strongest and weakest channel. */
private fun chromaOf(swatch: Palette.Swatch): Float {
    val r = android.graphics.Color.red(swatch.rgb)
    val g = android.graphics.Color.green(swatch.rgb)
    val b = android.graphics.Color.blue(swatch.rgb)
    return (maxOf(r, g, b) - minOf(r, g, b)) / CHANNEL_MAX
}

private const val PALETTE_SOURCE_PX = 128
private const val HSV_SIZE = 3
private const val PALETTE_COLORS = 16
private const val ACCENT_MIN_SATURATION = 0.45f
private const val DARK_MIN_VALUE = 0.78f
private const val LIGHT_MIN_VALUE = 0.38f
private const val LIGHT_MAX_VALUE = 0.58f

// A colour must cover at least this share of the cover to become the accent.
private const val MIN_AREA_SHARE = 0.05f

/** Visible colour needed to count: muted reds and blues pass, near-black and greys don't. */
private const val MIN_CHROMA = 0.12f
private const val CHANNEL_MAX = 255f

// Covers without such a colour: soft silver.
private const val SILVER_SATURATION = 0.05f
private const val DARK_SILVER_VALUE = 0.86f
private const val LIGHT_SILVER_VALUE = 0.40f
