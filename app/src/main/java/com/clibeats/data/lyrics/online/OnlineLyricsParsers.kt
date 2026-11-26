@file:Suppress("MagicNumber", "ReturnCount", "TooManyFunctions", "ForbiddenImport", "CyclomaticComplexMethod")

package com.clibeats.data.lyrics.online

import com.clibeats.data.lyrics.LrcParser
import com.clibeats.domain.model.LyricLine
import com.clibeats.domain.model.LyricWord
import com.clibeats.domain.model.Lyrics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/*
 * Turns each online source's answer into [Lyrics]. Pure functions (no network), so they're unit-tested with
 * real responses.
 */

private val json = Json { ignoreUnknownKeys = true }

/** What we search with. [durationSec] is used to reject lyrics for a different version of the song. */
data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val videoId: String?,
)

/**
 * LyricsPlus (KPoe) JSON: `{"type":"Word"|"Line"|…, "metadata":{"source":"Apple"}, "lyrics":[{time, duration,
 * text, syllabus:[{time,duration,text}], translation:{text}, transliteration:{text}}]}`, times in ms.
 * Brings its own translation and pronunciation when it has them.
 */
internal fun parseLyricsPlus(
    body: String,
    artist: String? = null,
): Lyrics? {
    val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
    val items = root["lyrics"] as? JsonArray ?: return null
    val origin = (root["metadata"] as? JsonObject)?.string("source")
    val type = root.string("type")
    val source = listOfNotNull("LyricsPlus", origin).joinToString(" · ")
    val lines =
        items.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val start = item.long("time") ?: return@mapNotNull null
            val duration = item.long("duration") ?: 0L
            val words =
                (item["syllabus"] as? JsonArray).orEmpty().mapNotNull { syllable ->
                    val obj = syllable as? JsonObject ?: return@mapNotNull null
                    if (obj.bool("isBackground")) return@mapNotNull null
                    val text = obj.string("text") ?: return@mapNotNull null
                    val at = obj.long("time") ?: return@mapNotNull null
                    LyricWord(text, at, at + (obj.long("duration") ?: 0L))
                }.let(::mergeSpaces)
            val text = words.joinedOr(item.string("text").orEmpty())
            LyricLine(
                startMs = start,
                endMs = start + duration,
                text = text,
                words = if (type.equals("Line", ignoreCase = true)) emptyList() else words.trimEdges(),
                translation = item.nestedText("translation"),
                pronunciation = item.nestedText("transliteration"),
            )
        }
    return syncedOrNull(lines, source, artist)
}

/**
 * Apple Music TTML (BetterLyrics): `<p begin end><span begin end>word</span> <span …>…</span></p>`. Background
 * vocals (`ttm:role="x-bg"`) are left out, like the local lyrics.
 */
internal fun parseTtml(
    ttml: String,
    source: String,
): Lyrics? {
    val wordTimed = ttml.contains("itunes:timing=\"Word\"")
    val lines =
        P_TAG.findAll(ttml).mapNotNull { match ->
            val start = timeAttr(match.groupValues[1], "begin") ?: return@mapNotNull null
            val end = timeAttr(match.groupValues[1], "end") ?: start
            val inner = removeBackground(match.groupValues[2])
            val words = mutableListOf<LyricWord>()
            var cursor = 0
            SPAN.findAll(inner).forEach { span ->
                // Text between spans (usually the space between words) belongs to the word before it.
                val between = decodeXml(stripTags(inner.substring(cursor, span.range.first)))
                if (between.isNotEmpty() && words.isNotEmpty()) {
                    words[words.lastIndex] = words.last().let { it.copy(text = it.text + between) }
                }
                val wStart = timeAttr(span.groupValues[1], "begin")
                val wEnd = timeAttr(span.groupValues[1], "end")
                val text = decodeXml(stripTags(span.groupValues[2]))
                if (wStart != null && text.isNotEmpty()) words += LyricWord(text, wStart, wEnd ?: wStart)
                cursor = span.range.last + 1
            }
            val text = words.joinedOr(decodeXml(stripTags(inner)))
            LyricLine(start, end, text, if (wordTimed) words.trimEdges() else emptyList())
        }.toList()
    return syncedOrNull(lines, source)
}

/** LRCLIB `/api/get` (or one item of `/api/search`): synced LRC when there is one, else the plain text. */
internal fun parseLrclib(body: String): Lyrics? {
    val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
    return lrclibItem(obj)
}

/** LRCLIB `/api/search`: the item closest in length to [durationSec] (within the allowed difference). */
internal fun parseLrclibSearch(
    body: String,
    durationSec: Int,
): Lyrics? {
    val items = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return null
    return items
        .mapNotNull { it as? JsonObject }
        .filter { durationSec <= 0 || closeEnough(it.double("duration") ?: 0.0, durationSec) }
        .sortedByDescending { !it.string("syncedLyrics").isNullOrBlank() }
        .firstNotNullOfOrNull(::lrclibItem)
}

private fun lrclibItem(obj: JsonObject): Lyrics? {
    if (obj.bool("instrumental")) return null
    val synced = obj.string("syncedLyrics")?.let(LrcParser::parse) as? Lyrics.Synced
    if (synced != null) return synced.copy(source = "LRCLIB")
    val plain = obj.string("plainLyrics")?.let(LrcParser::parse) as? Lyrics.Plain
    return plain?.copy(source = "LRCLIB")
}

/** The duration LRCLIB gives for a match, in seconds (null when missing). */
internal fun lrclibDuration(body: String): Double? =
    runCatching { (json.parseToJsonElement(body) as? JsonObject)?.double("duration") }.getOrNull()

/** lyrics.ovh: `{"lyrics":"…"}`, plain text only. */
internal fun parseLyricsOvh(body: String): Lyrics? {
    val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
    val text = obj.string("lyrics") ?: return null
    // lyrics.ovh sometimes starts with "Paroles de la chanson … par …".
    val cleaned =
        text.replace("\r\n", "\n").lines().dropWhile { it.startsWith("Paroles de la chanson") }.joinToString("\n")
    return (LrcParser.parse(cleaned) as? Lyrics.Plain)?.copy(source = "lyrics.ovh")
}

/** The TTML inside BetterLyrics' `{"ttml":"…"}` answer. */
internal fun betterLyricsTtml(body: String): String? =
    runCatching { (json.parseToJsonElement(body) as? JsonObject)?.string("ttml") }
        .getOrNull()
        ?.takeIf { it.contains("<p") }

/** The song's length a TTML document gives (`<body dur="3:53.713">`), in seconds. */
internal fun ttmlDurationSec(ttml: String): Double? {
    val value = BODY_DUR.find(ttml)?.groupValues?.get(1) ?: return null
    return clockMs(value)?.div(1000.0)
}

/**
 * Lines in order, with credits dropped from the start (some sources open with "Song - Artist" or
 * "Writer/Writer/Writer" lines timed at 0 s), and line ends filled in where a source left them out.
 */
private fun syncedOrNull(
    raw: List<LyricLine>,
    source: String,
    artist: String? = null,
): Lyrics.Synced? {
    val sorted = raw.sortedBy { it.startMs }.dropWhile { isCreditLine(it, artist) }
    if (sorted.none { it.text.isNotBlank() }) return null
    val lines =
        sorted.mapIndexed { i, line ->
            val wordsEnd = line.words.lastOrNull()?.endMs ?: 0L
            val nextStart = sorted.getOrNull(i + 1)?.startMs
            val end =
                when {
                    line.words.isNotEmpty() -> maxOf(wordsEnd, line.startMs)
                    line.endMs > line.startMs -> line.endMs
                    nextStart != null -> nextStart
                    else -> line.startMs + 5_000L
                }
            line.copy(endMs = end)
        }
    return Lyrics.Synced(lines, source)
}

/** "Ed Sheeran/Steve Mac/Johnny McDaid", "作词：…", "Lyrics by …" near the very start. */
private fun isCreditLine(
    line: LyricLine,
    artist: String?,
): Boolean {
    if (line.startMs > CREDITS_UNTIL_MS) return false
    val text = line.text.trim()
    val titleLine = !artist.isNullOrBlank() && text.contains(" - ") && text.contains(artist, ignoreCase = true)
    return titleLine || CREDIT_WORDS.containsMatchIn(text) || NAMES_WITH_SLASHES.matches(text)
}

/** Spaces some sources send as their own syllables (" ") go onto the syllable before them. */
private fun mergeSpaces(words: List<LyricWord>): List<LyricWord> {
    val out = mutableListOf<LyricWord>()
    for (word in words) {
        if (word.text.isBlank() && out.isNotEmpty()) {
            out[out.lastIndex] = out.last().let { it.copy(text = it.text + word.text) }
        } else {
            out += word
        }
    }
    return out
}

/** No leading blank pieces and no trailing space, so the words joined equal the line's text. */
private fun List<LyricWord>.trimEdges(): List<LyricWord> {
    val list = dropWhile { it.text.isBlank() }.toMutableList()
    if (list.isNotEmpty()) list[0] = list[0].copy(text = list[0].text.trimStart())
    if (list.isNotEmpty()) list[list.lastIndex] = list.last().copy(text = list.last().text.trimEnd())
    return list
}

private fun removeBackground(inner: String): String {
    var text = inner
    while (true) {
        val start = BG_OPEN.find(text) ?: return text
        // Find the matching </span>, counting nested spans.
        var depth = 0
        var at = start.range.first
        var end = -1
        val tag = Regex("<span\\b[^>]*>|</span>")
        for (m in tag.findAll(text, at)) {
            if (m.value.startsWith("</")) depth-- else depth++
            if (depth == 0) {
                end = m.range.last + 1
                break
            }
            at = m.range.last
        }
        if (end < 0) return text.substring(0, start.range.first)
        text = text.removeRange(start.range.first, end)
    }
}

private fun timeAttr(
    attrs: String,
    name: String,
): Long? = Regex("""\b$name="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.let(::clockMs)

/** "9.731", "1:02.345", "1:02:03.4", "12.5s" → milliseconds. */
internal fun clockMs(value: String): Long? {
    val clean = value.trim().removeSuffix("s")
    val parts = clean.split(':')
    val seconds = parts.last().toDoubleOrNull() ?: return null
    val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0L
    val hours = if (parts.size >= 3) parts[parts.size - 3].toLongOrNull() ?: 0L else 0L
    return ((hours * 3600 + minutes * 60) * 1000 + seconds * 1000).toLong()
}

private fun stripTags(text: String): String = text.replace(Regex("<[^>]+>"), "")

private fun decodeXml(text: String): String =
    text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonElement)?.let {
        runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
    }

private fun JsonObject.long(key: String): Long? =
    this[key]?.let {
        runCatching { it.jsonPrimitive.longOrNull ?: it.jsonPrimitive.doubleOrNull?.toLong() }.getOrNull()
    }

private fun JsonObject.double(key: String): Double? {
    val value = this[key] ?: return null
    return runCatching { value.jsonPrimitive.doubleOrNull }.getOrNull()
}

private fun JsonObject.bool(key: String): Boolean = string(key).equals("true", ignoreCase = true)

private val P_TAG = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
private val SPAN = Regex("""<span\b([^>]*\bbegin="[^"]*"[^>]*)>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
private val BG_OPEN = Regex("""<span\b[^>]*ttm:role="x-bg"[^>]*>""")
private val BODY_DUR = Regex("""<body\b[^>]*\bdur="([^"]+)"""")
private val CREDIT_WORDS =
    Regex(
        """(作词|作詞|作曲|编曲|編曲|制作|製作|词：|曲：|lyrics by|written by|composed by|producer|composer|lyricist)""",
        RegexOption.IGNORE_CASE,
    )
private val NAMES_WITH_SLASHES = Regex("""^[^/\s][^/]*(/[^/]+){1,}$""")
private const val CREDITS_UNTIL_MS = 15_000L

/** Lyrics for a version more than this many seconds longer or shorter are someone else's. */
internal const val MAX_DURATION_GAP_SEC = 12

/** The words joined (the line's text), or [fallback] for a line without word timing. */
private fun List<LyricWord>.joinedOr(fallback: String): String {
    if (isEmpty()) return fallback.trim()
    return joinToString("") { it.text }.trim()
}

/** `{"text": …}` under [key] (LyricsPlus' translation / transliteration), trimmed; null when empty. */
private fun JsonObject.nestedText(key: String): String? {
    val text = (this[key] as? JsonObject)?.string("text")?.trim()
    return text?.takeIf { it.isNotEmpty() }
}

/** Within [MAX_DURATION_GAP_SEC] of the song's length. */
internal fun closeEnough(
    seconds: Double,
    durationSec: Int,
): Boolean = kotlin.math.abs(seconds - durationSec) <= MAX_DURATION_GAP_SEC
