@file:Suppress("ForbiddenImport") // data layer: the rule is meant for presentation

package com.clibeats.data.importer

import com.clibeats.data.provider.mapper.AlbumHit
import com.clibeats.domain.model.Track

/**
 * How one Spotify song is looked up on YouTube Music: several searches, from most to least specific,
 * stopping at the first result [TrackMatcher] trusts.
 *
 * Kept separate from [PlaylistImporter] so the live accuracy test runs exactly the same steps as the app.
 *
 * @param search runs one search; `songsOnly = false` includes music videos. Null = YouTube Music unreachable.
 * @param searchAlbums albums, singles and EPs for a query. Null = unreachable.
 * @param albumTracks every track of an album. Null = unreachable.
 */
class SongSearch(
    private val search: suspend (query: String, songsOnly: Boolean) -> List<Track>?,
    private val searchAlbums: suspend (query: String) -> List<AlbumHit>? = { emptyList() },
    private val albumTracks: suspend (AlbumHit) -> List<Track>? = { emptyList() },
) {
    data class Outcome(
        val match: Track?,
        /** False when every search failed to reach YouTube Music (no internet). */
        val reached: Boolean,
        /** Every result seen, for diagnosing songs that weren't matched. */
        val seen: List<Track>,
        /** The match is another version of the song (allowed by "closest version"), not the exact one. */
        val closest: Boolean = false,
    )

    /**
     * Finds [song]. With [allowClosest], a song that only exists in another version on YouTube Music
     * ("Song (Remix)" when only "Song" is there) takes that version, but only after every exact attempt failed.
     */
    @Suppress("ReturnCount")
    suspend fun find(
        song: SpotifySong,
        allowClosest: Boolean = false,
    ): Outcome {
        var reached = false
        val seen = mutableListOf<Track>()
        for (step in steps(song)) {
            val results = step() ?: continue
            reached = true
            seen += results
            // Stop early only on a certain match. When Spotify names a language, a near-spelling without that
            // language may be another dub of the same film; keep looking and let the best overall win below.
            val match = TrackMatcher.pick(song, results)
            if (match != null && TrackMatcher.isCertainVersion(song, match)) {
                return Outcome(match, reached = true, seen = seen)
            }
        }
        // Each search alone found nothing trustworthy; judge everything together (a result can rank
        // lower in one search than another), still with the same rules.
        val pool = seen.distinctBy { it.id }
        TrackMatcher.pick(song, pool)?.let { return Outcome(it, reached, seen) }
        val closest = if (allowClosest) TrackMatcher.pick(song, pool, allowClosest = true) else null
        return Outcome(closest, reached, seen, closest = closest != null)
    }

    /**
     * In order:
     * 1. song search with the main artist (finds most songs at once);
     * 2. the ISRC (official recording code), which YouTube Music answers for many labels, even when a
     *    title search doesn't;
     * 3. all artists (soundtracks credit the singer second), then title + film/album, then title alone;
     * 4. the Spotify album opened on YouTube Music: some official tracks never show in song search
     *    (YouTube Music leaves some of them out of song search; their album still lists them);
     * 5. music videos too, also with the film and language, for songs with no audio release
     *    (some film songs exist only as the label's videos in a given language).
     */
    private fun steps(song: SpotifySong): List<suspend () -> List<Track>?> {
        val title = song.title
        val main = song.artists.firstOrNull().orEmpty()
        val album = TrackMatcher.baseAlbum(song.album)
        val language = TrackMatcher.languageOf(song)
        val songQueries =
            buildList {
                add("$title $main".trim())
                if (song.isrc.isNotBlank()) add(song.isrc)
                if (song.artists.size > 1) add("$title ${song.artists.take(MAX_QUERY_ARTISTS).joinToString(" ")}")
                if (album.isNotEmpty() && !album.equals(title, ignoreCase = true)) add("$title $album")
                add(title)
            }.distinct()
        val videoQueries =
            listOfNotNull(
                "$title $main".trim(),
                language?.let { "$title $album $it".replace("  ", " ").trim() },
            ).distinct()
        return songQueries.map { q -> suspend { search(q, true) } } +
            listOf(suspend { albumCandidates(song) }) +
            videoQueries.map { q -> suspend { search(q, false) } }
    }

    /**
     * Tracks of the Spotify album (or single) found on YouTube Music: same title or same artist. Film albums
     * exist once per language, so when Spotify names one (an album ending in "[KANNADA]") it is searched with
     * that language first; otherwise the other language versions crowd it out.
     */
    private suspend fun albumCandidates(song: SpotifySong): List<Track>? {
        val album = TrackMatcher.baseAlbum(song.album)
        if (album.isEmpty()) return emptyList()
        val queries =
            listOfNotNull(
                TrackMatcher.languageOf(song)?.let { "$album $it" },
                "$album ${song.artists.firstOrNull().orEmpty()}".trim(),
            ).distinct()
        var reached = false
        val hits = mutableListOf<AlbumHit>()
        for (query in queries) {
            hits += searchAlbums(query)?.also { reached = true }.orEmpty()
        }
        val unique = hits.distinctBy { it.browseId }
        // Albums that clearly match first; then the top results anyway, since a single's title or artist may be
        // written in another script. Every track still has to pass the matcher, so a wrong album adds nothing.
        val matching =
            unique.filter {
                TrackMatcher.sameAlbumName(song.album, it.title) || TrackMatcher.artistMatches(song.artists, it.artist)
            }
        val likely = (matching + unique.take(TOP_ALBUMS)).distinct().take(MAX_ALBUMS)
        return if (reached) likely.flatMap { albumTracks(it).orEmpty() } else null
    }

    private companion object {
        const val MAX_QUERY_ARTISTS = 3
        const val MAX_ALBUMS = 3
        const val TOP_ALBUMS = 2
    }
}
