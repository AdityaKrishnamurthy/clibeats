package com.clibeats.domain.model

/** A song's lyrics: timed (line by line, maybe word by word) or plain text. */
sealed interface Lyrics {
    /**
     * Timed lyrics, lines in time order. [hasWordTiming] is true when lines carry per-word (or per-syllable)
     * times, as in enhanced LRC: `[00:09.73]<00:09.73>The <00:09.92>club`.
     */
    data class Synced(
        val lines: List<LyricLine>,
    ) : Lyrics {
        val hasWordTiming: Boolean get() = lines.any { it.words.isNotEmpty() }
    }

    /** Lyrics without times: shown as text. */
    data class Plain(
        val text: String,
    ) : Lyrics
}

/**
 * One timed line. [endMs] is when its last word ends (word-timed lines) or when the next line starts.
 * A line with blank [text] marks an instrumental break.
 */
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    /** Per-word (per-syllable) timing; empty for line-timed lyrics. Their texts joined make [text]. */
    val words: List<LyricWord> = emptyList(),
)

/** One word or syllable, with its own trailing space when it ends a word ("Come ", "ア・"). */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)
