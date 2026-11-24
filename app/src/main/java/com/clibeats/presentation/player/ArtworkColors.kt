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
 * Returns null for no artwork or black-and-white artwork (the theme colours are used then).
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
    if (hsv[1] < MIN_SATURATION) return null // black-and-white cover: keep the theme accent
    // Near-black or grey picks (a very dark blue-black can count as 33% saturated) would be brightened into
    // an unrelated purple; dark covers keep the theme accent chosen in Settings instead.
    if (chromaOf(vivid.rgb) < MIN_CHROMA) return null
    // Bright enough to read on a dark screen, dark enough on a light one.
    hsv[1] = hsv[1].coerceAtLeast(ACCENT_MIN_SATURATION)
    hsv[2] = if (lightTheme) hsv[2].coerceIn(LIGHT_MIN_VALUE, LIGHT_MAX_VALUE) else hsv[2].coerceAtLeast(DARK_MIN_VALUE)
    val cover = listOfNotNull(palette.dominantSwatch, vivid, palette.lightVibrantSwatch, palette.mutedSwatch)
    return ArtworkColors(
        accent = Color(android.graphics.Color.HSVToColor(hsv)),
        tint = Color(deep.rgb),
        coverColors = cover.map { Color(it.rgb) }.distinct(),
    )
}

/** Visible colour, 0 (grey, black or white) to 1: the spread between the strongest and weakest channel. */
private fun chromaOf(rgb: Int): Float {
    val r = android.graphics.Color.red(rgb)
    val g = android.graphics.Color.green(rgb)
    val b = android.graphics.Color.blue(rgb)
    return (maxOf(r, g, b) - minOf(r, g, b)) / CHANNEL_MAX
}

private const val PALETTE_SOURCE_PX = 128
private const val HSV_SIZE = 3
private const val PALETTE_COLORS = 16
private const val MIN_SATURATION = 0.18f
private const val ACCENT_MIN_SATURATION = 0.45f
private const val DARK_MIN_VALUE = 0.78f
private const val LIGHT_MIN_VALUE = 0.38f
private const val LIGHT_MAX_VALUE = 0.58f
private const val MIN_CHROMA = 0.12f
private const val CHANNEL_MAX = 255f
