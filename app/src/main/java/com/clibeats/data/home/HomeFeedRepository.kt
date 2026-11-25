@file:Suppress("ForbiddenImport", "ReturnCount")

package com.clibeats.data.home

import android.content.Context
import com.clibeats.data.provider.YouTubeMusicProvider
import com.clibeats.data.provider.api.InnerTubeApi
import com.clibeats.data.provider.dto.BrowseRequest
import com.clibeats.data.provider.mapper.feedContinuation
import com.clibeats.data.provider.mapper.homeMoods
import com.clibeats.data.provider.mapper.homeShelves
import com.clibeats.domain.model.HomeFeed
import com.clibeats.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music's home feed (no account needed; it follows the phone's country), and the playlists and
 * albums it links to. The unfiltered feed is also kept on disk, so Home still has something to show when
 * the app starts offline.
 */
@Singleton
class HomeFeedRepository
    @Inject
    constructor(
        private val api: InnerTubeApi,
        private val youTube: YouTubeMusicProvider,
        @ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val cacheFile: File get() = File(context.filesDir, CACHE_FILE)

        /**
         * The feed, filtered to a mood when [moodParams] is set. Falls back to the copy on disk (unfiltered
         * feed only) when YouTube Music can't be reached; null when there's nothing to show.
         */
        suspend fun feed(moodParams: String? = null): HomeFeed? =
            withContext(Dispatchers.IO) {
                val pages = runCatching { fetchPages(moodParams) }.getOrNull()
                if (!pages.isNullOrEmpty()) {
                    val feed = parse(pages)
                    if (moodParams == null && feed.shelves.isNotEmpty()) save(pages)
                    feed.takeIf { it.shelves.isNotEmpty() }
                } else if (moodParams == null) {
                    readSaved()?.let(::parse)?.takeIf { it.shelves.isNotEmpty() }
                } else {
                    null
                }
            }

        /** A playlist, album or artist page: its name (blank if it has none) and songs. Null when offline. */
        suspend fun collection(browseId: String): Pair<String, List<Track>>? = youTube.page(browseId)

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

        private companion object {
            const val CACHE_FILE = "home_feed.json"
            const val MAX_CONTINUATIONS = 4
        }
    }
