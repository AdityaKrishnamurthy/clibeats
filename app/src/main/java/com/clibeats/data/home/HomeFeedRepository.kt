@file:Suppress("ForbiddenImport", "ReturnCount", "TooManyFunctions")

package com.clibeats.data.home

import android.content.Context
import com.clibeats.data.importer.PlaylistImporter
import com.clibeats.data.importer.SpotifyPlaylist
import com.clibeats.data.link.LinkResolver
import com.clibeats.data.provider.YouTubeMusicProvider
import com.clibeats.data.provider.api.InnerTubeApi
import com.clibeats.data.provider.dto.BrowseRequest
import com.clibeats.data.provider.dto.UserLocale
import com.clibeats.data.provider.mapper.feedContinuation
import com.clibeats.data.provider.mapper.genreButtons
import com.clibeats.data.provider.mapper.homeMoods
import com.clibeats.data.provider.mapper.homeShelves
import com.clibeats.domain.model.CollectionKind
import com.clibeats.domain.model.HomeFeed
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeShelf
import com.clibeats.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Songs of a Spotify chart found on YouTube Music so far, in chart order; [done] once every song was tried. */
data class ChartProgress(
    val tracks: List<Track>,
    val tried: Int,
    val total: Int,
    val done: Boolean,
)

/**
 * The online Home: YouTube Music's home feed (no account needed; it follows the phone's country), tuned to
 * the picked languages:
 * - rows and items in other languages are hidden ([HomeLanguageFilter]);
 * - each picked language adds its songs and playlists (its YouTube Music genre page);
 * - a "Charts & Top 50" row: YouTube's charts for the languages' countries and Spotify's Top 50 lists.
 *
 * The unfiltered feed is also kept on disk, so Home still has something to show when the app starts offline.
 */
@Singleton
class HomeFeedRepository
    @Inject
    constructor(
        private val api: InnerTubeApi,
        private val youTube: YouTubeMusicProvider,
        private val linkResolver: LinkResolver,
        private val importer: PlaylistImporter,
        @ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val cacheFile: File get() = File(context.filesDir, CACHE_FILE)

        /** Genre name -> (browseId, params), read once per app run. */
        @Volatile private var genres: Map<String, Pair<String, String>>? = null
        private val spotifyLists = ConcurrentHashMap<String, SpotifyPlaylist>()
        private val matchedCharts = ConcurrentHashMap<String, List<Track>>()

        /**
         * The page, filtered to a mood when [moodParams] is set (then only the mood's rows, filtered by
         * language). Falls back to the copy on disk when YouTube Music can't be reached; null when there's
         * nothing to show.
         */
        suspend fun feed(
            moodParams: String? = null,
            languages: Set<HomeLanguage> = emptySet(),
            /** Refresh: also re-read the Spotify charts (otherwise kept for the app run). */
            refresh: Boolean = false,
        ): HomeFeed? =
            withContext(Dispatchers.IO) {
                if (refresh) {
                    spotifyLists.clear()
                    matchedCharts.clear()
                }
                coroutineScope {
                    // The extra rows load alongside the feed.
                    val extras = if (moodParams == null) async { extraRows(languages) } else null
                    val pages = runCatching { fetchPages(moodParams) }.getOrNull()
                    val main =
                        when {
                            !pages.isNullOrEmpty() -> parse(pages).also { if (moodParams == null) save(pages) }
                            moodParams == null -> readSaved()?.let(::parse)
                            else -> null
                        }
                    val extra = extras?.await() ?: ExtraRows()
                    val shelves = HomeLanguageFilter.filter(main?.shelves.orEmpty(), languages)
                    // What's trending where you are, then the charts, then your languages, then the feed
                    // (Quick picks and the rest).
                    val trending = extra.trending?.let { HomeLanguageFilter.filter(listOf(it), languages) }.orEmpty()
                    val ordered = trending + listOfNotNull(extra.charts) + extra.languages + shelves
                    ordered.takeIf { it.isNotEmpty() }?.let { HomeFeed(main?.moods.orEmpty(), it) }
                }
            }

        /** Playlists on YouTube Music matching [query], for Search; null when it can't be reached. */
        suspend fun searchPlaylists(query: String): List<HomeItem.Collection>? = youTube.searchPlaylists(query)

        /** A playlist, album or artist page: its name (blank if it has none) and songs. Null when offline. */
        suspend fun collection(browseId: String): Pair<String, List<Track>>? = youTube.page(browseId)

        /**
         * A Spotify chart's songs, found on YouTube Music one by one (like an import) and reported as they're
         * found, in chart order. Remembered for the rest of the app run once complete.
         */
        fun spotifyChart(id: String): Flow<ChartProgress> =
            channelFlow {
                matchedCharts[id]?.let { done ->
                    send(ChartProgress(done, done.size, done.size, done = true))
                    return@channelFlow
                }
                val list = spotifyLists[id] ?: linkResolver.spotify("playlist", id)?.also { spotifyLists[id] = it }
                if (list == null) {
                    send(ChartProgress(emptyList(), 0, 0, done = true))
                    return@channelFlow
                }
                val total = list.songs.size
                val found = arrayOfNulls<Track>(total)
                var tried = 0
                val lock = Mutex()
                val slots = Semaphore(MATCH_PARALLEL)
                coroutineScope {
                    list.songs.forEachIndexed { index, song ->
                        launch {
                            val track = slots.withPermit { runCatching { importer.findSong(song) }.getOrNull() }
                            lock.withLock {
                                found[index] = track
                                tried++
                                if (tried < total) {
                                    send(ChartProgress(found.filterNotNull(), tried, total, done = false))
                                }
                            }
                        }
                    }
                }
                val tracks = found.filterNotNull()
                if (tracks.isNotEmpty()) matchedCharts[id] = tracks
                send(ChartProgress(tracks, total, total, done = true))
            }.flowOn(Dispatchers.IO)

        // ── Extra rows ──

        /** Home's own rows: trending where you are, charts, and your languages. */
        private data class ExtraRows(
            val trending: HomeShelf? = null,
            val charts: HomeShelf? = null,
            val languages: List<HomeShelf> = emptyList(),
        )

        private suspend fun extraRows(languages: Set<HomeLanguage>): ExtraRows =
            coroutineScope {
                val charts = async { runCatching { chartsRows(languages) }.getOrNull() }
                val rows =
                    languages.take(MAX_LANGUAGE_ROWS).map { language ->
                        async { runCatching { languageRows(language) }.getOrNull().orEmpty() }
                    }
                val (trending, chartsRow) = charts.await() ?: (null to null)
                ExtraRows(trending, chartsRow, rows.awaitAll().flatten())
            }

        /** A language's popular songs and featured playlists, from its YouTube Music genre page. */
        private suspend fun languageRows(language: HomeLanguage): List<HomeShelf> {
            if (language.genre == null) return emptyList()
            val all = genres ?: api.browse(BrowseRequest.page(GENRES_ID)).contents?.genreButtons()?.also { genres = it }
            val genre = language.genre ?: return emptyList()
            val (browseId, params) = all?.get(genre) ?: return emptyList()
            val shelves = api.browse(BrowseRequest.category(browseId, params)).contents?.homeShelves().orEmpty()
            val songs = shelves.firstOrNull { it.title == GENRE_SONGS && it.songs.isNotEmpty() }
            val playlists = shelves.firstOrNull { it.title == GENRE_PLAYLISTS }
            return listOfNotNull(
                songs?.copy(title = "${language.label} songs", strapline = "", items = songs.items.take(ROW_SONGS)),
                playlists?.copy(title = "${language.label} playlists", strapline = ""),
            )
        }

        /**
         * "Trending in <country>" (the songs of YouTube's Trending 20 where the phone is) and the
         * "Charts & Top 50" row: Spotify's Top 50 Global, your country's, and your languages'; then YouTube's
         * charts for your country and your languages' countries (Top 100 videos, Trending 20, and India's
         * weekly chart for each picked Indian language).
         */
        private suspend fun chartsRows(languages: Set<HomeLanguage>): Pair<HomeShelf?, HomeShelf?> =
            coroutineScope {
                val home = UserLocale.gl
                val countries = (listOf(home) + languages.map { it.chartCountry }).distinct()
                val spotifyCharts =
                    (
                        listOf(HomeLanguage.globalChart) + listOfNotNull(HomeLanguage.countryChart(home)) +
                            languages.flatMap { it.spotifyCharts }
                    ).distinct()
                val spotify = spotifyCharts.map { chart -> async { spotifyCard(chart) } }
                val youtube = countries.map { country -> async { country to youTubeCharts(country, languages) } }
                val byCountry = youtube.awaitAll().toMap()

                // Your country's daily top songs become a row at the top (not a card). This is the list the
                // old Trending used: real hits, unlike "Trending 20", which is mostly devotional and regional
                // video uploads.
                val homeCards = byCountry[home].orEmpty()
                val trendingCard =
                    TOP_ROW_CHARTS.firstNotNullOfOrNull { name ->
                        homeCards.firstOrNull { it.title.startsWith(name, ignoreCase = true) }
                    }
                val trending =
                    trendingCard?.let { card ->
                        val songs = runCatching { youTube.page(card.browseId) }.getOrNull()?.second.orEmpty()
                        val place = UserLocale.countryName.ifBlank { UserLocale.gl }
                        val items = songs.take(TOP_ROW_SONGS).map(HomeItem::Song)
                        items.takeIf { it.isNotEmpty() }?.let { HomeShelf("Trending in $place", TRENDING_STRAP, it) }
                    }
                val youTubeCards =
                    countries.flatMap { byCountry[it].orEmpty() }.filterNot { trending != null && it == trendingCard }
                val cards = spotify.awaitAll().filterNotNull() + youTubeCards
                val chartsRow = cards.takeIf { it.isNotEmpty() }?.let { HomeShelf(CHARTS_TITLE, CHARTS_STRAP, it) }
                trending to chartsRow
            }

        private suspend fun spotifyCard(chart: SpotifyChart): HomeItem.Collection? {
            val list =
                spotifyLists[chart.id]
                    ?: withContext(Dispatchers.IO) { linkResolver.spotify("playlist", chart.id) }
                        ?.also { spotifyLists[chart.id] = it }
                    ?: return null
            return HomeItem.Collection(
                browseId = SPOTIFY_PREFIX + chart.id,
                title = list.name.ifBlank { chart.name },
                subtitle = "Spotify · ${list.songs.size} songs",
                artworkUrl = list.coverUrl,
                kind = CollectionKind.PLAYLIST,
            )
        }

        private suspend fun youTubeCharts(
            country: String,
            languages: Set<HomeLanguage>,
        ): List<HomeItem.Collection> {
            val cards =
                api.browse(BrowseRequest.charts(country)).contents?.homeShelves().orEmpty()
                    .flatMap { it.items }
                    .filterIsInstance<HomeItem.Collection>()
                    .filter { it.kind == CollectionKind.PLAYLIST }
            val general = cards.filter { card -> GENERAL_CHARTS.any { card.title.startsWith(it, ignoreCase = true) } }
            val perLanguage =
                cards.filter { card ->
                    card.title.startsWith(WEEKLY_CHART, ignoreCase = true) &&
                        languages.any { card.title.endsWith(it.label, ignoreCase = true) }
                }
            return general + perLanguage
        }

        // ── Feed pages ──

        /** The first part of the feed and up to [MAX_CONTINUATIONS] more (the feed arrives in parts). */
        private suspend fun fetchPages(moodParams: String?): List<JsonElement> {
            val first = api.browse(BrowseRequest.home(moodParams))
            val visitor = first.responseContext?.visitorData
            val pages = mutableListOf(first.contents ?: return emptyList())
            var token = pages.first().feedContinuation()
            repeat(MAX_CONTINUATIONS) {
                val current = token ?: return pages
                // A part that fails only cuts the feed short; the rows already loaded are still shown.
                val next =
                    runCatching {
                        api.browseContinuation(current, current, visitor, BrowseRequest.continuation(visitor))
                    }.getOrNull()?.continuationContents ?: return pages
                pages += next
                token = next.feedContinuation()
            }
            return pages
        }

        private fun parse(pages: List<JsonElement>): HomeFeed =
            HomeFeed(
                moods = pages.first().homeMoods(),
                shelves = pages.flatMap { it.homeShelves() }.distinctBy { it.title },
            )

        private fun save(pages: List<JsonElement>) {
            runCatching { cacheFile.writeText(json.encodeToString(JsonElement.serializer(), JsonArray(pages))) }
        }

        private fun readSaved(): List<JsonElement>? =
            runCatching {
                json.parseToJsonElement(cacheFile.readText()).jsonArray.toList()
            }.getOrNull()?.takeIf { it.isNotEmpty() }

        companion object {
            /** Card ids of Spotify charts start with this (the rest is the Spotify playlist id). */
            const val SPOTIFY_PREFIX = "spotify:"
            private const val CACHE_FILE = "home_feed.json"
            private const val MAX_CONTINUATIONS = 4
            private const val GENRES_ID = "FEmusic_moods_and_genres"
            private const val GENRE_SONGS = "Songs"
            private const val GENRE_PLAYLISTS = "Featured playlists"
            private const val CHARTS_TITLE = "Charts & Top 50"
            private const val WEEKLY_CHART = "Top Weekly Videos"
            private const val TRENDING = "Trending 20"
            private const val DAILY_TOP = "Daily Top Music Videos"
            private const val TOP_100 = "Top 100 Music Videos"

            /** Charts for the "Trending in <country>" row, best first. */
            private val TOP_ROW_CHARTS = listOf(DAILY_TOP, TOP_100, TRENDING)
            private const val TRENDING_STRAP = "WHAT EVERYONE'S PLAYING"
            private const val CHARTS_STRAP = "WHAT'S HOT RIGHT NOW"
            private val GENERAL_CHARTS = listOf(DAILY_TOP, TOP_100, TRENDING)
            private const val MAX_LANGUAGE_ROWS = 6
            private const val ROW_SONGS = 20
            private const val TOP_ROW_SONGS = 24
            private const val MATCH_PARALLEL = 4
        }
    }
