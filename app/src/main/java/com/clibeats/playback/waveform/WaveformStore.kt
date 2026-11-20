package com.clibeats.playback.waveform

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

/**
 * Saved waveforms, one tiny file per song (64 floats), so a song is decoded or listened to only once.
 * Kept in no_backup: it isn't worth backup space and is rebuilt on demand.
 */
class WaveformStore(
    private val dir: File,
) {
    data class Saved(
        val levels: FloatArray,
        val complete: Boolean,
    )

    fun load(trackId: String): Saved? =
        runCatching {
            val file = fileFor(trackId).takeIf { it.isFile } ?: return null
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readByte().toInt() != VERSION) return null
                val complete = input.readBoolean()
                val count = input.readUnsignedByte()
                if (count != Loudness.BAR_COUNT) return null
                Saved(FloatArray(count) { input.readFloat() }, complete)
            }
        }.getOrNull()

    fun save(
        trackId: String,
        levels: FloatArray,
        complete: Boolean,
    ) {
        if (levels.size != Loudness.BAR_COUNT || levels.all { it.isNaN() }) return
        runCatching {
            dir.mkdirs()
            val target = fileFor(trackId)
            val temp = File(dir, target.name + ".tmp")
            DataOutputStream(temp.outputStream().buffered()).use { out ->
                out.writeByte(VERSION)
                out.writeBoolean(complete)
                out.writeByte(levels.size)
                levels.forEach { out.writeFloat(it) }
            }
            temp.renameTo(target) || run { target.delete() && temp.renameTo(target) }
        }
    }

    private fun fileFor(trackId: String): File {
        val name = UUID.nameUUIDFromBytes(trackId.toByteArray()).toString()
        return File(dir, "$name.wf")
    }

    private companion object {
        const val VERSION = 1
    }
}
