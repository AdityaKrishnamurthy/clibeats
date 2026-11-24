package com.clibeats.data.importer

/** One song row from an Exportify playlist file. [artists] keeps Spotify's order (main artist first). */
data class SpotifySong(
    val title: String,
    val artists: List<String>,
    val album: String,
    val durationMs: Long,
    /** The recording's ISRC code; YouTube Music finds many official tracks by it. May be empty. */
    val isrc: String = "",
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
 * Reads playlist CSV exports from exportify.net and tunemymusic.com ("Export to file").
 *
 * - Exportify: one playlist per file, named after the file. Columns used: `Track Name`, `Artist Name(s)`
 *   (artists joined with `;`), `Album Name`, `Duration (ms)`, `ISRC`.
 * - TuneMyMusic: one file holding several playlists, told apart by `Playlist name`. Columns used:
 *   `Track name`, `Artist name` (artists joined with `,`), `Album`, `ISRC`, `Type`. No song length.
 *   Rows of type `Artist` are followed channels/artists, not songs, and are skipped.
 *
 * Column names are matched ignoring case; other columns are ignored, so newer versions with extra columns
 * still work.
 */
object ExportifyCsv {
    private val COL_TITLE = listOf("track name")
    private val COL_ARTISTS = listOf("artist name(s)", "artist name", "artist")
    private val COL_ALBUM = listOf("album name", "album")
    private val COL_DURATION = listOf("duration (ms)")
    private val COL_ISRC = listOf("isrc")
    private val COL_PLAYLIST = listOf("playlist name")
    private val COL_TYPE = listOf("type")
    private const val TYPE_ARTIST = "artist"

    /** Turns `anime.csv` / `My_Top_Mix_2024.csv` into "anime" / "My Top Mix 2024". */
    fun playlistName(fileName: String): String =
        fileName
            .substringAfterLast('/')
            .removeSuffix(".csv")
            .removeSuffix(".CSV")
            .replace('_', ' ')
            .trim()
            .ifEmpty { "Imported playlist" }

    /**
     * Parses one export file into its playlists: one for an Exportify file (named [name]), one per
     * `Playlist name` for a TuneMyMusic file, in the order they first appear. Rows without a title (podcast
     * episodes, blank lines) are skipped.
     */
    fun parse(
        name: String,
        text: String,
    ): List<SpotifyPlaylist> {
        val rows = parseCsv(text.removePrefix("\uFEFF"))
        val header = rows.firstOrNull()?.map { it.trim().lowercase() }.orEmpty()

        fun column(names: List<String>) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val title = column(COL_TITLE)
        val artists = column(COL_ARTISTS)
        if (title < 0 || artists < 0) {
            throw NotAnExportifyFileException("\"$name\" doesn't look like an Exportify or TuneMyMusic playlist file.")
        }
        val album = column(COL_ALBUM)
        val duration = column(COL_DURATION)
        val isrc = column(COL_ISRC)
        val playlist = column(COL_PLAYLIST)
        val type = column(COL_TYPE)
        // Exportify joins artists with ";", TuneMyMusic with ",".
        val separators = if (playlist >= 0) Regex("[;,]") else Regex(";")
        val byPlaylist = LinkedHashMap<String, MutableList<SpotifySong>>()
        rows.drop(1).forEach { row ->
            fun cell(i: Int) = row.getOrNull(i)?.trim().orEmpty()
            val songTitle = cell(title)
            if (songTitle.isEmpty() || cell(type).equals(TYPE_ARTIST, ignoreCase = true)) return@forEach
            val song =
                SpotifySong(
                    title = songTitle,
                    artists = cell(artists).split(separators).map { it.trim() }.filter { it.isNotEmpty() },
                    album = cell(album),
                    durationMs = cell(duration).toLongOrNull() ?: 0L,
                    isrc = cell(isrc),
                )
            val listName = if (playlist >= 0) cell(playlist).ifEmpty { name } else name
            byPlaylist.getOrPut(listName) { mutableListOf() } += song
        }
        if (byPlaylist.isEmpty()) return listOf(SpotifyPlaylist(name = name, songs = emptyList()))
        return byPlaylist.map { (listName, songs) -> SpotifyPlaylist(name = listName, songs = songs) }
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
