@file:Suppress("ForbiddenImport", "ReturnCount")

package com.clibeats.data.provider.mapper

import com.clibeats.domain.model.CollectionKind
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeMood
import com.clibeats.domain.model.HomeShelf
import com.clibeats.domain.model.Track
import kotlinx.serialization.json.JsonElement

private const val PROVIDER_ID = "youtube_music"

/**
 * The rows of a home feed page (the first part or a continuation): every carousel shelf with its songs and
 * cards. Items the app can't open (podcasts, channels, episodes) are left out, and so are rows left empty.
 */
fun JsonElement.homeShelves(): List<HomeShelf> =
    findAll(this, "musicCarouselShelfRenderer").mapNotNull { shelf ->
        val header = shelf.nav("header", "musicCarouselShelfBasicHeaderRenderer")
        val title = header?.nav("title")?.runsText().orEmpty()
        val items = shelf.nav("contents")?.safeArray().orEmpty().mapNotNull { it.homeItem() }
        if (title.isBlank() || items.isEmpty()) {
            null
        } else {
            HomeShelf(title, header?.nav("strapline")?.runsText().orEmpty(), items)
        }
    }

/** The mood chips at the top of the home feed ("Relax", "Workout", ...), without Podcasts. */
fun JsonElement.homeMoods(): List<HomeMood> =
    findAll(this, "chipCloudChipRenderer").mapNotNull { chip ->
        val title = chip.nav("text")?.runsText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val params = chip.nav("navigationEndpoint", "browseEndpoint", "params")?.safeString() ?: return@mapNotNull null
        if (title.equals("Podcasts", ignoreCase = true)) null else HomeMood(title, params)
    }.distinctBy { it.title }

/** The Moods & genres page's buttons: name ("Kannada", "J-Pop") to the page's browse id and params. */
fun JsonElement.genreButtons(): Map<String, Pair<String, String>> =
    findAll(this, "musicNavigationButtonRenderer").mapNotNull { button ->
        val name = button.nav("buttonText")?.runsText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val browse = button.nav("clickCommand", "browseEndpoint") ?: return@mapNotNull null
        val id = browse.nav("browseId")?.safeString() ?: return@mapNotNull null
        val params = browse.nav("params")?.safeString() ?: return@mapNotNull null
        name to (id to params)
    }.toMap()

/** Token for the next part of a feed, or null when there's no more. */
fun JsonElement.feedContinuation(): String? =
    findAll(this, "nextContinuationData").firstNotNullOfOrNull { it.nav("continuation")?.safeString() }

private fun JsonElement.homeItem(): HomeItem? {
    nav("musicResponsiveListItemRenderer")?.let { row -> return row.parseTrack()?.let(HomeItem::Song) }
    val card = nav("musicTwoRowItemRenderer") ?: return null
    val title = card.nav("title")?.runsText()?.takeIf { it.isNotBlank() } ?: return null
    val artwork =
        card.nav("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails")?.safeArray()
            ?.mapNotNull { it.nav("url")?.safeString() }?.lastOrNull()
    val subtitleRuns = card.nav("subtitle", "runs")?.safeArray().orEmpty().mapNotNull { it.nav("text")?.safeString() }
    val endpoint = card.nav("navigationEndpoint") ?: return null

    // A song or video card plays straight away.
    endpoint.nav("watchEndpoint", "videoId")?.safeString()?.let { videoId ->
        val subtitle = parseSubtitle(subtitleRuns)
        return HomeItem.Song(
            Track(
                id = videoId,
                title = title,
                artist = subtitle.artist,
                album = subtitle.album,
                durationMs = parseDurationMs(subtitle.duration),
                artworkUrl = artwork,
                streamUrl = null,
                providerId = PROVIDER_ID,
                isVideo =
                    endpoint.nav("watchEndpoint", "watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig")
                        ?.nav("musicVideoType")?.safeString()?.let(::isVideoType) == true,
            ),
        )
    }
    val browse = endpoint.nav("browseEndpoint") ?: return null
    val browseId = browse.nav("browseId")?.safeString() ?: return null
    val pageType =
        browse.nav("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")
            ?.safeString()
    val kind =
        when (pageType) {
            "MUSIC_PAGE_TYPE_PLAYLIST" -> CollectionKind.PLAYLIST
            "MUSIC_PAGE_TYPE_ALBUM" -> CollectionKind.ALBUM
            "MUSIC_PAGE_TYPE_ARTIST" -> CollectionKind.ARTIST
            else -> return null
        }
    return HomeItem.Collection(browseId, title, subtitleRuns.joinToString(""), artwork, kind)
}

private fun JsonElement.runsText(): String =
    nav("runs")?.safeArray().orEmpty().mapNotNull { it.nav("text")?.safeString() }.joinToString("").trim()
