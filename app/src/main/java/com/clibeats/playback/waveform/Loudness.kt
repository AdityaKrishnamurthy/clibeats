package com.clibeats.playback.waveform

import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The waveform behind Now Playing's seek bar: [BAR_COUNT] bars across the song, each the loudness of its
 * slice of audio. Loudness is RMS on a decibel scale, so quiet passages still show and loud ones don't all
 * clip to the top. Bars that haven't been measured yet are NaN.
 */
object Loudness {
    const val BAR_COUNT = 64

    /** Quieter than this (dBFS) draws as the smallest bar. */
    private const val FLOOR_DB = -45f
    private const val SILENCE_RMS = 1e-5
    private const val DB_PER_DECADE = 20f

    /** Bars never draw shorter than this (relative to the loudest), so silence still shows a dot. */
    const val MIN_BAR = 0.08f

    /** A quiet song isn't stretched to fill the height: bars are scaled to at least this loudest level. */
    private const val MIN_SCALE_LEVEL = 0.55f

    private const val PCM16_FULL_SCALE = 32768.0
    private const val PCM24_FULL_SCALE = 8388608.0
    private const val PCM32_FULL_SCALE = 2147483648.0
    private const val PCM24_BITS = 24

    /** RMS (0..1 of full scale) to a 0..1 level on the decibel scale. */
    fun rmsToLevel(rms: Double): Float {
        val db = DB_PER_DECADE * log10(max(rms, SILENCE_RMS)).toFloat()
        return ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
    }

    /** Bar a moment of the song falls into. */
    fun barFor(
        positionUs: Long,
        durationUs: Long,
    ): Int =
        if (durationUs <= 0) {
            0
        } else {
            (positionUs.toDouble() / durationUs * BAR_COUNT).toInt().coerceIn(0, BAR_COUNT - 1)
        }

    /**
     * Heights to draw (0..1) from measured levels: relative to the loudest measured bar, never below
     * [MIN_BAR]. Unmeasured (NaN) bars stay NaN so the caller can draw a placeholder there.
     */
    fun normalize(levels: FloatArray): FloatArray {
        val loudest = levels.filterNot { it.isNaN() }.maxOrNull() ?: return levels.copyOf()
        val scale = max(loudest, MIN_SCALE_LEVEL)
        return FloatArray(levels.size) { i ->
            val level = levels[i]
            if (level.isNaN()) Float.NaN else (level / scale).coerceIn(MIN_BAR, 1f)
        }
    }

    /**
     * Sum of squared samples (each -1..1) and the number of samples read from interleaved PCM in
     * [buffer] between [start] and [end] (bytes), little-endian as Android delivers it. Every [stride]-th
     * sample is read, which is plenty for loudness. Returns null for an encoding it doesn't read.
     */
    @Suppress("CyclomaticComplexMethod")
    fun sumSquares(
        buffer: ByteBuffer,
        start: Int,
        end: Int,
        encoding: PcmEncoding,
        stride: Int = 1,
    ): Pair<Double, Long> {
        val bytes = encoding.bytesPerSample
        var sum = 0.0
        var count = 0L
        var i = start
        val step = bytes * stride
        while (i + bytes <= end) {
            val sample =
                when (encoding) {
                    PcmEncoding.PCM_16 -> littleEndian(buffer, i, bytes).toShort() / PCM16_FULL_SCALE
                    PcmEncoding.PCM_24 -> signExtend(littleEndian(buffer, i, bytes), PCM24_BITS) / PCM24_FULL_SCALE
                    PcmEncoding.PCM_32 -> littleEndian(buffer, i, bytes) / PCM32_FULL_SCALE
                    PcmEncoding.FLOAT -> java.lang.Float.intBitsToFloat(littleEndian(buffer, i, bytes)).toDouble()
                }
            sum += sample * sample
            count++
            i += step
        }
        return sum to count
    }

    fun rms(
        sumSquares: Double,
        count: Long,
    ): Double = if (count <= 0) 0.0 else sqrt(sumSquares / count)

    private fun littleEndian(
        buffer: ByteBuffer,
        index: Int,
        bytes: Int,
    ): Int {
        var value = 0
        for (b in 0 until bytes) value = value or ((buffer.get(index + b).toInt() and BYTE_MASK) shl (BITS * b))
        return value
    }

    private fun signExtend(
        value: Int,
        bits: Int,
    ): Int = (value shl (Int.SIZE_BITS - bits)) shr (Int.SIZE_BITS - bits)

    private const val BYTE_MASK = 0xFF
    private const val BITS = 8
}

/** PCM sample layouts the waveform can read. */
@Suppress("MagicNumber") // bytes per sample
enum class PcmEncoding(
    val bytesPerSample: Int,
) {
    PCM_16(2),
    PCM_24(3),
    PCM_32(4),
    FLOAT(4),
}

/** Collects squared samples per bar; [levels] gives each bar's loudness (NaN where nothing was heard). */
class WaveformAccumulator(
    private val bars: Int = Loudness.BAR_COUNT,
) {
    private val sums = DoubleArray(bars)
    private val counts = LongArray(bars)

    fun add(
        bar: Int,
        sumSquares: Double,
        count: Long,
    ) {
        if (bar !in 0 until bars || count <= 0) return
        sums[bar] += sumSquares
        counts[bar] += count
    }

    fun hasAny(): Boolean = counts.any { it > 0 }

    fun levels(): FloatArray =
        FloatArray(bars) { i ->
            if (counts[i] == 0L) Float.NaN else Loudness.rmsToLevel(Loudness.rms(sums[i], counts[i]))
        }
}
