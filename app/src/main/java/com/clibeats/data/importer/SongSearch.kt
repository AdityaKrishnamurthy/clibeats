package com.clibeats.data.importer

import com.clibeats.domain.model.Track

/**
 * How one Spotify song is looked up on YouTube Music: a few searches, from most to least specific,
 * stopping at the first result [TrackMatcher] trusts.
 *
 * Kept separate from [PlaylistImporter] so the live accuracy test runs exactly the same steps as the app.
 *
 * @param search runs one search; `songsOnly = false` includes music videos. Returns null when YouTube
 * Music couldn't be reached.
 */
class SongSearch(
    private val search: suspend (query: String, songsOnly: Boolean) -> List<Track>?,
) {
    data class Outcome(
        val match: Track?,
        /** False when every search failed to reach YouTube Music (no internet). */
        val reached: Boolean,
        /** Every result seen, for diagnosing songs that weren't matched. */
        val seen: List<Track>,
    )

    suspend fun find(song: SpotifySong): Outcome {
        var reached = false
        val seen = mutableListOf<Track>()
        for ((query, songsOnly) in queries(song)) {
            val results = search(query, songsOnly) ?: continue
            reached = true
            seen += results
            TrackMatcher.pick(song, results)?.let { return Outcome(it, reached = true, seen = seen) }
        }
        // Each search alone found nothing trustworthy; judge everything together (a result can rank
        // lower in one search than another), still with the same rules.
        val pooled = TrackMatcher.pick(song, seen.distinctBy { it.id })
        return Outcome(pooled, reached, seen)
    }

    internal companion object {
        /**
         * Songs search with the main artist, then with all artists (soundtracks credit the singer second),
         * then title + album (film songs are listed under the film), then everything including videos.
         */
        fun queries(song: SpotifySong): List<Pair<String, Boolean>> {
            val title = song.title
            val main = song.artists.firstOrNull().orEmpty()
            val album = TrackMatcher.baseAlbum(song.album)
            return buildList {
                add("$title $main".trim() to true)
                if (song.artists.size > 1) {
                    add("$title ${song.artists.take(MAX_QUERY_ARTISTS).joinToString(" ")}" to true)
                }
                if (album.isNotEmpty() && !album.equals(title, ignoreCase = true)) add("$title $album" to true)
                add(title to true)
                add("$title $main".trim() to false)
            }.distinct()
        }

        private const val MAX_QUERY_ARTISTS = 3
    }
}
