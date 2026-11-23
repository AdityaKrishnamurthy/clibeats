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

    /** Versions that are never the original unless the Spotify title says so too. */
    private val VERSION_WORDS =
        listOf(
            "karaoke", "instrumental", "cover", "8d", "slowed", "sped up", "speed up", "nightcore",
            "reverb", "remix", "live", "acoustic", "lofi", "lo fi", "off vocal", "piano version",
        )

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
        if (VERSION_WORDS.any { hasWord(candidateTitle, it) && !hasWord(songTitle, it) }) return null

        val diff =
            if (song.durationMs > 0 && candidate.durationMs > 0) abs(song.durationMs - candidate.durationMs) else null
        if (diff != null && diff > MAX_DURATION_DIFF_MS) return null

        val titleSim = titleSimilarity(song.title, candidate.title)
        val artistOk = artistMatches(song.artists, candidate.artist)
        // Singles often have the song's other-script name as the album (星空花 · album "Hoshizora").
        val albumSim = if (song.album.isNotBlank()) titleSimilarity(song.album, candidate.title) else 0.0

        val passes =
            when {
                artistOk && maxOf(titleSim, albumSim) >= TITLE_OK && (diff == null || diff <= LOOSE_LENGTH_MS) -> true
                // Title in another script or language: same artist, same length, near the top of the results.
                artistOk && diff != null && diff <= EXACT_LENGTH_MS && rank < CROSS_SCRIPT_MAX_RANK -> true
                // Artist name written differently (山田太郎 vs Taro Yamada): same title, same length.
                titleSim >= TITLE_STRONG && diff != null && diff <= EXACT_LENGTH_MS -> true
                else -> false
            }
        if (!passes) return null
        val lengthScore = diff?.let { 1.0 - it.toDouble() / MAX_DURATION_DIFF_MS } ?: 0.0
        return maxOf(titleSim, albumSim) * TITLE_WEIGHT + (if (artistOk) ARTIST_WEIGHT else 0.0) +
            lengthScore * LENGTH_WEIGHT - rank * RANK_PENALTY
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

    internal fun artistMatches(
        spotifyArtists: List<String>,
        youtubeArtist: String,
    ): Boolean {
        val yt = compact(normalize(youtubeArtist))
        if (yt.isEmpty()) return false
        return spotifyArtists.any { artist ->
            val a = compact(normalize(artist))
            a.isNotEmpty() && (yt.contains(a) || a.contains(yt) || dice(a, yt) >= ARTIST_SIMILAR)
        }
    }

    /** Lowercase, width/accent-folded, punctuation turned into single spaces. Keeps every script's letters. */
    internal fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
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

    private fun hasWord(
        normalized: String,
        word: String,
    ) = " $normalized ".contains(" $word ")
}
