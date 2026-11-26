package com.clibeats.domain.model

import kotlinx.serialization.Serializable

/** A song's lyrics: timed (line by line, maybe word by word) or plain text. */
@Serializable
sealed interface Lyrics {
    /** Where they came from ("LyricsPlus · Apple", "LRCLIB", …); null for the song's own file. */
    val source: String?

    /**
     * Timed lyrics, lines in time order. [hasWordTiming] is true when lines carry per-word (or per-syllable)
     * times, as in enhanced LRC: `[00:09.73]<00:09.73>The <00:09.92>club`.
     */
    @Serializable
    data class Synced(
        val lines: List<LyricLine>,
        override val source: String? = null,
    ) : Lyrics {
        val hasWordTiming: Boolean get() = lines.any { it.words.isNotEmpty() }
    }

    /** Lyrics without times: shown as text, line by line ([lines] keeps the blank lines between verses). */
    @Serializable
    data class Plain(
        val text: String,
        override val source: String? = null,
        /** Per line of [text] (same indexes as `text.lines()`); null where there is none. */
        val translations: List<String?> = emptyList(),
        val pronunciations: List<String?> = emptyList(),
    ) : Lyrics
}

/**
 * One timed line. [endMs] is when its last word ends (word-timed lines) or when the next line starts.
 * A line with blank [text] marks an instrumental break.
 */
@Serializable
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    /** Per-word (per-syllable) timing; empty for line-timed lyrics. Their texts joined make [text]. */
    val words: List<LyricWord> = emptyList(),
    /** What the line means, in the phone's language (null: not fetched, or the same as [text]). */
    val translation: String? = null,
    /** How to say it in Latin letters ("sanzan na omoide wa…"); null for lines already in Latin letters. */
    val pronunciation: String? = null,
)

/** One word or syllable, with its own trailing space when it ends a word ("Come ", "ア・"). */
@Serializable
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)
