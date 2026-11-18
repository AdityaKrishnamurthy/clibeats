package com.clibeats.data.scan

import java.net.URLDecoder

/**
 * Pure parsing/resolution for local playlist files (M3U, M3U8, PLS). No Android types,
 * so it is unit-tested directly.
 */
object PlaylistFileParser {
    private val PLAYLIST_EXTENSIONS = setOf("m3u", "m3u8", "pls")
    private val PLAYLIST_MIMES =
        setOf(
            "audio/x-mpegurl",
            "audio/mpegurl",
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/x-scpls",
        )
    private val PLS_FILE_LINE = Regex("""^File\d+\s*=\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val DRIVE_PREFIX = Regex("""^[a-z]:/""")

    fun isPlaylistFile(
        mime: String,
        name: String,
    ): Boolean = mime.lowercase() in PLAYLIST_MIMES || extension(name) in PLAYLIST_EXTENSIONS

    /** Raw entries in file order (duplicates kept; caller dedupes). */
    fun parseEntries(
        fileName: String,
        content: String,
    ): List<String> {
        val lines = content.trimStart(Char(BOM_CODE)).lineSequence().map { it.trim() }
        return if (extension(fileName) == "pls") {
            lines.mapNotNull { PLS_FILE_LINE.find(it)?.groupValues?.get(1)?.trim() }.toList()
        } else {
            lines.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        }
    }

    /**
     * Normalizes a path inside the music tree: forward slashes, lowercase, no leading slash,
     * `.`/`..` collapsed. Used for both map keys and lookups.
     */
    fun normalize(path: String): String {
        val parts = ArrayDeque<String>()
        path.replace('\\', '/').lowercase().split('/').forEach { seg ->
            when (seg) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(seg)
            }
        }
        return parts.joinToString("/")
    }

    /**
     * Resolves one entry to a song id.
     * @param playlistDir normalized directory of the playlist inside the tree ("" = root)
     * @param pathToId normalized relative path (inside the tree) -> song id
     * @param nameToIds lowercase filename -> song ids (fallback when the path doesn't match)
     */
    @Suppress("ReturnCount") // early returns read clearer than nested fallbacks here
    fun resolve(
        entry: String,
        playlistDir: String,
        pathToId: Map<String, String>,
        nameToIds: Map<String, List<String>>,
    ): String? {
        var raw = entry.trim()
        if (raw.startsWith("file:", ignoreCase = true)) {
            raw = raw.substringAfter(':').trimStart('/')
            raw = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
            raw = "/$raw"
        }
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) return null
        val slashed = raw.replace('\\', '/')
        val absolute = slashed.startsWith("/") || DRIVE_PREFIX.containsMatchIn(slashed.lowercase())

        if (!absolute) {
            val joined = if (playlistDir.isEmpty()) slashed else "$playlistDir/$slashed"
            pathToId[normalize(joined)]?.let { return it }
        }
        // Absolute (or unresolved relative) path: match the longest suffix that exists in the tree.
        val segments = normalize(if (absolute) slashed.substringAfter(':') else slashed).split('/')
        for (i in segments.indices) {
            pathToId[segments.drop(i).joinToString("/")]?.let { return it }
        }
        val fileName = segments.lastOrNull().orEmpty()
        return nameToIds[fileName]?.singleOrNull()
    }

    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase()
}

private const val BOM_CODE = 0xFEFF
