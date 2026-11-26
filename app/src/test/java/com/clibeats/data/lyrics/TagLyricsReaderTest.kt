package com.clibeats.data.lyrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds small files in each format with the tags laid out as real taggers write them. */
class TagLyricsReaderTest {
    private val lrc = "[00:01.00]<00:01.00>Hello <00:01.50>world <00:02.00>"

    private fun le32(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun be32(value: Int) = ByteBuffer.allocate(4).putInt(value).array()

    private fun vorbisComment(vararg fields: String): ByteArray {
        val out = ByteArrayOutputStream()
        val vendor = "reference libFLAC".toByteArray()
        out.write(le32(vendor.size))
        out.write(vendor)
        out.write(le32(fields.size))
        fields.forEach {
            val bytes = it.toByteArray(Charsets.UTF_8)
            out.write(le32(bytes.size))
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun flacBlock(
        type: Int,
        body: ByteArray,
        last: Boolean,
    ): ByteArray {
        val header =
            byteArrayOf(
                ((if (last) 0x80 else 0) or type).toByte(),
                (body.size shr 16).toByte(),
                (body.size shr 8).toByte(),
                body.size.toByte(),
            )
        return header + body
    }

    @Test
    fun `flac - LYRICS after a big picture block, UNSYNCEDLYRICS second`() {
        val file =
            "fLaC".toByteArray() +
                flacBlock(0, ByteArray(34), last = false) +
                flacBlock(6, ByteArray(200_000), last = false) +
                flacBlock(4, vorbisComment("TITLE=Zoo", "UNSYNCEDLYRICS=plain words", "LYRICS=$lrc"), last = true)

        assertThat(TagLyricsReader.read(ByteArraySource(file))).containsExactly(lrc, "plain words").inOrder()
    }

    @Test
    fun `ogg opus - comment packet spanning two pages`() {
        val id = "OpusHead".toByteArray() + ByteArray(11)
        val tags = "OpusTags".toByteArray() + vorbisComment("ARTIST=Vijay Prakash", "LYRICS=${"x".repeat(800)}$lrc")
        // Page 1: the id packet. Pages 2-3: the comments, split across pages with 255-byte lacing.
        val firstPart = tags.copyOfRange(0, 255 * 2)
        val rest = tags.copyOfRange(255 * 2, tags.size)
        val file =
            oggPage(listOf(id.size), id) +
                oggPage(listOf(255, 255), firstPart) +
                oggPage(lacingFor(rest.size), rest)

        assertThat(TagLyricsReader.read(ByteArraySource(file))).containsExactly("${"x".repeat(800)}$lrc")
    }

    /** Ogg lacing for a packet's last [length] bytes: 255s, then the remainder (ending the packet). */
    private fun lacingFor(length: Int): List<Int> = List(length / 255) { 255 } + (length % 255)

    private fun oggPage(
        lacing: List<Int>,
        data: ByteArray,
    ): ByteArray {
        val header = ByteArray(27)
        "OggS".toByteArray().copyInto(header)
        header[26] = lacing.size.toByte()
        return header + lacing.map { it.toByte() }.toByteArray() + data
    }

    @Test
    fun `mp3 - ID3v2_3 USLT in UTF-16 and a lyrics TXXX`() {
        val text = "Line one\nLine two"
        val uslt =
            byteArrayOf(1) + "eng".toByteArray() + byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0) +
                text.toByteArray(Charsets.UTF_16LE).let { byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + it }
        val txxx = byteArrayOf(3) + "LYRICS".toByteArray() + byteArrayOf(0) + lrc.toByteArray()
        val other = byteArrayOf(3) + "Title".toByteArray()
        val frames = id3Frame("TIT2", other) + id3Frame("USLT", uslt) + id3Frame("TXXX", txxx)
        val file = id3Header(frames.size + 20) + frames + ByteArray(20) + byteArrayOf(0xFF.toByte(), 0xFB.toByte())

        assertThat(TagLyricsReader.read(ByteArraySource(file))).containsExactly(text, lrc).inOrder()
    }

    private fun id3Frame(
        id: String,
        body: ByteArray,
    ) = id.toByteArray() + be32(body.size) + byteArrayOf(0, 0) + body

    private fun id3Header(size: Int): ByteArray {
        val syncSafe =
            byteArrayOf(
                (size shr 21 and 0x7F).toByte(),
                (size shr 14 and 0x7F).toByte(),
                (size shr 7 and 0x7F).toByte(),
                (size and 0x7F).toByte(),
            )
        return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + syncSafe
    }

    @Test
    fun `m4a - ©lyr inside moov udta meta ilst, after the audio`() {
        val data = box("data", be32(1) + be32(0) + "Kanmaniye kayyahiyiye\nKanyamaniye".toByteArray())
        val ilst = box("ilst", box("©nam", box("data", be32(1) + be32(0) + "Title".toByteArray())) + box("©lyr", data))
        val meta = box("meta", ByteArray(4) + box("hdlr", ByteArray(25)) + ilst)
        val moov = box("moov", box("mvhd", ByteArray(100)) + box("udta", meta))
        val file = box("ftyp", "M4A ".toByteArray() + ByteArray(8)) + box("mdat", ByteArray(5_000)) + moov

        assertThat(TagLyricsReader.read(ByteArraySource(file))).containsExactly("Kanmaniye kayyahiyiye\nKanyamaniye")
    }

    private fun box(
        type: String,
        body: ByteArray,
    ) = be32(body.size + 8) + type.toByteArray(Charsets.ISO_8859_1) + body

    @Test
    fun `unknown or broken files give nothing`() {
        assertThat(TagLyricsReader.read(ByteArraySource(ByteArray(0)))).isEmpty()
        assertThat(TagLyricsReader.read(ByteArraySource("RIFF....WAVE".toByteArray()))).isEmpty()
        assertThat(TagLyricsReader.read(ByteArraySource("fLaC".toByteArray() + byteArrayOf(4, 0x7F, 0, 0)))).isEmpty()
    }
}
