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

    /** Close enough in length to trust a match whose title is written differently (星空花 vs Hoshizora). */
    private const val EXACT_LENGTH_MS = 3_000L

    /** Length tolerance when title and artist both agree (edits, different intros). */
    private const val LOOSE_LENGTH_MS = 8_000L
    private const val TITLE_OK = 0.6
    private const val TITLE_STRONG = 0.85
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

    /** The best matching result from [candidates] (in search order), or null when none is trustworthy. */
    fun pick(
        song: SpotifySong,
        candidates: List<Track>,
    ): Track? =
        candidates
            .mapIndexedNotNull { rank, candidate -> score(song, candidate, rank)?.let { candidate to it } }
            .maxByOrNull { it.second }
            ?.first

    /** A score (higher is better) when [candidate] passes the rules, else null. */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    internal fun score(
        song: SpotifySong,
        candidate: Track,
        rank: Int,
    ): Double? {
        val songTitle = normalize(song.title)
        val candidateTitle = normalize(candidate.title)
        if (RECORDING_WORDS.any { hasWord(candidateTitle, it) != hasWord(songTitle, it) }) return null
        if (EXTRA_VERSION_WORDS.any { hasWord(candidateTitle, it) && !hasWord(songTitle, it) }) return null

        val diff =
            if (song.durationMs > 0 && candidate.durationMs > 0) abs(song.durationMs - candidate.durationMs) else null
        if (diff != null && diff > MAX_DURATION_DIFF_MS) return null

        val titleSim = titleSimilarity(song.title, candidate.title)
        val artistOk = artistMatches(song.artists, candidate.artist)
        if (!sameLanguage(song, candidate)) return null
        // Film songs are credited differently on each service (composer vs singer), but share the film.
        val albumOk = albumMatches(song, candidate)
        // Singles often have the song's other-script name as the album (星空花 · album "Hoshizora").
        val albumSim = if (song.album.isNotBlank()) titleSimilarity(song.album, candidate.title) else 0.0
        val bestTitle = maxOf(titleSim, albumSim)

        val passes =
            when {
                // Same artist and title: lengths may differ a little (edits, intros).
                artistOk && bestTitle >= TITLE_STRONG && (diff == null || diff <= LOOSE_LENGTH_MS) -> true
                // Same artist, title only roughly alike (transliterations like a title / a title):
                // the length must agree too, or "Raajana" takes "Rajan", another song by the same singers.
                artistOk && bestTitle >= TITLE_OK && diff != null && diff <= EXACT_LENGTH_MS -> true
                // Title in another script (星空花 vs Hoshizora): same artist, same length, near the top of the results.
                // Only when the scripts really differ; two unrelated English-letter titles are two songs.
                artistOk && diff != null && diff <= EXACT_LENGTH_MS && rank < CROSS_SCRIPT_MAX_RANK &&
                    hasNonLatinLetters(song.title) != hasNonLatinLetters(candidate.title) -> true
                // Artist name in another script (山田太郎 vs Taro Yamada): same title, same length. Only when a
                // script differs; two different Latin names are two different artists ("Lights" by Arden vs Corvo).
                titleSim >= TITLE_STRONG && diff != null && diff <= EXACT_LENGTH_MS &&
                    (song.artists.any(::hasNonLatinLetters) || hasNonLatinLetters(candidate.artist)) -> true
                // Different credits, same song: same title, same length and the same album (film soundtracks).
                titleSim >= TITLE_STRONG && diff != null && diff <= EXACT_LENGTH_MS && albumOk -> true
                // Same singer, same film, same length, title spelt another way (a title / a title).
                artistOk && albumOk && titleSim >= TITLE_LOOSE && diff != null && diff <= EXACT_LENGTH_MS -> true
                // Title, singer and film all agree: a longer or shorter edit of the same song is still that song.
                artistOk && albumOk && bestTitle >= TITLE_STRONG -> true
                else -> false
            }
        if (!passes) return null
        val lengthScore = diff?.let { 1.0 - it.toDouble() / MAX_DURATION_DIFF_MS } ?: 0.0
        val fullTitle = similarity(songTitle, candidateTitle)
        return maxOf(titleSim, albumSim) * TITLE_WEIGHT + (if (artistOk) ARTIST_WEIGHT else 0.0) +
            (if (albumOk) ALBUM_WEIGHT else 0.0) + lengthScore * LENGTH_WEIGHT + fullTitle * FULL_TITLE_WEIGHT -
            rank * RANK_PENALTY
    }

    /** Best similarity between the two titles, also trying each without "(feat. ...)" / " - Remastered" parts. */
    internal fun titleSimilarity(
        a: String,
        b: String,
    ): Double {
        val left = listOf(normalize(a), normalize(stripExtras(a))).filter { it.isNotEmpty() }.distinct()
        val right = listOf(normalize(b), normalize(stripExtras(b))).filter { it.isNotEmpty() }.distinct()
        return left.maxOfOrNull { l -> right.maxOf { r -> similarity(l, r) } } ?: 0.0
    }

    /**
     * True when any Spotify artist is one of the YouTube credits. YouTube joins several artists in one line
     * ("Singer, Chorus", "A & B"), so each name is compared on its own, allowing small spelling
     * differences (a name / a name).
     */
    internal fun artistMatches(
        spotifyArtists: List<String>,
        youtubeArtist: String,
    ): Boolean {
        val credits = youtubeArtist.split(ARTIST_SEPARATORS).map { compact(normalize(it)) }.filter { it.isNotEmpty() }
        val whole = compact(normalize(youtubeArtist))
        if (whole.isEmpty()) return false
        return spotifyArtists.any { artist ->
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
     * Indian film songs come in dubbed versions with the same title and length ("Raajana" in Kannada,
     * "Raajan" in Hindi). When either side names a language (album "Film Four [KANNADA]"), the other
     * must not name a different one; and when only Spotify names it, the main singer must match, since the
     * singer is what changes between dubs.
     */
    private fun sameLanguage(
        song: SpotifySong,
        candidate: Track,
    ): Boolean {
        val wanted = languages("${song.title} ${song.album}")
        val offered = languages("${candidate.title} ${candidate.album}")
        return when {
            wanted.isEmpty() && offered.isEmpty() -> true
            wanted.isNotEmpty() && offered.isNotEmpty() -> wanted.any { it in offered }
            wanted.isNotEmpty() -> artistMatches(song.artists.take(1), candidate.artist)
            // Only YouTube names a language: accept just when the artists agree.
            else -> artistMatches(song.artists, candidate.artist)
        }
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
        return wanted.any { a -> offered.any { b -> similarity(a, b) >= TITLE_STRONG } }
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
        // One title inside the other ("Nadi Tara" in "Nadi Tara (From Film Two)").
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
