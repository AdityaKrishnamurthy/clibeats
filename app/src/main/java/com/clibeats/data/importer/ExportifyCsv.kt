package com.clibeats.data.importer

/** One song row from an Exportify playlist file. [artists] keeps Spotify's order (main artist first). */
data class SpotifySong(
    val title: String,
    val artists: List<String>,
    val album: String,
    val durationMs: Long,
) {
    val artistLine: String get() = artists.joinToString(", ")
}

/** A playlist read from one Exportify `.csv` file. */
data class SpotifyPlaylist(
    val name: String,
    val songs: List<SpotifySong>,
)

/** The file isn't an Exportify export (or is damaged); [message] is shown to the user as is. */
class NotAnExportifyFileException(
    message: String,
) : Exception(message)

/**
 * Reads the CSV files that exportify.net produces.
 *
 * Header row (columns we use): `Track Name`, `Artist Name(s)` (artists joined with `;`),
 * `Album Name`, `Duration (ms)`. Every other column is ignored, so newer Exportify versions with
 * extra columns still work.
 */
object ExportifyCsv {
    private const val COL_TITLE = "Track Name"
    private const val COL_ARTISTS = "Artist Name(s)"
    private const val COL_ALBUM = "Album Name"
    private const val COL_DURATION = "Duration (ms)"

    /** Turns `anime.csv` / `My_Top_Mix_2024.csv` into "anime" / "My Top Mix 2024". */
    fun playlistName(fileName: String): String =
        fileName
            .substringAfterLast('/')
            .removeSuffix(".csv")
            .removeSuffix(".CSV")
            .replace('_', ' ')
            .trim()
            .ifEmpty { "Imported playlist" }

    /** Parses one Exportify file. Rows without a title (e.g. podcast episodes, blank lines) are skipped. */
    fun parse(
        name: String,
        text: String,
    ): SpotifyPlaylist {
        val rows = parseCsv(text.removePrefix("﻿"))
        val header = rows.firstOrNull()?.map { it.trim() }
        val title = header?.indexOf(COL_TITLE) ?: -1
        val artists = header?.indexOf(COL_ARTISTS) ?: -1
        if (title < 0 || artists < 0) {
            throw NotAnExportifyFileException("\"$name\" doesn't look like an Exportify playlist file.")
        }
        val album = header!!.indexOf(COL_ALBUM)
        val duration = header.indexOf(COL_DURATION)
        val songs =
            rows.drop(1).mapNotNull { row ->
                val songTitle = row.getOrNull(title)?.trim().orEmpty()
                if (songTitle.isEmpty()) return@mapNotNull null
                SpotifySong(
                    title = songTitle,
                    artists =
                        row.getOrNull(artists).orEmpty()
                            .split(';')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() },
                    album = row.getOrNull(album)?.trim().orEmpty(),
                    durationMs = row.getOrNull(duration)?.trim()?.toLongOrNull() ?: 0L,
                )
            }
        return SpotifyPlaylist(name = name, songs = songs)
    }

    /**
     * Minimal RFC 4180 reader: fields may be quoted, quoted fields may hold commas, line breaks and
     * doubled quotes (`""`). Handles `\n` and `\r\n` line endings. Blank lines are dropped.
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endRow() {
            row.add(field.toString())
            field.clear()
            if (row.size > 1 || row[0].isNotEmpty()) rows.add(row)
            row = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (text.getOrNull(i + 1) == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    ',' -> {
                        row.add(field.toString())
                        field.clear()
                    }
                    '\r' -> Unit
                    '\n' -> endRow()
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
