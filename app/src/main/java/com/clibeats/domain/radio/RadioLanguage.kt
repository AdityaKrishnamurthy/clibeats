package com.clibeats.domain.radio

import com.clibeats.domain.model.Track
import java.lang.Character.UnicodeScript

/**
 * Keeps autoplay's radio in the language it started in. YouTube Music's radio for a Kannada song often mixes
 * in Telugu or Tamil film songs (frequently the same film song in another language). Suggestions in the
 * radio's language play first, songs whose language can't be told next, and other languages last. Nothing
 * is dropped, so the radio never runs dry because of this.
 *
 * A song's language comes from its writing system (Kannada script, kana, Hangul, ...) or, for titles in Latin
 * letters, a language named in the title or artist ("(Telugu)", "Tamil Version").
 */
object RadioLanguage {
    /** The language of [track] (e.g. "kannada", "japanese"), or null when it can't be told. */
    fun languageOf(track: Track): String? = languageOf(track.title, track.artist, track.album)

    /**
     * The first text written in a non-Latin script decides (the title, then the artist: a Kannada title with
     * an artist name in English letters is Kannada); otherwise a language named in any of them.
     */
    fun languageOf(vararg texts: String?): String? =
        texts.firstNotNullOfOrNull { scriptOf(it) }?.let { SCRIPT_LANGUAGE[it] ?: it.lowercase() }
            ?: namedLanguage(*texts)

    /**
     * The language a radio should keep to: the seed song's own, or, when that can't be told (a Latin title),
     * the clear majority language of YouTube Music's suggestions for it (its first suggestions are the closest
     * matches).
     */
    @Suppress("ReturnCount")
    fun forRadio(
        seed: Track,
        suggestions: List<Track>,
    ): String? {
        languageOf(seed)?.let { return it }
        val labelled = suggestions.take(MAJORITY_SAMPLE).mapNotNull { languageOf(it) }
        if (labelled.size < MIN_LABELLED) return null
        val (top, count) = labelled.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return top.takeIf { count * 2 > labelled.size }
    }

    /**
     * [candidates] reordered for a radio in [language]: same language first, unknown next, other languages
     * last, each group in YouTube Music's order. With no language the order is unchanged.
     */
    fun preferSameLanguage(
        language: String?,
        candidates: List<Track>,
    ): List<Track> {
        if (language == null) return candidates
        return candidates.sortedBy { track ->
            when (languageOf(track)) {
                language -> 0
                null -> 1
                else -> 2
            }
        }
    }

    /**
     * The writing system of [texts] (e.g. "KANNADA", "JAPANESE"), or null when they are in Latin letters or
     * have no letters. Latin is not a language signal: English, romanised Kannada and Spanish all use it.
     */
    @Suppress("NestedBlockDepth", "ReturnCount")
    fun scriptOf(vararg texts: String?): String? {
        val counts = HashMap<UnicodeScript, Int>()
        var letters = 0
        texts.filterNotNull().forEach { text ->
            text.codePoints().forEach { cp ->
                if (Character.isLetter(cp)) {
                    letters++
                    val script = UnicodeScript.of(cp)
                    if (script !in NEUTRAL && script != UnicodeScript.LATIN) counts.merge(script, 1, Int::plus)
                }
            }
        }
        // A stray non-Latin letter in a Latin title doesn't make it that language.
        if (letters == 0 || counts.values.sum() * MIN_SHARE_DENOMINATOR < letters) return null
        // Japanese mixes kana and kanji; kanji alone could be Chinese.
        if ((counts[UnicodeScript.HIRAGANA] ?: 0) + (counts[UnicodeScript.KATAKANA] ?: 0) > 0) return "JAPANESE"
        return counts.maxByOrNull { it.value }?.key?.name
    }

    /** A language named as a word in [texts]: "Song (Telugu)", "Tamil Version", "Hindi Cover". */
    private fun namedLanguage(vararg texts: String?): String? {
        val words =
            texts.filterNotNull().joinToString(" ").lowercase().split(NON_LETTER).filter { it.isNotEmpty() }
        return words.firstOrNull { it in NAMED_LANGUAGES }
    }

    private val SCRIPT_LANGUAGE =
        mapOf(
            "KANNADA" to "kannada",
            "TELUGU" to "telugu",
            "TAMIL" to "tamil",
            "MALAYALAM" to "malayalam",
            "DEVANAGARI" to "hindi",
            "BENGALI" to "bengali",
            "GURMUKHI" to "punjabi",
            "GUJARATI" to "gujarati",
            "ORIYA" to "odia",
            "JAPANESE" to "japanese",
            "HANGUL" to "korean",
            "HAN" to "chinese",
            "THAI" to "thai",
            "ARABIC" to "arabic",
            "CYRILLIC" to "russian",
        )

    /** Language names that appear in song titles; "english" is left out (it marks covers, not the radio). */
    private val NAMED_LANGUAGES =
        setOf(
            "kannada", "telugu", "tamil", "malayalam", "hindi", "marathi", "bengali", "punjabi", "gujarati",
            "odia", "bhojpuri", "japanese", "korean", "chinese", "spanish", "thai", "arabic",
        )

    private val NON_LETTER = Regex("[^\\p{L}]+")
    private val NEUTRAL = setOf(UnicodeScript.COMMON, UnicodeScript.INHERITED, UnicodeScript.UNKNOWN)

    /** Non-Latin letters must be at least a third of all letters to count. */
    private const val MIN_SHARE_DENOMINATOR = 3

    /** How many of the first suggestions decide a radio's language when the seed's can't be told. */
    private const val MAJORITY_SAMPLE = 20
    private const val MIN_LABELLED = 5
}
