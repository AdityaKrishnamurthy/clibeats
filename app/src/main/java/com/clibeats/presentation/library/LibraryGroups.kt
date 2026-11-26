package com.clibeats.presentation.library

import com.clibeats.domain.model.Track

/*
 * Builds the Library's Artists and Albums from a list of songs.
 *
 * Songs are credited to several artists in one string ("A.R. Rahman, Ravi Basrur & Karthik",
 * "Justice feat. Tame Impala", local tags like "Artist A; Artist B"). Each name gets its own artist, so a song
 * shows under every artist on it and an artist's count covers all their songs, solo or not.
 */

/** Splits a credit like "A, B & C feat. D" into its artists, in order, without repeats. */
internal fun splitArtists(credit: String): List<String> {
    val names =
        credit
            .split(ARTIST_SEPARATORS)
            .map { it.trim().trim('(', ')').trim() }
            .filter { it.isNotEmpty() }
    return names.distinctBy { it.lowercase() }.ifEmpty { listOf(UNKNOWN_ARTIST) }
}

/** Artists from [tracks]; [playCounts] maps song id to plays. */
internal fun buildArtists(
    tracks: List<Track>,
    playCounts: Map<String, Int>,
    sort: GroupSort,
): List<ArtistGroup> {
    // Group case-insensitively; show the spelling used most often.
    val byKey = LinkedHashMap<String, MutableList<Pair<String, Track>>>()
    tracks.forEach { track ->
        splitArtists(track.artist).forEach { name ->
            byKey.getOrPut(name.lowercase()) { mutableListOf() } += name to track
        }
    }
    val groups =
        byKey.values.map { entries ->
            val name = entries.groupingBy { it.first }.eachCount().maxBy { it.value }.key
            val songs = entries.map { it.second }.distinctBy { it.id }.sortedByDescending { playCounts[it.id] ?: 0 }
            ArtistGroup(
                name = name,
                tracks = songs,
                playCount = songs.sumOf { playCounts[it.id] ?: 0 },
                artworkUrl = songs.firstNotNullOfOrNull { it.artworkUrl },
            )
        }
    return sortGroups(groups, sort, { it.name }, { it.playCount }, { it.trackCount })
}

/** Albums from [tracks] (songs without an album name are left out); [playCounts] maps song id to plays. */
internal fun buildAlbums(
    tracks: List<Track>,
    playCounts: Map<String, Int>,
    sort: GroupSort,
): List<AlbumGroup> {
    val groups =
        tracks
            .filter { it.album.isNotBlank() }
            .groupBy { it.album.trim().lowercase() }
            .values
            .flatMap { sameTitle -> splitByArtist(sameTitle) }
            .map { songs ->
                val artist =
                    songs
                        .map { splitArtists(it.artist).first() }
                        .groupingBy { it }
                        .eachCount()
                        .maxBy { it.value }
                        .key
                AlbumGroup(
                    title = songs.first().album.trim(),
                    artist = artist,
                    tracks = songs.distinctBy { it.id },
                    playCount = songs.sumOf { playCounts[it.id] ?: 0 },
                    artworkUrl = songs.firstNotNullOfOrNull { it.artworkUrl },
                )
            }
    return sortGroups(groups, sort, { it.title }, { it.playCount }, { it.trackCount })
}

/**
 * Songs sharing an album title: one album when they share a main artist with the rest, otherwise one album per
 * main artist (two different albums called "Greatest Hits" stay apart).
 */
private fun splitByArtist(songs: List<Track>): List<List<Track>> {
    val byArtist = songs.groupBy { splitArtists(it.artist).first().lowercase() }
    if (byArtist.size == 1) return listOf(songs)
    // Soundtracks credit a different singer on every song: if no artist has more than one song, keep it whole.
    return if (byArtist.values.all { it.size == 1 }) listOf(songs) else byArtist.values.toList()
}

private fun <T> sortGroups(
    groups: List<T>,
    sort: GroupSort,
    name: (T) -> String,
    plays: (T) -> Int,
    songs: (T) -> Int,
): List<T> {
    val byName = compareBy<T> { name(it).lowercase() }
    return when (sort) {
        GroupSort.A_TO_Z -> groups.sortedWith(byName)
        GroupSort.MOST_PLAYED ->
            groups.sortedWith(compareByDescending<T> { plays(it) }.thenByDescending { songs(it) }.then(byName))
        GroupSort.MOST_SONGS ->
            groups.sortedWith(compareByDescending<T> { songs(it) }.thenByDescending { plays(it) }.then(byName))
    }
}

/** ", " "&" ";" " / " "feat." "ft." "featuring" "with" (not a plain "/" or "and": "AC/DC", "Florence and the…"). */
private val ARTIST_SEPARATORS =
    Regex(
        """\s*(?:,|&|;|\s/\s|\s+(?:feat\.?|ft\.?|featuring|with)\s+|\(\s*(?:feat\.?|ft\.?)\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )

internal const val UNKNOWN_ARTIST = "Unknown Artist"
