package com.clibeats.data.importer

import com.clibeats.domain.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Decides which YouTube Music search result (if any) is the same recording as a Spotify song.
 *
 * Spotify and YouTube share no song ids, so this compares what both sides show: title, artist and
 * length. It prefers missing a song over adding the wrong one: when nothing passes, the song is
 * reported as not matched so the user can pick it by hand.
 */
@Suppress("TooManyFunctions") // small private text helpers for the matching rules
object TrackMatcher {
    /** Results further off than this are a different recording (live, extended, a loop video...). */
    const val MAX_DURATION_DIFF_MS = 15_000L

    /** Close enough in length to trust a match whose title is written differently (native script vs romanized). */
    private const val EXACT_LENGTH_MS = 3_000L

    /** Length tolerance when title and artist both agree (edits, different intros). */
    private const val LOOSE_LENGTH_MS = 8_000L
    private const val TITLE_OK = 0.6
    private const val TITLE_STRONG = 0.85
    private const val EXACT_TITLE = 0.999
    private const val ALBUM_SIMILAR = 0.85
    private const val ARTIST_SIMILAR = 0.8

    /** Only the top results are trusted on artist + length alone; lower ones are more often other songs. */
    private const val CROSS_SCRIPT_MAX_RANK = 3

    // Ranking among results that pass: title counts most, then artist and length; earlier results win ties.
    private const val TITLE_WEIGHT = 3.0
    private const val ARTIST_WEIGHT = 2.0
    private const val LENGTH_WEIGHT = 2.0
    private const val RANK_PENALTY = 0.05

    /** Small push towards the result whose full title (brackets included) matches. */
    private const val FULL_TITLE_WEIGHT = 0.5
    private const val ALBUM_WEIGHT = 1.0

    /**
     * A result whose album or title names the asked-for language beats one that doesn't: YouTube Music can list
     * one language's audio under an album that doesn't say which language it is.
     */
    private const val LANGUAGE_WEIGHT = 3.0

    /** Only with singer, film and length all agreeing; titles transliterated differently share about half. */
    private const val TITLE_LOOSE = 0.45
    private val FROM_FILM = Regex("""\bfrom\s*["“”']([^"“”']+)["“”']""", RegexOption.IGNORE_CASE)
    private val LANGUAGES =
        listOf("kannada", "telugu", "tamil", "hindi", "malayalam", "marathi", "bengali", "punjabi", "gujarati")
    private const val MIN_CONTAINED_NAME = 4
    private val ARTIST_SEPARATORS = Regex(""",|&|、| x | and |\bfeat\.?|\bft\.?| with """, RegexOption.IGNORE_CASE)

    /**
     * A different recording from the song itself: both sides must agree. "Song" never matches "Song (Remix)",
     * and "Song (Remix)" never matches plain "Song".
     */
    private val RECORDING_WORDS =
        listOf(
            "karaoke", "instrumental", "8d", "slowed", "sped up", "speed up", "nightcore", "reverb",
            "remix", "live", "acoustic", "lofi", "lo fi", "off vocal", "tv size", "tv ver",
        )

    /** Only rejected when the result has it and the Spotify title doesn't (Spotify often omits "Version"). */
    private val EXTRA_VERSION_WORDS = listOf("cover", "piano version", "first take", "version", "ver")

    /**
     * The best matching result from [candidates] (in search order), or null when none is trustworthy.
     * [allowClosest]: a song YouTube Music only has in another version ("Song (Remix)" when only "Song"
     * exists) may take that version. Off unless the user asks for it on the import screen.
     */
    fun pick(
        song: SpotifySong,
        candidates: List<Track>,
        allowClosest: Boolean = false,
    ): Track? =
        candidates
            .mapIndexedNotNull { rank, candidate ->
                score(song, candidate, rank, allowClosest)?.let { candidate to it }
            }.maxByOrNull { it.second }
            ?.first

    /** A score (higher is better) when [candidate] passes the rules, else null. */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    internal fun score(
        song: SpotifySong,
        candidate: Track,
        rank: Int,
        allowClosest: Boolean = false,
    ): Double? {
        val songTitle = normalize(song.title)
        val candidateTitle = normalize(candidate.title)
        // A different recording never stands in for the original (no karaoke for the song)...
        if (RECORDING_WORDS.any { hasWord(candidateTitle, it) && !hasWord(songTitle, it) }) return null
        // ...and the asked-for version (remix, live) needs that version: checked below, once the album is known.
        val versionMissing = RECORDING_WORDS.any { hasWord(songTitle, it) && !hasWord(candidateTitle, it) }
        if (EXTRA_VERSION_WORDS.any { hasWord(candidateTitle, it) && !hasWord(songTitle, it) }) return null

        val diff =
            if (song.durationMs > 0 && candidate.durationMs > 0) abs(song.durationMs - candidate.durationMs) else null
        if (diff != null && diff > MAX_DURATION_DIFF_MS) return null

        val titleSim = titleSimilarity(song.title, candidate.title)
        val artistOk = artistMatches(song.artists, candidate.artist)
        val wanted = languages("${song.title} ${song.album}")
        // The channel counts for videos: a label channel named after a language uploads that version.
        val offered = languages("${candidate.title} ${candidate.album} ${candidate.artist}")
        if (!sameLanguage(song, candidate, wanted, offered)) return null
        val languageConfirmed = wanted.isNotEmpty() && wanted.any { it in offered }
        // Film songs are credited differently on each service (composer vs singer), but share the film.
        val albumOk = albumMatches(song, candidate)
        // Services label the same recording differently: a title marked "(Remix)" on Spotify can be the plain
        // title on YouTube Music, on the same album at the same length. Same album and
        // length = same recording; otherwise a remix never takes the original unless closest was allowed.
        val sameRecording = albumOk && diff != null && diff <= EXACT_LENGTH_MS
        if (versionMissing && !allowClosest && !sameRecording) return null
        // Singles often have the song's other-script name as the album (native-script title, romanized album).
        val albumSim = if (song.album.isNotBlank()) titleSimilarity(song.album, candidate.title) else 0.0
        val bestTitle = maxOf(titleSim, albumSim)

        val passes =
            when {
                // Same artist and title: lengths may differ a little (edits, intros).
                artistOk && bestTitle >= TITLE_STRONG && diff != null && diff <= LOOSE_LENGTH_MS -> true
                // No length to compare (e.g. TuneMyMusic exports): a near-title by a shared singer can be another
                // language's dub, so demand the exact title and, when both sides name one, the same album.
                artistOk && diff == null && titleSim >= EXACT_TITLE &&
                    (albumOk || song.album.isBlank() || candidate.album.isBlank()) -> true
                // Same artist, title only roughly alike (a transliteration spelt another way):
                // the length must agree too, or a similar title by the same singers (a different song) slips in.
                artistOk && bestTitle >= TITLE_OK && diff != null && diff <= EXACT_LENGTH_MS -> true
                // Title in another script (native vs romanized): same artist, same length, near the top of the results.
                // Only when the scripts really differ; two unrelated English-letter titles are two songs.
                artistOk && diff != null && diff <= EXACT_LENGTH_MS && rank < CROSS_SCRIPT_MAX_RANK &&
                    hasNonLatinLetters(song.title) != hasNonLatinLetters(candidate.title) -> true
                // Artist name in another script (native vs romanized): same title, same length. Only when a
                // script differs; two different Latin names are two different artists, even with the same title.
                titleSim >= TITLE_STRONG && diff != null && diff <= EXACT_LENGTH_MS &&
                    (song.artists.any(::hasNonLatinLetters) || hasNonLatinLetters(candidate.artist)) -> true
                // Different credits, same song: same title, same length and the same album (film soundtracks).
                titleSim >= TITLE_STRONG && diff != null && diff <= EXACT_LENGTH_MS && albumOk -> true
                // Same singer, same film, same length, title transliterated another way.
                artistOk && albumOk && titleSim >= TITLE_LOOSE && diff != null && diff <= EXACT_LENGTH_MS -> true
                // Title, singer and film all agree: a longer or shorter edit of the same song is still that song.
                artistOk && albumOk && bestTitle >= TITLE_STRONG -> true
                // A song with no audio release, uploaded as a video by the label in the asked-for language
                // (a lyrical video on the label's language channel): same title, about the same length.
                languageConfirmed && titleSim >= TITLE_STRONG && diff != null && diff <= LOOSE_LENGTH_MS -> true
                // Exports that only list a video's title (no artist, no length): only the exact same title counts.
                song.artists.isEmpty() && compact(songTitle) == compact(candidateTitle) -> true
                else -> false
            }
        if (!passes) return null
        val lengthScore = diff?.let { 1.0 - it.toDouble() / MAX_DURATION_DIFF_MS } ?: 0.0
        val fullTitle = similarity(songTitle, candidateTitle)
        return maxOf(titleSim, albumSim) * TITLE_WEIGHT + (if (artistOk) ARTIST_WEIGHT else 0.0) +
            (if (albumOk) ALBUM_WEIGHT else 0.0) + lengthScore * LENGTH_WEIGHT + fullTitle * FULL_TITLE_WEIGHT +
            (if (languageConfirmed) LANGUAGE_WEIGHT else 0.0) +
            // The language in the channel or album name (a label's language channel) beats it only in a title,
            // which fan uploads add as a hashtag.
            (if (languageConfirmed && wanted.any { it in languages("${candidate.artist} ${candidate.album}") }) 1.0 else 0.0) -
            rank * RANK_PENALTY
    }

    /** Best similarity between the two titles, also trying each without "(feat. ...)" / " - Remastered" parts. */
    internal fun titleSimilarity(
        a: String,
        b: String,
    ): Double {
        val left = listOf(normalize(a), normalize(stripExtras(a))).filter { it.isNotEmpty() }.distinct()
        val right = listOf(normalize(b), normalize(stripExtras(b))).filter { it.isNotEmpty() }.distinct()
        // Either side can be empty after removing symbols (a title made only of emoji): no similarity, no crash.
        return left.maxOfOrNull { l -> right.maxOfOrNull { r -> similarity(l, r) } ?: 0.0 } ?: 0.0
    }

    /**
     * True when any Spotify artist is one of the YouTube credits. YouTube joins several artists in one line
     * ("Singer, Chorus", "A & B"), so each name is compared on its own, allowing small spelling
     * differences (one letter more or less in a transliterated name).
     */
    internal fun artistMatches(
        spotifyArtists: List<String>,
        youtubeArtist: String,
    ): Boolean {
        val credits = youtubeArtist.split(ARTIST_SEPARATORS).map { compact(normalize(it)) }.filter { it.isNotEmpty() }
        val whole = compact(normalize(youtubeArtist))
        if (whole.isEmpty()) return false
        // Spotify credits duos as one artist too ("A with B" on Spotify, "A & B" on YouTube).
        val names = spotifyArtists.flatMap { listOf(it) + it.split(ARTIST_SEPARATORS) }
        return names.any { artist ->
            val a = compact(normalize(artist))
            a.isNotEmpty() && (sameName(a, whole) || credits.any { sameName(a, it) })
        }
    }

    private fun sameName(
        a: String,
        b: String,
    ): Boolean {
        if (a == b) return true
        // Containment only for names long enough not to hit by accident ("K" is inside many names).
        val containable = minOf(a.length, b.length) >= MIN_CONTAINED_NAME
        return (containable && (a.contains(b) || b.contains(a))) || dice(a, b) >= ARTIST_SIMILAR
    }

    /**
     * Indian film songs come in dubbed versions with near-identical titles and the same length, one per
     * language. When either side names a language (an album ending in "[KANNADA]"), the other
     * must not name a different one; and when only Spotify names it, the main singer must match, since the
     * singer is what changes between dubs.
     */
    private fun sameLanguage(
        song: SpotifySong,
        candidate: Track,
        wanted: Set<String>,
        offered: Set<String>,
    ): Boolean =
        when {
            wanted.isEmpty() && offered.isEmpty() -> true
            wanted.isNotEmpty() && offered.isNotEmpty() -> wanted.any { it in offered }
            wanted.isNotEmpty() -> artistMatches(song.artists.take(1), candidate.artist)
            // Only YouTube names a language: accept just when the artists agree.
            else -> artistMatches(song.artists, candidate.artist)
        }

    /**
     * True when [track] is surely the asked-for version: no language was named, or the result names the same
     * language. Otherwise it may be another language's dub, so the search keeps looking.
     */
    fun isCertainVersion(
        song: SpotifySong,
        track: Track,
    ): Boolean {
        val wanted = languages("${song.title} ${song.album}")
        if (wanted.isEmpty()) return true
        val offered = languages("${track.title} ${track.album} ${track.artist}")
        // An exact title isn't enough: YouTube Music can list one language's audio under the other's title.
        return wanted.any { it in offered }
    }

    /** The language named in the Spotify title or album (e.g. "[KANNADA]"), to search for that version. */
    fun languageOf(song: SpotifySong): String? = languages("${song.title} ${song.album}").firstOrNull()

    /** Same album once "(Original Motion Picture Soundtrack)"-style suffixes are dropped. */
    fun sameAlbumName(
        a: String,
        b: String,
    ): Boolean {
        val x = normalize(baseAlbum(a))
        val y = normalize(baseAlbum(b))
        return x.isNotEmpty() && y.isNotEmpty() && sameAlbumText(x, y)
    }

    /**
     * Album or film names: the same name, allowing small spelling differences. Not "one inside the other":
     * a short name sits inside a longer one, and film sequels and language editions differ by exactly such parts.
     */
    private fun sameAlbumText(
        a: String,
        b: String,
    ): Boolean {
        val x = compact(a)
        val y = compact(b)
        return x == y || dice(x, y) >= ALBUM_SIMILAR
    }

    private fun languages(text: String): Set<String> {
        val words = " ${normalize(text)} "
        return LANGUAGES.filterTo(mutableSetOf()) { words.contains(" $it ") }
    }

    /**
     * Same album or film on both sides, once "(Original Motion Picture Soundtrack)"-style suffixes are dropped.
     * The film also comes from a title's `(From "Film")`, since Spotify often files film songs under a
     * singer's compilation.
     */
    private fun albumMatches(
        song: SpotifySong,
        candidate: Track,
    ): Boolean {
        fun names(
            album: String,
            title: String,
        ) = listOfNotNull(baseAlbum(album), filmOf(title)).map(::normalize).filter { it.isNotEmpty() }
        val wanted = names(song.album, song.title)
        val offered = names(candidate.album, candidate.title)
        return wanted.any { a -> offered.any { b -> sameAlbumText(a, b) } }
    }

    /** "Song (From "Film")" → "Film". */
    private fun filmOf(title: String): String? = FROM_FILM.find(title)?.groupValues?.get(1)?.trim()

    /** "Film (Original Motion Picture Soundtrack)" → "Film". Used for searching too. */
    fun baseAlbum(album: String): String = stripExtras(album)

    /** Lowercase, width/accent-folded, punctuation turned into single spaces. Keeps every script's letters. */
    internal fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            // Japanese titles write "TVサイズ" where YouTube writes "TV Size".
            .replace("サイズ", " size")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /** Drops bracketed parts and anything after " - " (featurings, "Remastered 2011", "From the film X"). */
    private fun stripExtras(s: String): String =
        s.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            .substringBefore(" - ")
            .trim()

    @Suppress("ReturnCount")
    private fun similarity(
        a: String,
        b: String,
    ): Double {
        val x = compact(a)
        val y = compact(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val shorter = minOf(x.length, y.length)
        // One title inside the other ("Song" in "Song (From Film)").
        if (shorter >= 2 && (x.contains(y) || y.contains(x))) return 0.9
        return dice(x, y)
    }

    /** Sørensen–Dice over character pairs: works the same for Latin, Kannada and Japanese text. */
    private fun dice(
        a: String,
        b: String,
    ): Double {
        if (a.length < 2 || b.length < 2) return if (a == b) 1.0 else 0.0
        val pairs = a.windowed(2).groupingBy { it }.eachCount().toMutableMap()
        var shared = 0
        for (pair in b.windowed(2)) {
            val left = pairs[pair] ?: 0
            if (left > 0) {
                shared++
                pairs[pair] = left - 1
            }
        }
        return 2.0 * shared / (a.length - 1 + b.length - 1)
    }

    private fun compact(s: String) = s.replace(" ", "")

    /** True for names written in e.g. Japanese or Kannada script, where YouTube may show a romanized name. */
    private fun hasNonLatinLetters(s: String) =
        s.any { it.isLetter() && Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN }

    private fun hasWord(
        normalized: String,
        word: String,
    ) = " $normalized ".contains(" $word ")
}
