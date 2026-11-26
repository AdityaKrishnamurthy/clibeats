@file:Suppress(
    "MagicNumber",
    "TooManyFunctions",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    // Binary tag formats: a branch per field is the clearest way to read them.
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
)

package com.clibeats.data.lyrics

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

/** Random access to a file's bytes (a FileChannel on the phone, a byte array in tests). */
interface ByteSource {
    val size: Long

    /** Up to [length] bytes from [position] (fewer at the end of the file). */
    fun read(
        position: Long,
        length: Int,
    ): ByteArray
}

class ByteArraySource(
    private val bytes: ByteArray,
) : ByteSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(
        position: Long,
        length: Int,
    ): ByteArray {
        if (position >= bytes.size || position < 0) return ByteArray(0)
        val end = minOf(bytes.size.toLong(), position + length).toInt()
        return bytes.copyOfRange(position.toInt(), end)
    }
}

class ChannelSource(
    private val channel: FileChannel,
) : ByteSource {
    override val size: Long get() = channel.size()

    override fun read(
        position: Long,
        length: Int,
    ): ByteArray {
        if (position >= size || position < 0 || length <= 0) return ByteArray(0)
        val buffer = ByteBuffer.allocate(minOf(length.toLong(), size - position).toInt())
        var at = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, at)
            if (n <= 0) break
            at += n
        }
        return buffer.array().copyOf(buffer.position())
    }
}

/**
 * Finds the lyrics stored in an audio file's tags, without reading the audio:
 * - FLAC and Ogg (Opus / Vorbis): Vorbis comments `LYRICS`, `UNSYNCEDLYRICS` (and other `…LYRICS` fields).
 * - MP3 (ID3v2.2–2.4): `USLT` frames and `TXXX` frames whose name contains "lyrics".
 * - M4A / MP4: the `©lyr` atom and iTunes freeform `----` atoms named "…LYRICS".
 *
 * Returns every candidate text found, in the file's order; the caller picks the best.
 */
object TagLyricsReader {
    fun read(source: ByteSource): List<String> {
        val head = source.read(0, 12)
        return runCatching {
            when {
                head.startsWith("fLaC") -> flac(source, 0)
                head.startsWith("ID3") -> {
                    // An ID3 tag in front: MP3, or (rarely) FLAC with an ID3 header.
                    val id3Size = 10 + syncSafe(head, 6)
                    val inner = source.read(id3Size.toLong(), 4)
                    id3(source) + if (inner.startsWith("fLaC")) flac(source, id3Size.toLong()) else emptyList()
                }
                head.startsWith("OggS") -> ogg(source)
                head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4(source)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    // ── FLAC ──

    private fun flac(
        source: ByteSource,
        start: Long,
    ): List<String> {
        var at = start + 4
        while (at + 4 <= source.size) {
            val header = source.read(at, 4)
            if (header.size < 4) break
            val last = header[0].toInt() and 0x80 != 0
            val type = header[0].toInt() and 0x7F
            val length = u24(header, 1)
            if (type == FLAC_VORBIS_COMMENT) return vorbisComments(source.read(at + 4, length))
            at += 4 + length
            if (last) break
        }
        return emptyList()
    }

    // ── Ogg (Opus / Vorbis): the comment packet is the second one ──

    private fun ogg(source: ByteSource): List<String> {
        val packets = mutableListOf<ByteArray>()
        var current = java.io.ByteArrayOutputStream()
        var at = 0L
        while (packets.size < 2 && at + 27 <= source.size && at < MAX_OGG_HEADER_BYTES) {
            val page = source.read(at, 27)
            if (!page.startsWith("OggS")) break
            val segments = page[26].toInt() and 0xFF
            val table = source.read(at + 27, segments)
            var dataAt = at + 27 + segments
            for (lacing in table) {
                val len = lacing.toInt() and 0xFF
                current.write(source.read(dataAt, len))
                dataAt += len
                if (len < 255) {
                    packets += current.toByteArray()
                    current = java.io.ByteArrayOutputStream()
                    if (packets.size == 2) break
                }
            }
            at = at + 27 + segments + table.sumOf { it.toInt() and 0xFF }
        }
        val comments = packets.getOrNull(1) ?: return emptyList()
        val body =
            when {
                comments.startsWith("OpusTags") -> comments.copyOfRange(8, comments.size)
                isVorbisComment(comments) ->
                    comments.copyOfRange(7, comments.size)
                else -> return emptyList()
            }
        return vorbisComments(body)
    }

    /** A Vorbis comment block: vendor string, then `NAME=value` fields (lengths little-endian). */
    private fun vorbisComments(data: ByteArray): List<String> {
        val buffer = ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        if (buffer.remaining() < 8) return emptyList()
        val vendor = buffer.int
        if (vendor < 0 || vendor > buffer.remaining()) return emptyList()
        buffer.position(buffer.position() + vendor)
        if (buffer.remaining() < 4) return emptyList()
        val count = buffer.int
        val found = mutableListOf<Pair<String, String>>()
        repeat(count.coerceIn(0, MAX_FIELDS)) {
            if (buffer.remaining() < 4) return@repeat
            val len = buffer.int
            if (len < 0 || len > buffer.remaining()) return@repeat
            val field = ByteArray(len).also(buffer::get).toString(Charsets.UTF_8)
            val eq = field.indexOf('=')
            if (eq > 0) found += field.substring(0, eq).uppercase() to field.substring(eq + 1)
        }
        return found
            .filter { (name, _) -> name.contains("LYRICS") }
            .sortedBy { lyricsFieldRank(it.first) }
            .map { it.second }
    }

    /** LYRICS first, then UNSYNCEDLYRICS, then anything else named …LYRICS. */
    private fun lyricsFieldRank(name: String): Int =
        when (name) {
            "LYRICS" -> 0
            "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> 1
            else -> 2
        }

    // ── ID3v2 (MP3) ──

    private fun id3(source: ByteSource): List<String> {
        val header = source.read(0, 10)
        val major = header[3].toInt()
        val flags = header[5].toInt()
        val size = syncSafe(header, 6)
        var tag = source.read(10, size)
        if (flags and 0x80 != 0 && major < 4) tag = removeUnsync(tag)
        var at = 0
        if (flags and 0x40 != 0 && major >= 3) {
            // Extended header: skip it.
            at += if (major == 4) syncSafe(tag, 0) else 4 + u32(tag, 0)
        }
        val idLength = if (major == 2) 3 else 4
        val headerLength = if (major == 2) 6 else 10
        val found = mutableListOf<String>()
        while (at + headerLength <= tag.size) {
            val id = String(tag, at, idLength, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val frameSize =
                when (major) {
                    2 -> u24(tag, at + 3)
                    3 -> u32(tag, at + 4)
                    else -> syncSafe(tag, at + 4)
                }
            if (frameSize <= 0 || at + headerLength + frameSize > tag.size) break
            val frame = tag.copyOfRange(at + headerLength, at + headerLength + frameSize)
            when (id) {
                "USLT", "ULT" -> uslt(frame)?.let(found::add)
                "TXXX", "TXX" -> txxx(frame)?.let(found::add)
            }
            at += headerLength + frameSize
        }
        return found
    }

    /** USLT: encoding, language (3), description, text. */
    private fun uslt(frame: ByteArray): String? {
        if (frame.size < 5) return null
        val charset = id3Charset(frame[0].toInt())
        val afterDescription = skipTerminated(frame, 4, frame[0].toInt())
        return decode(frame, afterDescription, frame.size, charset).takeIf { it.isNotBlank() }
    }

    /** TXXX: encoding, description, value; only lyrics ones. */
    private fun txxx(frame: ByteArray): String? {
        if (frame.size < 2) return null
        val encoding = frame[0].toInt()
        val charset = id3Charset(encoding)
        val descriptionEnd = skipTerminated(frame, 1, encoding)
        val terminator = if (encoding == 1 || encoding == 2) 2 else 1
        val description = decode(frame, 1, (descriptionEnd - terminator).coerceAtLeast(1), charset)
        if (!description.contains("lyrics", ignoreCase = true)) return null
        return decode(frame, descriptionEnd, frame.size, charset).takeIf { it.isNotBlank() }
    }

    private fun id3Charset(encoding: Int): Charset =
        when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }

    /** Position just after a null-terminated string starting at [from] (two zero bytes for UTF-16). */
    private fun skipTerminated(
        data: ByteArray,
        from: Int,
        encoding: Int,
    ): Int {
        if (encoding == 1 || encoding == 2) {
            var i = from
            while (i + 1 < data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return i + 2
                i += 2
            }
            return data.size
        }
        var i = from
        while (i < data.size) {
            if (data[i].toInt() == 0) return i + 1
            i++
        }
        return data.size
    }

    private fun decode(
        data: ByteArray,
        from: Int,
        to: Int,
        charset: Charset,
    ): String {
        if (from >= to || from >= data.size) return ""
        return String(data, from, minOf(to, data.size) - from, charset).trimEnd('\u0000')
    }

    private fun removeUnsync(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            out.write(data[i].toInt())
            if (data[i].toInt() and 0xFF == 0xFF && i + 1 < data.size && data[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    // ── MP4 / M4A: moov › udta › meta › ilst › ©lyr / ---- ──

    private fun mp4(source: ByteSource): List<String> {
        val moov = findBox(source, 0, source.size, "moov") ?: return emptyList()
        val udta = findBox(source, moov.first, moov.second, "udta") ?: return emptyList()
        val meta = findBox(source, udta.first, udta.second, "meta") ?: return emptyList()
        // "meta" is a full box: 4 bytes of version and flags before its children.
        val ilst = findBox(source, meta.first + 4, meta.second, "ilst") ?: return emptyList()
        val found = mutableListOf<String>()
        forEachBox(source, ilst.first, ilst.second) { type, start, end ->
            when (type) {
                "©lyr" -> dataText(source, start, end)?.let(found::add)
                "----" -> {
                    val name = findBox(source, start, end, "name")?.let { (s, e) -> text(source, s + 4, e) }
                    if (name != null && name.contains("LYRICS", ignoreCase = true)) {
                        dataText(source, start, end)?.let(found::add)
                    }
                }
            }
        }
        return found
    }

    /** The text in an item's `data` box (8 bytes of type and locale first). */
    private fun dataText(
        source: ByteSource,
        start: Long,
        end: Long,
    ): String? {
        val (s, e) = findBox(source, start, end, "data") ?: return null
        return text(source, s + 8, e)?.takeIf { it.isNotBlank() }
    }

    private fun text(
        source: ByteSource,
        start: Long,
        end: Long,
    ): String? {
        val length = end - start
        if (length <= 0 || length > MAX_TEXT_BYTES) return null
        return source.read(start, length.toInt()).toString(Charsets.UTF_8)
    }

    /** The content range (after the header) of the first box of [type] between [start] and [end]. */
    private fun findBox(
        source: ByteSource,
        start: Long,
        end: Long,
        type: String,
    ): Pair<Long, Long>? {
        var found: Pair<Long, Long>? = null
        forEachBox(source, start, end) { boxType, s, e ->
            if (found == null && boxType == type) found = s to e
        }
        return found
    }

    private fun forEachBox(
        source: ByteSource,
        start: Long,
        end: Long,
        visit: (type: String, contentStart: Long, contentEnd: Long) -> Unit,
    ) {
        var at = start
        var count = 0
        while (at + 8 <= end && count++ < MAX_BOXES) {
            val header = source.read(at, 16)
            if (header.size < 8) return
            var size = u32(header, 0).toLong() and 0xFFFFFFFFL
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            when (size) {
                1L -> {
                    if (header.size < 16) return
                    size = ByteBuffer.wrap(header, 8, 8).long
                    headerSize = 16
                }
                0L -> size = end - at
            }
            if (size < headerSize || at + size > end) return
            visit(type, at + headerSize, at + size)
            at += size
        }
    }

    // ── bytes ──

    /** A Vorbis comment header packet: type 3, then "vorbis". */
    private fun isVorbisComment(packet: ByteArray): Boolean =
        packet.size > 7 && packet[0].toInt() == 3 && String(packet, 1, 6, Charsets.ISO_8859_1) == "vorbis"

    private fun ByteArray.startsWith(magic: String): Boolean =
        size >= magic.length && magic.indices.all { this[it] == magic[it].code.toByte() }

    private fun u24(
        data: ByteArray,
        at: Int,
    ): Int =
        (data[at].toInt() and 0xFF shl 16) or (data[at + 1].toInt() and 0xFF shl 8) or
            (data[at + 2].toInt() and 0xFF)

    private fun u32(
        data: ByteArray,
        at: Int,
    ): Int =
        (data[at].toInt() and 0xFF shl 24) or (data[at + 1].toInt() and 0xFF shl 16) or
            (data[at + 2].toInt() and 0xFF shl 8) or (data[at + 3].toInt() and 0xFF)

    private fun syncSafe(
        data: ByteArray,
        at: Int,
    ): Int =
        (data[at].toInt() and 0x7F shl 21) or (data[at + 1].toInt() and 0x7F shl 14) or
            (data[at + 2].toInt() and 0x7F shl 7) or (data[at + 3].toInt() and 0x7F)

    private const val FLAC_VORBIS_COMMENT = 4
    private const val MAX_FIELDS = 10_000
    private const val MAX_BOXES = 10_000
    private const val MAX_TEXT_BYTES = 4L * 1024 * 1024

    /** Covers embedded in the comment packet can be big; give up past this. */
    private const val MAX_OGG_HEADER_BYTES = 32L * 1024 * 1024
}
