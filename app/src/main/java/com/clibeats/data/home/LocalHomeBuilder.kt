@file:Suppress("MagicNumber", "TooManyFunctions")

package com.clibeats.data.home

import com.clibeats.domain.model.CollectionKind
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeShelf
import com.clibeats.domain.model.Track
import java.net.URLDecoder
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** One play from the history: which song, and when. */
data class Play(
    val songId: String,
    val playedAt: Long,
)

/**
 * Home for local music: rows built from the library and the play history, plus the song lists behind
 * every card ([collections], keyed by the card's id) and what the Shuffle all / Surprise album buttons use.
 */
data class LocalHome(
    val shelves: List<HomeShelf>,
    val collections: Map<String, List<Track>>,
    val allSongs: List<Track>,
    /** Albums with at least [MIN_ALBUM_SONGS] songs, each in track order. */
    val albums: List<List<Track>>,
)

/**
 * Builds local Home from [songs] (the local library), [recentlyAdded] (newest files first) and [plays]
 * (the play history, any order; plays of songs that aren't local are ignored). [now] and [zone] decide the
 * time-of-day mix; [seed] fixes every shuffle, so the page stays the same for a day (or until Refresh).
 *
 * Rows, each left out when there's nothing for it:
 * - Mixes for you: what you usually play at this time of day, and per top artist a mix of that artist and
 *   the artists you play in the same sittings (an offline radio).
 * - Jump back in: albums played lately.
 * - Recently added: the newest files.
 * - Your top artists: by plays (by number of songs before there's any history).
 * - Forgotten gems: songs played a lot, but not for a month.
 * - Never played (or Discover your library, when nothing has been played yet).
 * - Albums to revisit: albums not played for two months.
 */
@Suppress("LongParameterList")
fun buildLocalHome(
    songs: List<Track>,
    recentlyAdded: List<Track>,
    plays: List<Play>,
    now: Long,
    zone: ZoneId,
    seed: Long,
): LocalHome {
    val library = LocalLibrary(songs, plays, Random(seed))
    val collections = LinkedHashMap<String, List<Track>>()
    val shelves = mutableListOf<HomeShelf>()

    fun cards(
        title: String,
        strapline: String,
        entries: List<Triple<String, List<Track>, HomeItem.Collection>>,
    ) {
        if (entries.isEmpty()) return
        entries.forEach { (id, tracks, _) -> collections[id] = tracks }
        shelves += HomeShelf(title, strapline, entries.map { it.third })
    }

    fun songRow(
        title: String,
        strapline: String,
        tracks: List<Track>,
        minimum: Int = MIN_SONG_ROW,
    ) {
        if (tracks.size >= minimum) shelves += HomeShelf(title, strapline, tracks.map(HomeItem::Song))
    }

    val mixes = listOfNotNull(library.timeOfDayMix(now, zone)) + library.artistMixes()
    cards("Mixes for you", "MADE FROM WHAT YOU PLAY", mixes)
    cards("Jump back in", "", library.recentAlbums())
    songRow("Recently added", "NEW IN YOUR LIBRARY", recentlyAdded.filter { it.id in library.byId }.take(ROW_SONGS))
    cards("Your top artists", "", library.topArtists())
    songRow("Forgotten gems", "YOU USED TO LOVE THESE", library.forgottenGems(now))
    if (library.hasHistory) {
        songRow("Never played", "GIVE THESE A CHANCE", library.neverPlayed())
    } else {
        songRow("Discover your library", "", library.neverPlayed())
    }
    cards(
        if (library.hasHistory) "Albums to revisit" else "Your albums",
        "",
        library.albumsToRevisit(now),
    )
    return LocalHome(shelves, collections, songs, library.albums.map { it.tracks })
}

private class Album(
    val key: String,
    val title: String,
    val artist: String,
    val tracks: List<Track>,
)

/** The library and history, indexed for the rows. */
private class LocalLibrary(
    songs: List<Track>,
    allPlays: List<Play>,
    private val random: Random,
) {
    val byId: Map<String, Track> = songs.associateBy { it.id }

    /** Local plays, oldest first. */
    private val plays = allPlays.filter { it.songId in byId }.sortedBy { it.playedAt }
    val hasHistory = plays.isNotEmpty()
    private val playCount: Map<String, Int> = plays.groupingBy { it.songId }.eachCount()
    private val lastPlayed: Map<String, Long> = plays.associate { it.songId to it.playedAt }

    private val songsByArtist: Map<String, List<Track>> = songs.groupBy { artistKey(it) }
    private val artistNames: Map<String, String> =
        songsByArtist.mapValues { (_, tracks) -> mostCommonArtist(tracks) }

    val albums: List<Album> =
        songs
            .filter { it.album.isNotBlank() }
            .groupBy { "${it.album.lowercase()}|${folderOf(it)}" }
            .map { (key, tracks) ->
                val ordered = tracks.sortedWith(trackOrder)
                Album(key, ordered.first().album, mostCommonArtist(ordered), ordered)
            }.filter { it.tracks.size >= MIN_ALBUM_SONGS }
    private val albumOfSong: Map<String, Album> =
        albums.flatMap { album -> album.tracks.map { it.id to album } }.toMap()

    /** Plays per artist, and how often two artists are played in the same sitting. */
    private val artistPlays: Map<String, Int> = plays.groupingBy { artistKey(byId.getValue(it.songId)) }.eachCount()
    private val playedTogether: Map<String, Map<String, Int>> = coPlayedArtists()

    // ── Mixes ──

    fun timeOfDayMix(
        now: Long,
        zone: ZoneId,
    ): Triple<String, List<Track>, HomeItem.Collection>? {
        val minute = minuteOfDay(now, zone)
        val counts =
            plays
                .filter { now - it.playedAt <= TIME_MIX_HISTORY_MS }
                .filter { circularDistance(minuteOfDay(it.playedAt, zone), minute) <= TIME_WINDOW_MIN }
                .groupingBy { it.songId }
                .eachCount()
        val picked =
            counts.entries
                .sortedByDescending { it.value }
                .take(MIX_SONGS)
                .mapNotNull { byId[it.key] }
        if (picked.size < MIN_MIX_SONGS) return null
        val tracks = picked.shuffled(random)
        val name = "${partOfDay(minute)} mix"
        val id = "local:mix:time"
        return Triple(id, tracks, card(id, name, "What you play around this time · ${tracks.size} songs", tracks))
    }

    fun artistMixes(): List<Triple<String, List<Track>, HomeItem.Collection>> =
        rankedArtists().take(ARTIST_MIXES).mapNotNull { artist ->
            val own = songsByArtist[artist].orEmpty()
            val (played, unplayed) = own.partition { (playCount[it.id] ?: 0) > 0 }
            val core =
                (played.sortedByDescending { playCount[it.id] ?: 0 } + unplayed.shuffled(random))
                    .take(MIX_OWN_SONGS)
            val companions =
                playedTogether[artist].orEmpty().entries
                    .sortedByDescending { it.value }
                    .take(MIX_COMPANIONS)
                    .map { it.key }
            val extra = companions.flatMap { songsByArtist[it].orEmpty().shuffled(random).take(MIX_COMPANION_SONGS) }
            val tracks = (core + extra).distinctBy { it.id }.shuffled(random)
            if (tracks.size < MIN_ARTIST_MIX_SONGS) return@mapNotNull null
            val names = (listOf(artist) + companions).mapNotNull { artistNames[it] }.filter { it.isNotBlank() }
            val id = "local:mix:artist:$artist"
            Triple(id, tracks, card(id, "Mix: ${artistNames[artist]}", "Mix · ${names.joinToString(", ")}", tracks))
        }

    // ── Albums and artists ──

    fun recentAlbums(): List<Triple<String, List<Track>, HomeItem.Collection>> =
        plays
            .asReversed()
            .mapNotNull { albumOfSong[it.songId] }
            .distinctBy { it.key }
            .take(CARD_ROW_SIZE)
            .map(::albumCard)

    fun albumsToRevisit(now: Long): List<Triple<String, List<Track>, HomeItem.Collection>> =
        albums
            .filter { album ->
                val last = album.tracks.maxOfOrNull { lastPlayed[it.id] ?: 0L } ?: 0L
                now - last > REVISIT_AFTER_MS
            }.shuffled(random)
            .take(CARD_ROW_SIZE)
            .map(::albumCard)

    fun topArtists(): List<Triple<String, List<Track>, HomeItem.Collection>> =
        rankedArtists().take(CARD_ROW_SIZE).map { artist ->
            val tracks = songsByArtist[artist].orEmpty().sortedWith(albumThenTrack)
            val plays = artistPlays[artist] ?: 0
            val id = "local:artist:$artist"
            val cover =
                tracks.sortedByDescending { playCount[it.id] ?: 0 }.firstOrNull { it.artworkUrl != null }?.artworkUrl
            val subtitle = if (plays > 0) "${tracks.size} songs · $plays plays" else "${tracks.size} songs"
            Triple(
                id,
                tracks,
                HomeItem.Collection(id, artistNames[artist].orEmpty(), subtitle, cover, CollectionKind.ARTIST),
            )
        }

    /** Artists by plays, then by number of songs; "unknown" artists last. */
    private fun rankedArtists(): List<String> =
        songsByArtist.keys
            .filter { it != UNKNOWN_KEY }
            .sortedWith(
                compareByDescending<String> { artistPlays[it] ?: 0 }
                    .thenByDescending { songsByArtist[it]?.size ?: 0 },
            ).filter { artist ->
                // With a history: artists you play, plus those with a few songs (not every one-off guest).
                !hasHistory || (artistPlays[artist] ?: 0) > 0 || (songsByArtist[artist]?.size ?: 0) >= MIN_ALBUM_SONGS
            }

    // ── Songs ──

    fun forgottenGems(now: Long): List<Track> =
        playCount.entries
            .filter { (id, count) -> count >= GEM_MIN_PLAYS && now - (lastPlayed[id] ?: now) > GEM_AFTER_MS }
            .sortedByDescending { it.value }
            .take(ROW_SONGS)
            .mapNotNull { byId[it.key] }

    fun neverPlayed(): List<Track> = byId.values.filter { it.id !in playCount }.shuffled(random).take(ROW_SONGS)

    // ── Helpers ──

    private fun albumCard(album: Album): Triple<String, List<Track>, HomeItem.Collection> {
        val id = "local:album:${album.key}"
        val subtitle = listOf(album.artist, "${album.tracks.size} songs").filter { it.isNotBlank() }.joinToString(" · ")
        val cover = album.tracks.firstOrNull { it.artworkUrl != null }?.artworkUrl
        return Triple(id, album.tracks, HomeItem.Collection(id, album.title, subtitle, cover, CollectionKind.ALBUM))
    }

    private fun card(
        id: String,
        title: String,
        subtitle: String,
        tracks: List<Track>,
    ): HomeItem.Collection {
        val cover = tracks.firstOrNull { it.artworkUrl != null }?.artworkUrl
        return HomeItem.Collection(id, title, subtitle, cover, CollectionKind.PLAYLIST)
    }

    /** Sittings are plays with no gap longer than [SESSION_GAP_MS]; artists in one sitting count as played together. */
    private fun coPlayedArtists(): Map<String, Map<String, Int>> {
        val together = HashMap<String, HashMap<String, Int>>()
        var sitting = HashSet<String>()
        var previous = Long.MIN_VALUE

        fun close() {
            sitting.forEach { a ->
                sitting.forEach { b -> if (a != b) together.getOrPut(a) { HashMap() }.merge(b, 1, Int::plus) }
            }
            sitting = HashSet()
        }
        plays.forEach { play ->
            if (previous != Long.MIN_VALUE && play.playedAt - previous > SESSION_GAP_MS) close()
            val artist = artistKey(byId.getValue(play.songId))
            if (artist != UNKNOWN_KEY) sitting += artist
            previous = play.playedAt
        }
        close()
        return together
    }
}

/** Album songs in file order (files usually start with the track number), which matches the disc order. */
private val trackOrder: Comparator<Track> =
    compareBy<Track> { decodedPath(it).lowercase() }.thenBy { it.title.lowercase() }

private val albumThenTrack: Comparator<Track> = compareBy<Track> { it.album.lowercase() }.then(trackOrder)

private fun mostCommonArtist(tracks: List<Track>): String =
    tracks.groupingBy { primaryArtist(it) }.eachCount().maxByOrNull { it.value }?.key.orEmpty()

private fun decodedPath(track: Track): String =
    runCatching { URLDecoder.decode(track.streamUrl.orEmpty(), "UTF-8") }.getOrDefault(track.streamUrl.orEmpty())

/** The folder a song's file is in, so two albums with the same name in different folders stay apart. */
private fun folderOf(track: Track): String = decodedPath(track).substringBeforeLast('/', "")

/** "A, B & C feat. D" counts as A's song. */
internal fun primaryArtist(track: Track): String = track.artist.split(ARTIST_SEPARATORS).first().trim()

private fun artistKey(track: Track): String =
    primaryArtist(track).lowercase().takeUnless { it.isBlank() || it in UNKNOWN_ARTISTS } ?: UNKNOWN_KEY

private val ARTIST_SEPARATORS = Regex("""\s*(?:,|;|&|/|\bfeat\.?|\bft\.?|\bfeaturing\b)\s*""", RegexOption.IGNORE_CASE)
private val UNKNOWN_ARTISTS = setOf("unknown", "unknown artist", "<unknown>", "various artists")
private const val UNKNOWN_KEY = "\u0000unknown"

private fun minuteOfDay(
    epochMs: Long,
    zone: ZoneId,
): Int = Instant.ofEpochMilli(epochMs).atZone(zone).let { it.hour * 60 + it.minute }

private fun circularDistance(
    a: Int,
    b: Int,
): Int {
    val d = kotlin.math.abs(a - b)
    return minOf(d, MINUTES_PER_DAY - d)
}

internal fun partOfDay(minuteOfDay: Int): String =
    when (minuteOfDay / 60) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Late night"
    }

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val MINUTES_PER_DAY = 24 * 60
private const val TIME_WINDOW_MIN = 90
private const val TIME_MIX_HISTORY_MS = 90 * DAY_MS
private const val SESSION_GAP_MS = 30L * 60 * 1000
private const val GEM_AFTER_MS = 30 * DAY_MS
private const val REVISIT_AFTER_MS = 60 * DAY_MS
private const val GEM_MIN_PLAYS = 3
private const val MIX_SONGS = 40
private const val MIN_MIX_SONGS = 8
private const val ARTIST_MIXES = 6
private const val MIX_OWN_SONGS = 15
private const val MIX_COMPANIONS = 3
private const val MIX_COMPANION_SONGS = 6
private const val MIN_ARTIST_MIX_SONGS = 6
private const val ROW_SONGS = 20
private const val MIN_SONG_ROW = 4
private const val CARD_ROW_SIZE = 10
const val MIN_ALBUM_SONGS = 3
