package com.clibeats.domain.model

import kotlinx.serialization.Serializable

/**
 * YouTube Music's home page for the phone's country: rows of songs and playlist / album cards, and the mood
 * chips ("Relax", "Workout", ...) that filter it.
 */
@Serializable
data class HomeFeed(
    val moods: List<HomeMood>,
    val shelves: List<HomeShelf>,
)

/** A mood chip; [params] asks YouTube Music for the home feed filtered to that mood. */
@Serializable
data class HomeMood(
    val title: String,
    val params: String,
)

/** One row of the home page, e.g. "Quick picks" (songs) or "Trending community playlists" (cards). */
@Serializable
data class HomeShelf(
    val title: String,
    /** The small line above some titles ("COMFY & COZY, AS EVENINGS SHOULD BE"); may be blank. */
    val strapline: String,
    val items: List<HomeItem>,
) {
    val songs: List<Track> get() = items.filterIsInstance<HomeItem.Song>().map { it.track }
}

@Serializable
sealed interface HomeItem {
    /** A song: tapping plays it. */
    @Serializable
    data class Song(
        val track: Track,
    ) : HomeItem

    /** A playlist, album or artist: tapping opens its songs. */
    @Serializable
    data class Collection(
        val browseId: String,
        val title: String,
        /** "Album • Artist", the playlist's artists, or "Creator • 10M views". */
        val subtitle: String,
        val artworkUrl: String?,
        val kind: CollectionKind,
    ) : HomeItem
}

@Serializable
enum class CollectionKind { PLAYLIST, ALBUM, ARTIST }
