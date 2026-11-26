@file:Suppress("MagicNumber", "ReturnCount", "TooManyFunctions")

package com.clibeats.data.lyrics

import com.clibeats.domain.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds a translation (into the phone's language) and a pronunciation in Latin letters to each lyric line, using
 * Google Translate's free web endpoint.
 * - A whole song takes one or two requests (lines are sent together and split back apart).
 * - Lines that already have them (LyricsPlus sends both) aren't asked for again; results are kept by the caller.
 * - It never fails loudly: a request that doesn't work is retried a couple of times, quietly; lines it couldn't
 *   get simply show without the extra line, and are asked for again next time.
 * - Lines already in Latin letters get no pronunciation; a translation that's the same as the line is dropped.
 */
@Singleton
class LyricsTranslator
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
    ) {
        private val http = okHttpClient.newBuilder().callTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
        private val json = Json { ignoreUnknownKeys = true }

        /** [lyrics] with translations (when [translate]) and pronunciations (when [pronounce]) filled in. */
        suspend fun enrich(
            lyrics: Lyrics,
            translate: Boolean,
            pronounce: Boolean,
            targetLanguage: String,
        ): Lyrics =
            withContext(Dispatchers.IO) {
                val texts =
                    when (lyrics) {
                        is Lyrics.Synced -> lyrics.lines.map { it.text }
                        is Lyrics.Plain -> lyrics.text.lines()
                    }
                val oldTranslations =
                    when (lyrics) {
                        is Lyrics.Synced -> lyrics.lines.map { it.translation }
                        is Lyrics.Plain -> padded(lyrics.translations, texts.size)
                    }
                val oldPronunciations =
                    when (lyrics) {
                        is Lyrics.Synced -> lyrics.lines.map { it.pronunciation }
                        is Lyrics.Plain -> padded(lyrics.pronunciations, texts.size)
                    }
                val translations =
                    if (translate) translations(texts, oldTranslations, targetLanguage) else oldTranslations
                val pronunciations = if (pronounce) pronunciations(texts, oldPronunciations) else oldPronunciations
                when (lyrics) {
                    is Lyrics.Synced ->
                        lyrics.copy(
                            lines =
                                lyrics.lines.mapIndexed { i, line ->
                                    line.copy(translation = translations[i], pronunciation = pronunciations[i])
                                },
                        )
                    is Lyrics.Plain -> lyrics.copy(translations = translations, pronunciations = pronunciations)
                }
            }

        private suspend fun translations(
            texts: List<String>,
            existing: List<String?>,
            target: String,
        ): List<String?> = fill(texts, existing) { translateAll(it, target) }

        private suspend fun pronunciations(
            texts: List<String>,
            existing: List<String?>,
        ): List<String?> = fill(texts, existing, ::needsPronunciation, ::romanizeAll)

        private fun padded(
            list: List<String?>,
            size: Int,
        ): List<String?> = List(size) { list.getOrNull(it) }

        /**
         * [existing] with the gaps filled for lines that want one ([wanted]). "" marks "nothing to show" (same as
         * the line, or no letters), so it isn't asked for again.
         */
        private suspend fun fill(
            texts: List<String>,
            existing: List<String?>,
            wanted: (String) -> Boolean = { it.any(Char::isLetter) },
            fetch: suspend (List<String>) -> List<String?>,
        ): List<String?> {
            val missing = texts.indices.filter { existing.getOrNull(it) == null && texts[it].isNotBlank() }
            if (missing.isEmpty()) return existing
            val result = existing.toMutableList()
            val toAsk = missing.filter { wanted(texts[it]) }
            missing.filterNot { wanted(texts[it]) }.forEach { result[it] = "" }
            for (chunk in chunked(toAsk, texts)) {
                val answers = fetch(chunk.map { texts[it] })
                chunk.forEachIndexed { n, index ->
                    val answer = answers.getOrNull(n)?.trim() ?: return@forEachIndexed
                    result[index] = if (sameText(answer, texts[index])) "" else answer
                }
            }
            return result
        }

        /** Line indexes in groups small enough for one request. */
        private fun chunked(
            indexes: List<Int>,
            texts: List<String>,
        ): List<List<Int>> {
            val chunks = mutableListOf<MutableList<Int>>()
            var size = 0
            for (i in indexes) {
                if (chunks.isEmpty() || size + texts[i].length > CHUNK_CHARS) {
                    chunks += mutableListOf<Int>()
                    size = 0
                }
                chunks.last() += i
                size += texts[i].length + 3
            }
            return chunks
        }

        /** Translations of [lines], in order (null where one couldn't be had). */
        private suspend fun translateAll(
            lines: List<String>,
            target: String,
        ): List<String?> {
            // No answer at all (offline): leave them for next time rather than asking line by line.
            val answer = request(lines.joinToString("\n"), target, "t") ?: return List(lines.size) { null }
            val translated =
                answer.mapNotNull {
                    (it as? JsonArray)?.getOrNull(0)?.let {
                            v ->
                        runCatching { v.jsonPrimitive.contentOrNull }.getOrNull()
                    }
                }
                    .joinToString("")
                    .split('\n')
                    .map { it.trim() }
            return if (translated.size == lines.size) translated else perLine(lines) { translateOne(it, target) }
        }

        private suspend fun translateOne(
            line: String,
            target: String,
        ): String? =
            request(line, target, "t")
                ?.mapNotNull { segmentPart(it, 0) }
                ?.joinToString("")
                ?.takeIf { it.isNotBlank() }

        /** Pronunciations of [lines]: sent joined by " ¦ " (Google keeps that mark), then split back apart. */
        private suspend fun romanizeAll(lines: List<String>): List<String?> {
            val answer = request(lines.joinToString(SEPARATOR), "en", "rm") ?: return List(lines.size) { null }
            val parts = romanizedText(answer)?.split('¦')?.map { it.trim() }
            return if (parts != null && parts.size == lines.size) parts else perLine(lines, ::romanizeOne)
        }

        private suspend fun romanizeOne(line: String): String? = request(line, "en", "rm")?.let(::romanizedText)

        /** The romanized source text in a `dt=rm` answer: the segment whose 4th item is a string. */
        private fun romanizedText(segments: JsonArray): String? =
            segments.firstNotNullOfOrNull { segment ->
                segmentPart(segment, 3)
            }

        private suspend fun perLine(
            lines: List<String>,
            one: suspend (String) -> String?,
        ): List<String?> {
            // Asked one by one only when a batch came back in a shape that couldn't be split (rare); a line that
            // fails stops the rest, to keep it quick.
            val out = MutableList<String?>(lines.size) { null }
            for ((i, line) in lines.withIndex()) {
                out[i] = if (line.isBlank()) "" else one(line) ?: break
            }
            return out
        }

        /**
         * One call to the endpoint with a couple of quiet retries; the answer's first array (the segments), or null
         * if it didn't work.
         */
        private suspend fun request(
            text: String,
            target: String,
            kind: String,
        ): JsonArray? {
            repeat(ATTEMPTS) { attempt ->
                val answer =
                    runCatching {
                        val body = FormBody.Builder().add("q", text).build()
                        val request =
                            Request.Builder()
                                .url("$ENDPOINT?client=gtx&sl=auto&tl=$target&dt=$kind")
                                .header("User-Agent", "Mozilla/5.0")
                                .post(body)
                                .build()
                        http.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) null else response.body?.string()
                        }
                    }.getOrNull()
                val segments =
                    answer?.let {
                        runCatching {
                            (
                                json.parseToJsonElement(
                                    it,
                                ) as? JsonArray
                            )?.getOrNull(0) as? JsonArray
                        }.getOrNull()
                    }
                if (segments != null) return segments
                delay(RETRY_DELAYS_MS.getOrElse(attempt) { 2_000L })
            }
            return null
        }

        private companion object {
            const val REQUEST_TIMEOUT_MS = 12_000L
            const val CHUNK_CHARS = 1_800
            const val ATTEMPTS = 3
            val RETRY_DELAYS_MS = listOf(600L, 1_500L, 0L)
            const val SEPARATOR = " ¦ "
            const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
        }
    }

/** True for lines with letters outside the Latin alphabet (Japanese, Hindi, Korean, Kannada, Cyrillic…). */
internal fun needsPronunciation(text: String): Boolean =
    text.any { ch ->
        ch.isLetter() && Character.UnicodeScript.of(ch.code) !in LATIN_SCRIPTS
    }

/** Same words, ignoring case, spaces and punctuation. */
internal fun sameText(
    a: String,
    b: String,
): Boolean {
    fun key(s: String) = s.lowercase().filter(Char::isLetterOrDigit)
    return key(a) == key(b)
}

private val LATIN_SCRIPTS = setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.COMMON)

/** Item [index] of one answer segment (`[translated, original, …, romanized]`), as text. */
private fun segmentPart(
    segment: kotlinx.serialization.json.JsonElement,
    index: Int,
): String? {
    val item = (segment as? JsonArray)?.getOrNull(index) ?: return null
    return runCatching { item.jsonPrimitive.contentOrNull }.getOrNull()
}
