package com.clibeats.data.home

import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeShelf
import com.clibeats.domain.radio.RadioLanguage

/** A Spotify Top 50 chart: its playlist id and name. */
data class SpotifyChart(
    val id: String,
    val name: String,
)

private fun top50(
    country: String,
    id: String,
) = SpotifyChart(id, "Top 50 - $country")

private val TOP50_GLOBAL = top50("Global", "37i9dQZEVXbMDoHDwVN2tF")
private val TOP50_INDIA = top50("India", "37i9dQZEVXbLZ52XmnySJg")

/**
 * A language the online Home can be tuned to.
 * - [genre]: the YouTube Music genre page with its songs and playlists ("Kannada", "J-Pop"); null when
 *   YouTube Music has none for it (then the language gets its country's charts).
 * - [chartCountry]: the country whose YouTube charts to show (ISO code).
 * - [spotifyCharts]: Spotify Top 50 lists in this language (ids checked against Spotify).
 * - [codes]: how the language is named by [HomeLanguageFilter] (from titles and scripts).
 */
enum class HomeLanguage(
    val label: String,
    val genre: String?,
    val chartCountry: String,
    val spotifyCharts: List<SpotifyChart> = emptyList(),
    val codes: Set<String>,
) {
    ENGLISH("English", "Pop", "US", listOf(TOP50_GLOBAL, top50("USA", "37i9dQZEVXbLRQDuF5jeBp")), setOf("english")),
    HINDI("Hindi", "Hindi", "IN", listOf(TOP50_INDIA), setOf("hindi")),
    KANNADA("Kannada", "Kannada", "IN", codes = setOf("kannada")),
    ARABIC(
        "Arabic",
        "Arabic",
        "EG",
        listOf(top50("Egypt", "37i9dQZEVXbLn7RQmT5Xv2"), top50("Saudi Arabia", "37i9dQZEVXbLrQBcXqUtaC")),
        setOf("arabic"),
    ),
    BENGALI("Bengali", "Bengali", "IN", codes = setOf("bengali")),
    BHOJPURI("Bhojpuri", "Bhojpuri", "IN", codes = setOf("bhojpuri")),
    CHINESE("Chinese", null, "TW", listOf(top50("Taiwan", "37i9dQZEVXbMnZEatlMSiu")), setOf("chinese")),
    DUTCH("Dutch", null, "NL", listOf(top50("Netherlands", "37i9dQZEVXbKCF6dqVpDkS")), setOf("dutch")),
    FILIPINO("Filipino", null, "PH", listOf(top50("Philippines", "37i9dQZEVXbNBz9cRCSFkY")), setOf("filipino")),
    FRENCH("French", null, "FR", listOf(top50("France", "37i9dQZEVXbIPWwFssbupI")), setOf("french")),
    GERMAN("German", null, "DE", listOf(top50("Germany", "37i9dQZEVXbJiZcmkrIHGU")), setOf("german")),
    GUJARATI("Gujarati", "Gujarati", "IN", codes = setOf("gujarati")),
    HARYANVI("Haryanvi", "Haryanvi", "IN", codes = setOf("haryanvi")),
    HEBREW("Hebrew", null, "IL", listOf(top50("Israel", "37i9dQZEVXbJ6IpvItkve3")), setOf("hebrew")),
    INDONESIAN("Indonesian", null, "ID", listOf(top50("Indonesia", "37i9dQZEVXbObFQZ3JLcXt")), setOf("indonesian")),
    ITALIAN("Italian", null, "IT", listOf(top50("Italy", "37i9dQZEVXbIQnj7RRhdSX")), setOf("italian")),
    JAPANESE("Japanese", "J-Pop", "JP", listOf(top50("Japan", "37i9dQZEVXbKXQ4mDTEBXq")), setOf("japanese")),
    KOREAN("Korean", "K-Pop", "KR", listOf(top50("South Korea", "37i9dQZEVXbNxXF4SkHj9F")), setOf("korean")),
    MALAYALAM("Malayalam", "Malayalam", "IN", codes = setOf("malayalam")),
    MARATHI("Marathi", "Marathi", "IN", codes = setOf("marathi")),
    POLISH("Polish", null, "PL", listOf(top50("Poland", "37i9dQZEVXbN6itCcaL3Tt")), setOf("polish")),
    PORTUGUESE("Portuguese", null, "BR", listOf(top50("Brazil", "37i9dQZEVXbMXbN3EUUhlg")), setOf("portuguese")),
    PUNJABI("Punjabi", "Punjabi", "IN", listOf(TOP50_INDIA), setOf("punjabi")),
    RUSSIAN("Russian", null, "RU", codes = setOf("russian")),
    SPANISH(
        "Spanish",
        "Latin",
        "MX",
        listOf(top50("Mexico", "37i9dQZEVXbO3qyFxbkOE1"), top50("Spain", "37i9dQZEVXbNFJfN1Vw8d9")),
        setOf("spanish"),
    ),
    SWEDISH("Swedish", null, "SE", listOf(top50("Sweden", "37i9dQZEVXbLoATJ81JYXz")), setOf("swedish")),
    TAMIL("Tamil", "Tamil", "IN", codes = setOf("tamil")),
    TELUGU("Telugu", "Telugu", "IN", codes = setOf("telugu")),
    THAI("Thai", null, "TH", listOf(top50("Thailand", "37i9dQZEVXbMnz8KIWsvf9")), setOf("thai")),
    TURKISH("Turkish", null, "TR", listOf(top50("Turkey", "37i9dQZEVXbIVYVBNw9D5K")), setOf("turkish")),
    UKRAINIAN("Ukrainian", null, "UA", listOf(top50("Ukraine", "37i9dQZEVXbKkidEfWYRuD")), setOf("ukrainian")),
    URDU("Urdu", null, "PK", listOf(top50("Pakistan", "37i9dQZEVXbJkgIdfsJyTw")), setOf("urdu")),
    VIETNAMESE("Vietnamese", null, "VN", listOf(top50("Vietnam", "37i9dQZEVXbLdGSmz6xilI")), setOf("vietnamese")),
    ;

    companion object {
        private val PINNED = listOf(ENGLISH, HINDI, KANNADA)

        /** The picker's order: English, Hindi and Kannada first, then the rest alphabetically. */
        val ordered: List<HomeLanguage> = PINNED + entries.filterNot { it in PINNED }.sortedBy { it.label }

        /** Saved names back to languages (unknown names, e.g. from a newer version, are skipped). */
        fun fromNames(names: Set<String>): Set<HomeLanguage> = ordered.filterTo(LinkedHashSet()) { it.name in names }
    }
}

/**
 * Keeps Home to the picked languages: items and rows in another language are hidden; anything whose
 * language can't be told (most titles in English letters) stays. With nothing picked, nothing is hidden.
 *
 * A language is told from a language named in the text ("Bhojpuri Hits", "Telugu good songs", "Sandalwood
 * Romance") or, failing that, the writing system (Kannada script, kana, Hangul, Devanagari for the Hindi
 * family, ...).
 */
object HomeLanguageFilter {
    fun filter(
        shelves: List<HomeShelf>,
        picked: Set<HomeLanguage>,
    ): List<HomeShelf> {
        if (picked.isEmpty()) return shelves
        val allowed = picked.flatMapTo(HashSet()) { it.codes }
        return shelves.mapNotNull { shelf ->
            if (!fits(allowed, languageOf(shelf.title, shelf.strapline))) return@mapNotNull null
            val items = shelf.items.filter { fits(allowed, languageOf(it)) }
            if (items.isEmpty()) null else shelf.copy(items = items)
        }
    }

    /** The languages of an item ("hindi", "japanese", ...); empty when it can't be told. */
    fun languageOf(item: HomeItem): Set<String> =
        when (item) {
            is HomeItem.Song -> languageOf(item.track.title, item.track.artist, item.track.album)
            is HomeItem.Collection -> languageOf(item.title, item.subtitle)
        }

    /**
     * Languages named in [texts] win; otherwise the script. Devanagari counts for the whole Hindi family
     * (Hindi, Marathi, Bhojpuri, Haryanvi all use it), and kanji alone for Japanese or Chinese.
     */
    fun languageOf(vararg texts: String): Set<String> {
        val words = texts.joinToString(" ").lowercase().split(NON_LETTER).filter { it.isNotEmpty() }
        val named = words.mapNotNullTo(LinkedHashSet()) { NAMED[it] }
        if (named.isNotEmpty()) return named
        return when (val script = RadioLanguage.scriptOf(*texts)) {
            null -> emptySet()
            "DEVANAGARI" -> setOf("hindi", "marathi", "bhojpuri", "haryanvi")
            "HAN" -> setOf("japanese", "chinese")
            "ARABIC" -> setOf("arabic", "urdu")
            "CYRILLIC" -> setOf("russian", "ukrainian")
            else -> setOfNotNull(SCRIPTS[script])
        }
    }

    /** Unknown language fits; otherwise any of its possible languages must be picked. */
    private fun fits(
        allowed: Set<String>,
        languages: Set<String>,
    ): Boolean = languages.isEmpty() || languages.any { it in allowed }

    private val NON_LETTER = Regex("[^\\p{L}]+")

    private val NAMED =
        mapOf(
            "hindi" to "hindi", "bollywood" to "hindi",
            "kannada" to "kannada", "sandalwood" to "kannada",
            "tamil" to "tamil", "kollywood" to "tamil",
            "telugu" to "telugu", "tollywood" to "telugu",
            "malayalam" to "malayalam", "mollywood" to "malayalam",
            "marathi" to "marathi", "bengali" to "bengali", "bangla" to "bengali", "gujarati" to "gujarati",
            "punjabi" to "punjabi", "bhojpuri" to "bhojpuri", "haryanvi" to "haryanvi", "odia" to "odia",
            "japanese" to "japanese", "japan" to "japanese", "jpop" to "japanese",
            "korean" to "korean", "korea" to "korean", "kpop" to "korean",
            "chinese" to "chinese", "mandarin" to "chinese", "cantonese" to "chinese", "cpop" to "chinese",
            "spanish" to "spanish", "latin" to "spanish", "latino" to "spanish", "reggaeton" to "spanish",
            "arabic" to "arabic", "urdu" to "urdu", "french" to "french", "german" to "german",
            "deutsch" to "german", "portuguese" to "portuguese", "brazilian" to "portuguese",
            "sertanejo" to "portuguese", "italian" to "italian", "turkish" to "turkish",
            "indonesian" to "indonesian", "vietnamese" to "vietnamese", "thai" to "thai",
            "filipino" to "filipino", "tagalog" to "filipino", "opm" to "filipino", "polish" to "polish",
            "dutch" to "dutch", "russian" to "russian", "ukrainian" to "ukrainian", "swedish" to "swedish",
            "hebrew" to "hebrew",
        )

    private val SCRIPTS =
        mapOf(
            "KANNADA" to "kannada",
            "TELUGU" to "telugu",
            "TAMIL" to "tamil",
            "MALAYALAM" to "malayalam",
            "BENGALI" to "bengali",
            "GURMUKHI" to "punjabi",
            "GUJARATI" to "gujarati",
            "ORIYA" to "odia",
            "JAPANESE" to "japanese",
            "HANGUL" to "korean",
            "THAI" to "thai",
            "HEBREW" to "hebrew",
        )
}
