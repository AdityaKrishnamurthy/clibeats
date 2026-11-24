@file:Suppress("ReturnCount")

package com.clibeats.data.link

import java.net.URI
import java.net.URLDecoder

/** A YouTube Music, YouTube or Spotify link opened with CLIBeats (as the default app, or shared to it). */
sealed interface MusicLink {
    data class YouTubeSong(
        val videoId: String,
    ) : MusicLink

    /** A playlist ("VL…") or album ("MPREb_…") page on YouTube Music. */
    data class YouTubePage(
        val browseId: String,
    ) : MusicLink

    data class SpotifyTrack(
        val id: String,
    ) : MusicLink

    /** A Spotify playlist or album; [kind] is "playlist" or "album". */
    data class SpotifyCollection(
        val kind: String,
        val id: String,
    ) : MusicLink

    /** A spotify.link short link: it has to be followed to the real one first. */
    data class SpotifyShort(
        val url: String,
    ) : MusicLink

    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val SPOTIFY_ID = Regex("[A-Za-z0-9]{22}")
        private val URL_IN_TEXT = Regex("""https?://[^\s"'<>]+""")
        private const val TRAILING_PUNCTUATION = ".,)!?;:"

        /** The first link in shared text ("Listen to X on Spotify: https://..."). */
        fun findIn(text: String): MusicLink? =
            URL_IN_TEXT.findAll(text).firstNotNullOfOrNull { parse(withoutTrailingPunctuation(it.value)) }

        private fun withoutTrailingPunctuation(url: String) = url.trimEnd { it in TRAILING_PUNCTUATION }

        fun parse(link: String): MusicLink? {
            val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
            val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
            val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
            return when (host) {
                "music.youtube.com", "youtube.com" -> youtube(segments, queryOf(uri.rawQuery))
                "youtu.be" -> segments.firstOrNull()?.let(::song)
                "open.spotify.com" -> spotify(segments)
                "spotify.link", "spotify.app.link" -> SpotifyShort(link.trim())
                else -> null
            }
        }

        private fun youtube(
            segments: List<String>,
            query: Map<String, String>,
        ): MusicLink? =
            when (segments.firstOrNull()) {
                // A song played from a playlist ("watch?v=…&list=…") is the song.
                "watch" -> query["v"]?.let(::song)
                "shorts", "embed", "live" -> segments.getOrNull(1)?.let(::song)
                "playlist" -> query["list"]?.takeIf { it.isNotBlank() }?.let { YouTubePage("VL$it") }
                "browse" ->
                    segments.getOrNull(1)?.takeIf { it.startsWith("MPREb_") || it.startsWith("VL") }
                        ?.let(::YouTubePage)
                else -> null
            }

        private fun song(id: String): MusicLink? = id.takeIf { VIDEO_ID.matches(it) }?.let(::YouTubeSong)

        private fun spotify(segments: List<String>): MusicLink? {
            // open.spotify.com/intl-de/track/… is the same link in another language.
            val path = if (segments.firstOrNull()?.startsWith("intl-") == true) segments.drop(1) else segments
            val id = path.getOrNull(1)?.takeIf { SPOTIFY_ID.matches(it) } ?: return null
            return when (val kind = path.first()) {
                "track" -> SpotifyTrack(id)
                "album", "playlist" -> SpotifyCollection(kind, id)
                else -> null
            }
        }

        private fun queryOf(raw: String?): Map<String, String> =
            raw.orEmpty().split('&').mapNotNull { pair ->
                val key = pair.substringBefore('=', "")
                if (key.isEmpty()) null else key to URLDecoder.decode(pair.substringAfter('='), "UTF-8")
            }.toMap()
    }
}
