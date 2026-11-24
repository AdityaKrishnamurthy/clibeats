@file:Suppress(
    "ForbiddenImport",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "TooManyFunctions",
)

package com.clibeats.data.provider.mapper

import com.clibeats.data.provider.dto.BrowseResponse
import com.clibeats.data.provider.dto.PlayerResponse
import com.clibeats.data.provider.dto.SearchResponse
import com.clibeats.data.provider.dto.VideoDetails
import com.clibeats.domain.model.Track
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val PROVIDER_ID = "youtube_music"
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val MS_PER_SECOND = 1000L

/**
 * Parse InnerTube search response into a list of Track domain objects.
 * Navigation through the JSON tree is fully null-safe.
 * Tracks missing a videoId are skipped.
 */
fun SearchResponse.toTrackList(): List<Track> {
    val contents = this.contents ?: return emptyList()

    // Navigate: contents → tabbedSearchResultsRenderer → tabs[0] → tabRenderer
    //          → content → sectionListRenderer → contents[*]
    //          → musicShelfRenderer → contents[*] → musicResponsiveListItemRenderer
    val tabs = contents.nav("tabbedSearchResultsRenderer", "tabs") ?: return emptyList()
    val tabContent =
        tabs.safeArray()?.getOrNull(0)
            ?.nav("tabRenderer", "content") ?: return emptyList()

    val shelves =
        tabContent.nav("sectionListRenderer", "contents")
            ?.safeArray() ?: return emptyList()

    val tracks = mutableListOf<Track>()
    for (shelf in shelves) {
        val items =
            shelf.nav("musicShelfRenderer", "contents")
                ?.safeArray() ?: continue

        for (item in items) {
            val renderer = item.nav("musicResponsiveListItemRenderer") ?: continue
            val track = renderer.parseTrack() ?: continue
            tracks.add(track)
        }
    }
    return tracks
}

/** An album, single or EP from an albums search. Its page lists every track with its video id. */
data class AlbumHit(
    val browseId: String,
    val title: String,
    val artist: String,
    /** The album cover; album pages don't repeat it on each track row, so tracks take it from here. */
    val artworkUrl: String? = null,
)

/** Albums from an albums-filtered search, in result order. */
fun SearchResponse.toAlbumHits(): List<AlbumHit> {
    val root = contents ?: return emptyList()
    return findAll(root, "musicResponsiveListItemRenderer").mapNotNull { item ->
        val browseId =
            item.nav("navigationEndpoint", "browseEndpoint", "browseId")?.safeString()
                ?.takeIf { it.startsWith("MPRE") } ?: return@mapNotNull null
        val columns = item.nav("flexColumns")?.safeArray() ?: return@mapNotNull null

        fun column(i: Int) =
            columns.getOrNull(i)?.nav("musicResponsiveListItemFlexColumnRenderer", "text", "runs")?.safeArray()
                ?.mapNotNull { it.nav("text")?.safeString() }.orEmpty()
        val title = column(0).joinToString("").ifBlank { return@mapNotNull null }
        val artwork =
            item.nav("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
                ?.safeArray()
                ?.mapNotNull { it.nav("url")?.safeString() }
                ?.lastOrNull()
        AlbumHit(browseId, title, parseSubtitle(column(1)).artist, artwork)
    }
}

/**
 * The chart playlist to show as "trending" from a Charts page: the daily top music videos for the country
 * (real songs), else the top 100, else the trending list, else the first chart playlist. Returns its
 * browse id (`VL...`).
 */
fun BrowseResponse.chartPlaylistBrowseId(): String? {
    val playlists =
        findAll(contents ?: return null, "musicTwoRowItemRenderer").mapNotNull { item ->
            val title = item.nav("title", "runs")?.safeArray()?.getOrNull(0)?.nav("text")?.safeString()
            val id = item.nav("navigationEndpoint", "browseEndpoint", "browseId")?.safeString()
            if (title != null && id != null && id.startsWith("VL")) title to id else null
        }
    val preferences = listOf("daily top music videos", "top 100 music videos", "top songs", "trending")
    return preferences.firstNotNullOfOrNull { wanted ->
        playlists.firstOrNull { (title, _) -> title.lowercase().startsWith(wanted) }?.second
    } ?: playlists.firstOrNull()?.second
}

/** The name of a playlist or album page ("Top 100 Songs Global"); null when the page has no header. */
fun BrowseResponse.pageTitle(): String? {
    val root = contents ?: return null
    return (findAll(root, "musicResponsiveHeaderRenderer") + findAll(root, "musicDetailHeaderRenderer"))
        .firstNotNullOfOrNull { it.nav("title", "runs")?.safeArray()?.firstOrNull()?.nav("text")?.safeString() }
}

/** An album page's artist ("strapline" under the title); null on other pages. */
fun BrowseResponse.pageArtist(): String? {
    val root = contents ?: return null
    return findAll(root, "straplineTextOne")
        .firstNotNullOfOrNull { it.nav("runs")?.safeArray()?.firstOrNull()?.nav("text")?.safeString() }
}

/** The page header's cover, largest size. */
fun BrowseResponse.pageCover(): String? {
    val root = contents ?: return null
    val header =
        (findAll(root, "musicResponsiveHeaderRenderer") + findAll(root, "musicDetailHeaderRenderer")).firstOrNull()
            ?: return null
    return findAll(header, "thumbnails").firstOrNull()?.safeArray()?.mapNotNull { it.nav("url")?.safeString() }
        ?.lastOrNull()
}

/** The song behind a video id, from the player's details (a shared link has nothing else). */
fun VideoDetails.toTrack(): Track? {
    val id = videoId ?: return null
    return Track(
        id = id,
        title = title ?: return null,
        artist = author.orEmpty().removeSuffix(" - Topic"),
        album = "",
        durationMs = (lengthSeconds?.toLongOrNull() ?: 0L) * MS_PER_SECOND,
        artworkUrl =
            thumbnail?.nav("thumbnails")?.safeArray()?.mapNotNull { it.nav("url")?.safeString() }?.lastOrNull(),
        streamUrl = null,
        providerId = PROVIDER_ID,
    )
}

/** Songs of a playlist page (e.g. a chart), in order; items without a video id are skipped. */
fun BrowseResponse.playlistTracks(): List<Track> {
    val root = contents ?: return emptyList()
    return findAll(root, "musicResponsiveListItemRenderer")
        .mapNotNull { it.parseTrack() }
        .distinctBy { it.id }
}

/** Every value stored under [key] anywhere in the tree, in document order. */
private fun findAll(
    root: JsonElement,
    key: String,
): List<JsonElement> {
    val found = mutableListOf<JsonElement>()

    fun walk(element: JsonElement) {
        when (element) {
            is JsonObject ->
                element.forEach { (k, v) ->
                    if (k == key) found += v else walk(v)
                }
            is kotlinx.serialization.json.JsonArray -> element.forEach(::walk)
            else -> Unit
        }
    }
    walk(root)
    return found
}

private fun JsonElement.parseTrack(): Track? {
    val flexCols = this.nav("flexColumns")?.safeArray() ?: return null

    // Column 0: title + videoId
    val col0 =
        flexCols.getOrNull(0)
            ?.nav("musicResponsiveListItemFlexColumnRenderer", "text", "runs")
            ?.safeArray()?.getOrNull(0) ?: return null

    val title = col0.nav("text")?.safeString() ?: return null
    val videoId =
        this.nav("playlistItemData", "videoId")?.safeString()
            ?: this.nav("navigationEndpoint", "watchEndpoint", "videoId")?.safeString()
            ?: col0.nav("navigationEndpoint", "watchEndpoint", "videoId")?.safeString()
            ?: return null

    // Column 1: artist • album • duration (runs array)
    val col1Runs =
        flexCols.getOrNull(1)
            ?.nav("musicResponsiveListItemFlexColumnRenderer", "text", "runs")
            ?.safeArray() ?: emptyList()

    val subtitle = parseSubtitle(col1Runs.mapNotNull { it.nav("text")?.safeString() })
    val artist = subtitle.artist
    val album = subtitle.album
    val durationStr = subtitle.duration
    // Playlists (charts) put the duration in a fixed column instead of the artist line.
    val fixedDuration =
        this.nav("fixedColumns")?.safeArray()?.getOrNull(0)
            ?.nav("musicResponsiveListItemFixedColumnRenderer", "text", "runs")
            ?.safeArray()?.getOrNull(0)?.nav("text")?.safeString()
    val durationMs = parseDurationMs(durationStr).takeIf { it > 0 } ?: parseDurationMs(fixedDuration ?: "")

    // Thumbnail — pick highest resolution
    val artworkUrl =
        this.nav("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
            ?.safeArray()
            ?.mapNotNull { it.nav("url")?.safeString() }
            ?.lastOrNull()

    return Track(
        id = videoId,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        // streamUrl populated separately via /player
        streamUrl = null,
        providerId = PROVIDER_ID,
        isVideo = findAll(this, "musicVideoType").any { it.safeString()?.let(::isVideoType) == true },
    )
}

/** A music video or user upload, as opposed to the official audio track ("ATV"). */
internal fun isVideoType(musicVideoType: String) = musicVideoType != "MUSIC_VIDEO_TYPE_ATV"

/** The artist · album · duration line of a result, split into its parts. */
internal data class Subtitle(
    val artist: String,
    val album: String,
    val duration: String,
)

/**
 * Splits a result's second line, which YouTube Music sends as text runs joined by " • ":
 * `[artist(s)] • [album] • [duration]` for songs, `[artist] • [views] • [duration]` for videos, with a
 * leading type label ("Song", "Video") in unfiltered searches. Several artists arrive as separate runs
 * ("A", " & ", "B") inside the first part and are kept together, not mistaken for the album.
 */
internal fun parseSubtitle(runs: List<String>): Subtitle {
    val parts = mutableListOf(StringBuilder())
    runs.forEach { text -> if (text.trim() == "•") parts += StringBuilder() else parts.last().append(text) }
    var fields = parts.map { it.toString().trim() }.filter { it.isNotEmpty() }
    if (fields.size > 1 && fields.first() in RESULT_TYPE_LABELS) fields = fields.drop(1)
    val duration = fields.lastOrNull()?.takeIf { parseDurationMs(it) > 0 }.orEmpty()
    val middle = if (duration.isNotEmpty()) fields.drop(1).dropLast(1) else fields.drop(1)
    val album = middle.firstOrNull { !it.endsWith(" views") && !it.endsWith(" plays") }.orEmpty()
    return Subtitle(artist = fields.firstOrNull().orEmpty(), album = album, duration = duration)
}

private val RESULT_TYPE_LABELS = setOf("Song", "Video", "Single", "EP", "Album", "Episode", "Podcast")

/**
 * Parse duration string "4:19" → 259_000L ms. Returns 0 on parse failure.
 */
internal fun parseDurationMs(duration: String): Long {
    val parts = duration.trim().split(":").mapNotNull { it.toLongOrNull() }
    return when (parts.size) {
        2 -> (parts[0] * SECONDS_PER_MINUTE + parts[1]) * MS_PER_SECOND
        3 -> (parts[0] * SECONDS_PER_HOUR + parts[1] * SECONDS_PER_MINUTE + parts[2]) * MS_PER_SECOND
        else -> 0L
    }
}

/**
 * Extract the best audio stream URL from an InnerTube player response.
 * Looks for audio/mp4 or audio/webm formats.
 */
fun PlayerResponse.extractStreamUrl(): String? {
    val streamingData = this.streamingData ?: return null
    val formats =
        streamingData.nav("adaptiveFormats")?.safeArray()
            ?: streamingData.nav("formats")?.safeArray()
            ?: return null

    val audioFormats =
        formats.filter { item ->
            val mimeType = item.nav("mimeType")?.safeString() ?: ""
            mimeType.startsWith("audio/")
        }

    // Prefer mp4 audio, fall back to any audio format
    return audioFormats.firstOrNull { item ->
        item.nav("mimeType")?.safeString()?.contains("mp4") == true
    }?.nav("url")?.safeString()
        ?: audioFormats.firstOrNull()?.nav("url")?.safeString()
}

// ── Navigation helpers ─────────────────────────────────────────────────────

private fun JsonElement.nav(vararg keys: String): JsonElement? {
    var current: JsonElement = this
    for (key in keys) {
        current =
            when (current) {
                is JsonObject -> current.jsonObject[key] ?: return null
                else -> return null
            }
    }
    return current
}

private fun JsonElement.safeArray(): List<JsonElement>? = runCatching { this.jsonArray }.getOrNull()

private fun JsonElement.safeString(): String? = runCatching { this.jsonPrimitive.contentOrNull }.getOrNull()
