package com.clibeats.presentation.component

private val GOOGLE_SIZE_SPEC = Regex("""=(?:w\d+-h\d+|s\d+)[^/=]*$""")
private val YTIMG_THUMB = Regex("""(/vi(?:_webp)?/[^/]+/)[^/]+\.(?:jpg|webp)""")
private const val YTIMG_HQ_MAX_PX = 480

/**
 * Asks the image host for artwork at [sizePx] instead of the tiny thumbnail search returns.
 * googleusercontent/ggpht: rewrite the trailing `=w120-h120...` / `=s120...` size spec.
 * i.ytimg.com: `hqdefault` (480px, always exists) for small sizes, `maxresdefault` for large;
 * callers showing large art should fall back to [artworkUrlFor] with a small size on error.
 * Other hosts are returned unchanged.
 */
fun artworkUrlFor(
    url: String?,
    sizePx: Int,
): String? {
    if (url.isNullOrBlank()) return url
    return when {
        "googleusercontent.com" in url || "ggpht.com" in url ->
            if (GOOGLE_SIZE_SPEC.containsMatchIn(url)) {
                url.replace(GOOGLE_SIZE_SPEC, "=w$sizePx-h$sizePx-l90-rj")
            } else {
                url
            }
        "ytimg.com" in url -> {
            val name = if (sizePx <= YTIMG_HQ_MAX_PX) "hqdefault.jpg" else "maxresdefault.jpg"
            url.substringBefore('?').replace(YTIMG_THUMB, "$1$name")
        }
        else -> url
    }
}

/**
 * One thumbnail size for every small artwork (lists, mini player, dialogs) so each cover is
 * downloaded and cached once, and Now Playing can show it instantly as a placeholder.
 */
const val ARTWORK_THUMB_PX = 128

/** Now Playing / large artwork size. */
const val ARTWORK_FULL_PX = 1080
