package com.clibeats.data.importer

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Opens what the user picked: a single Exportify `.csv`, or the `.zip` from "Export All" holding any
 * number of them. Zip is detected from the file's first bytes, not its name, because some file
 * pickers hide or change extensions.
 */
object ExportifyFiles {
    /** A playlist file bigger than this isn't an Exportify export (10k songs is about 5 MB). */
    private const val MAX_FILE_BYTES = 20L * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 1_000
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /**
     * Reads every playlist in [input]. [fileName] names a single-CSV playlist. Empty playlists (like
     * Exportify's Saved Albums) are skipped; inside a zip, files that aren't Exportify CSVs are skipped.
     *
     * @throws NotAnExportifyFileException when nothing importable is found.
     */
    fun read(
        input: InputStream,
        fileName: String,
    ): List<SpotifyPlaylist> {
        val buffered = input.buffered()
        buffered.mark(ZIP_MAGIC.size)
        val head = ByteArray(ZIP_MAGIC.size)
        val headLength = buffered.read(head)
        buffered.reset()
        val playlists =
            if (headLength == ZIP_MAGIC.size && head.contentEquals(ZIP_MAGIC)) {
                readZip(buffered)
            } else {
                listOf(ExportifyCsv.parse(ExportifyCsv.playlistName(fileName), readText(buffered)))
            }
        val nonEmpty = playlists.filter { it.songs.isNotEmpty() }
        if (nonEmpty.isEmpty()) {
            throw NotAnExportifyFileException(
                if (playlists.isEmpty()) {
                    "No Exportify playlist files were found in \"$fileName\"."
                } else {
                    "The playlists in \"$fileName\" have no songs."
                },
            )
        }
        return nonEmpty
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun readZip(input: InputStream): List<SpotifyPlaylist> {
        val playlists = mutableListOf<SpotifyPlaylist>()
        ZipInputStream(input).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ZIP_ENTRIES) break
                val path = entry.name
                val base = path.substringAfterLast('/')
                // macOS adds __MACOSX/._name copies; they aren't real files.
                val skip =
                    entry.isDirectory || !base.endsWith(".csv", ignoreCase = true) ||
                        base.startsWith("._") || path.startsWith("__MACOSX")
                if (!skip) {
                    runCatching { ExportifyCsv.parse(ExportifyCsv.playlistName(base), readText(zip)) }
                        .onSuccess { playlists += it }
                }
                zip.closeEntry()
            }
        }
        return playlists.sortedBy { it.name.lowercase() }
    }

    private fun readText(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_FILE_BYTES) throw NotAnExportifyFileException("This file is too big to be a playlist.")
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
