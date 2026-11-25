@file:Suppress("ForbiddenImport", "ReturnCount") // data layer: the import rule is meant for presentation

package com.clibeats.data.link

import com.clibeats.data.importer.SpotifyPlaylist
import com.clibeats.data.importer.SpotifySong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull

/**
 * Spotify's public embed page for a track, album or playlist (no login): the names, artists and lengths of
 * its songs, which are then found on YouTube Music like an imported playlist. Spotify lists up to 100 songs
 * there; bigger playlists need an Exportify file.
 */
object SpotifyEmbed {
    private val NEXT_DATA =
        Regex(
            """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )
    private val json = Json { ignoreUnknownKeys = true }

    fun url(
        kind: String,
        id: String,
    ): String = "https://open.spotify.com/embed/$kind/$id"

    /** The page's songs, named after the track, album or playlist; null when the page isn't readable. */
    fun parse(html: String): SpotifyPlaylist? {
        val data = NEXT_DATA.find(html)?.groupValues?.get(1) ?: return null
        val entity =
            runCatching { json.parseToJsonElement(data) }.getOrNull()
                ?.at("props", "pageProps", "state", "data", "entity") as? JsonObject ?: return null
        val name = entity.text("name") ?: entity.text("title") ?: return null
        val songs =
            when (entity.text("type")) {
                "track" -> {
                    val artists =
                        entity["artists"]?.list()?.mapNotNull { (it as? JsonObject)?.text("name") }.orEmpty()
                    listOf(SpotifySong(name, artists, "", entity.number("duration")))
                }
                else -> {
                    // An album's songs are on that album, which helps find the right version.
                    val album = if (entity.text("type") == "album") name else ""
                    entity["trackList"]?.list().orEmpty().mapNotNull { item ->
                        val row = item as? JsonObject ?: return@mapNotNull null
                        val title = row.text("title") ?: return@mapNotNull null
                        // "Singer, Other Singer"
                        val artists = row.text("subtitle").orEmpty().split(',').map { it.trim(' ', ' ') }
                        SpotifySong(title, artists.filter { it.isNotEmpty() }, album, row.number("duration"))
                    }
                }
            }
        val cover =
            entity.at("coverArt")?.let { (it as? JsonObject)?.get("sources") }?.list()
                ?.mapNotNull { (it as? JsonObject)?.text("url") }?.lastOrNull()
        return if (songs.isEmpty()) null else SpotifyPlaylist(name, songs, cover)
    }

    private fun JsonElement.at(vararg keys: String): JsonElement? =
        keys.fold(this as JsonElement?) { node, key -> (node as? JsonObject)?.get(key) }

    private fun JsonObject.text(key: String): String? = primitive(key)?.contentOrNull

    private fun JsonObject.number(key: String): Long = primitive(key)?.longOrNull ?: 0L

    private fun JsonObject.primitive(key: String): JsonPrimitive? = get(key) as? JsonPrimitive

    private fun JsonElement.list(): List<JsonElement>? = runCatching { jsonArray }.getOrNull()
}
