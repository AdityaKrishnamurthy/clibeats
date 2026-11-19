package com.clibeats.data.preferences

import com.clibeats.domain.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Stores the songs picked from search results as a small JSON list in preferences. */
object RecentSearches {
    const val MAX_TRACKS = 20

    private val json = Json { ignoreUnknownKeys = true }

    /** Puts [track] first, drops an older copy of it, and keeps at most [MAX_TRACKS]. */
    fun add(
        current: List<Track>,
        track: Track,
    ): List<Track> = (listOf(track) + current.filterNot { it.id == track.id }).take(MAX_TRACKS)

    private val listSerializer = ListSerializer(StoredTrack.serializer())

    fun encode(tracks: List<Track>): String = json.encodeToString(listSerializer, tracks.map(StoredTrack::from))

    fun decode(value: String?): List<Track> =
        value
            ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
            ?.map(StoredTrack::toTrack)
            .orEmpty()

    @Serializable
    private data class StoredTrack(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val artworkUrl: String? = null,
        val streamUrl: String? = null,
        val providerId: String,
    ) {
        fun toTrack() = Track(id, title, artist, album, durationMs, artworkUrl, streamUrl, providerId)

        companion object {
            fun from(t: Track) =
                StoredTrack(
                    id = t.id,
                    title = t.title,
                    artist = t.artist,
                    album = t.album,
                    durationMs = t.durationMs,
                    artworkUrl = t.artworkUrl,
                    streamUrl = t.streamUrl,
                    providerId = t.providerId,
                )
        }
    }
}
