package com.clibeats.presentation.library

import com.clibeats.domain.model.Track

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data object Empty : LibraryUiState

    data class Success(
        val tracks: List<Track>,
        val artists: List<ArtistGroup>,
        val albums: List<AlbumGroup>,
    ) : LibraryUiState
}

/** One artist in the Library: their songs (most played first), how often they were played, and a cover. */
data class ArtistGroup(
    val name: String,
    val tracks: List<Track>,
    val playCount: Int,
    val artworkUrl: String?,
) {
    val trackCount: Int get() = tracks.size
}

/** One album in the Library: its songs, its main artist, how often they were played, and its cover. */
data class AlbumGroup(
    val title: String,
    val artist: String,
    val tracks: List<Track>,
    val playCount: Int,
    val artworkUrl: String?,
) {
    val trackCount: Int get() = tracks.size

    /** Stable id for lists and for reopening (titles repeat across artists). */
    val key: String get() = "${title.lowercase()}|${artist.lowercase()}"
}

/** How Artists and Albums are ordered. */
enum class GroupSort(
    val label: String,
) {
    RECENT("RECENT"),
    MOST_PLAYED("MOST PLAYED"),
    MOST_SONGS("MOST SONGS"),
    A_TO_Z("A–Z"),
}

/** An artist or album opened from the Library (its songs are looked up live, so they stay current). */
sealed interface OpenedGroup {
    data class Artist(
        val name: String,
    ) : OpenedGroup

    data class Album(
        val key: String,
    ) : OpenedGroup
}
