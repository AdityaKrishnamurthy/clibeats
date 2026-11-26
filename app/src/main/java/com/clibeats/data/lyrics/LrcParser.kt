@file:Suppress("ReturnCount", "MagicNumber")

package com.clibeats.data.lyrics

import com.clibeats.domain.model.LyricLine
import com.clibeats.domain.model.LyricWord
import com.clibeats.domain.model.Lyrics

/**
 * Reads lyrics text as found in audio tags and .lrc files:
 * - Syllable / word sync (enhanced LRC): `[00:09.731]<00:09.731>The <00:09.927><00:09.927>club ...`. A word's
 *   start is the tag before it and its end the tag after it, so both "start only" and "start + end" styles work.
 * - Line sync: `[00:19.09]Headlights`, several times per line allowed (`[00:12.00][01:30.00]Chorus`).
 * - Plain text: anything without line times.
 *
 * Header tags (`[ti:]`, `[ar:]`, `[by:]` …) are dropped and `[offset:]` is applied. Voice labels some
 * downloaders add (`v1:`, `v2:`) and background-vocal lines (`[bg: …]`) are removed.
 */
object LrcParser {
    /** Null when [raw] has nothing to show (blank, or only header tags). */
    fun parse(raw: String?): Lyrics? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var offsetMs = 0L
        val timed = mutableListOf<Pending>()
        val plain = mutableListOf<String>()
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd()
            val stamps = LINE_STAMPS.find(line)
            when {
                stamps != null -> {
                    val body = cleanBody(line.substring(stamps.range.last + 1))
                    LINE_STAMP.findAll(stamps.value).mapNotNull { timeOf(it.groupValues) }.forEach { start ->
                        timed += Pending(start, body, index)
                    }
                }
                line.trimStart().startsWith("[bg:", ignoreCase = true) -> Unit
                HEADER.matches(line.trim()) -> {
                    val (key, value) = HEADER.find(line.trim())!!.destructured
                    if (key.equals("offset", ignoreCase = true)) offsetMs = value.trim().toLongOrNull() ?: offsetMs
                }
                else -> plain += line
            }
        }
        if (timed.isEmpty()) return plainLyrics(plain)
        // A positive offset shows the lyrics sooner.
        return Lyrics.Synced(buildLines(timed, offsetMs))
    }

    private class Pending(
        val startMs: Long,
        val body: String,
        val order: Int,
    )

    private fun buildLines(
        timed: List<Pending>,
        offsetMs: Long,
    ): List<LyricLine> {
        val sorted = timed.sortedWith(compareBy<Pending> { it.startMs }.thenBy { it.order })
        val parsed =
            sorted.map { pending ->
                val start = (pending.startMs - offsetMs).coerceAtLeast(0L)
                // Leading blanks dropped and the last word's trailing space trimmed, so the words joined are
                // exactly the line's text (the screen highlights them by character position).
                val words =
                    parseWords(pending.body, start, offsetMs)?.dropWhile { it.text.isBlank() }?.trimLastSpace()
                val text = if (words.isNullOrEmpty()) pending.body.trim() else words.joinToString("") { it.text }
                Triple(start, text, words.orEmpty())
            }
        return parsed.mapIndexed { i, (start, text, words) ->
            val nextStart = parsed.getOrNull(i + 1)?.first
            val end =
                when {
                    words.isNotEmpty() -> maxOf(words.last().endMs, start)
                    nextStart != null -> maxOf(nextStart, start)
                    else -> start + LAST_LINE_MS
                }
            LyricLine(start, end, text, words)
        }
    }

    /**
     * The words of an enhanced-LRC line body, or null when it has no word tags (a line-timed line). Text before
     * the first tag starts with the line.
     */
    private fun parseWords(
        body: String,
        lineStart: Long,
        offsetMs: Long,
    ): List<LyricWord>? {
        val tags = WORD_STAMP.findAll(body).toList()
        if (tags.isEmpty()) return null
        // Pieces in order: times and texts.
        val times = tags.map { (timeOf(it.groupValues) ?: lineStart + offsetMs) - offsetMs }
        val texts = mutableListOf<Pair<Int, String>>() // (index of the tag before it, -1 = none) to text
        var cursor = 0
        tags.forEachIndexed { i, tag ->
            if (tag.range.first > cursor) texts += (i - 1) to body.substring(cursor, tag.range.first)
            cursor = tag.range.last + 1
        }
        if (cursor < body.length) texts += (tags.size - 1) to body.substring(cursor)
        val words =
            texts.mapIndexedNotNull { n, (before, text) ->
                if (text.isEmpty()) return@mapIndexedNotNull null
                val start = if (before >= 0) times[before] else lineStart
                // The tag right after this text ends it; with no tag after, the next word's start (or a guess).
                val after = before + 1
                val end =
                    when {
                        after < times.size -> times[after]
                        else -> texts.getOrNull(n + 1)?.first?.let { times.getOrNull(it) } ?: (start + LAST_WORD_MS)
                    }
                LyricWord(text, start.coerceAtLeast(0L), maxOf(end, start).coerceAtLeast(0L))
            }
        return words.takeIf { list -> list.any { it.text.isNotBlank() } }
    }

    private fun List<LyricWord>.trimLastSpace(): List<LyricWord> =
        if (isEmpty()) this else dropLast(1) + last().copy(text = last().text.trimEnd())

    /** Line text without a voice label ("v1:") in front. */
    private fun cleanBody(body: String): String = body.replace(VOICE_LABEL, "")

    private fun plainLyrics(lines: List<String>): Lyrics? {
        val text =
            lines
                .dropWhile { it.isBlank() }
                .dropLastWhile { it.isBlank() }
                .joinToString("\n") { it.trim() }
                // At most one blank line between verses.
                .replace(Regex("\n{3,}"), "\n\n")
        return text.takeIf { it.isNotBlank() }?.let { Lyrics.Plain(it) }
    }

    /** m:ss, m:ss.x, m:ss.xx, m:ss.xxx (also ":" before the fraction); the fraction is in tenths/hundredths/ms. */
    private fun timeOf(groups: List<String>): Long? {
        val minutes = groups[1].toLongOrNull() ?: return null
        val seconds = groups[2].toLongOrNull() ?: return null
        val fraction = groups[3]
        val millis =
            when (fraction.length) {
                0 -> 0L
                1 -> fraction.toLong() * 100
                2 -> fraction.toLong() * 10
                else -> fraction.take(3).toLong()
            }
        return minutes * 60_000 + seconds * 1_000 + millis
    }

    private const val TIME = """(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?"""
    private val LINE_STAMP = Regex("""\[$TIME]""")
    private val LINE_STAMPS = Regex("""^\s*(?:\[$TIME]\s*)+""")
    private val WORD_STAMP = Regex("""<$TIME>""")
    private val HEADER = Regex("""^\[([A-Za-z#]+):(.*)]$""")
    private val VOICE_LABEL = Regex("""^\s*v\d{1,2}\s*:\s*""", RegexOption.IGNORE_CASE)

    /** How long the last line stays lit when nothing says when it ends. */
    private const val LAST_LINE_MS = 5_000L
    private const val LAST_WORD_MS = 600L
}
