package com.clibeats.playback.waveform

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class WaveformTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun pcm16Sine(
        amplitude: Double,
        samples: Int = 4410,
    ): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(samples) { i ->
            val value = amplitude * Short.MAX_VALUE * sin(2 * PI * 440 * i / 44100)
            buffer.putShort(value.toInt().toShort())
        }
        buffer.flip()
        return buffer
    }

    @Test
    fun `louder audio gives a higher level, silence the lowest`() {
        val quiet = Loudness.sumSquares(pcm16Sine(0.05), 0, 8820, PcmEncoding.PCM_16)
        val loud = Loudness.sumSquares(pcm16Sine(0.8), 0, 8820, PcmEncoding.PCM_16)
        val quietLevel = Loudness.rmsToLevel(Loudness.rms(quiet.first, quiet.second))
        val loudLevel = Loudness.rmsToLevel(Loudness.rms(loud.first, loud.second))

        assertThat(loudLevel).isGreaterThan(quietLevel)
        assertThat(Loudness.rmsToLevel(0.0)).isEqualTo(0f)
        assertThat(Loudness.rmsToLevel(1.0)).isEqualTo(1f)
    }

    @Test
    fun `full-scale sine has an rms near 0_707`() {
        val (sum, count) = Loudness.sumSquares(pcm16Sine(1.0), 0, 8820, PcmEncoding.PCM_16)
        assertThat(Loudness.rms(sum, count)).isWithin(0.01).of(0.707)
    }

    @Test
    fun `float and 24-bit pcm are read`() {
        val float = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(0.5f).putFloat(-0.5f)
        float.flip()
        val (fSum, fCount) = Loudness.sumSquares(float, 0, 8, PcmEncoding.FLOAT)
        assertThat(Loudness.rms(fSum, fCount)).isWithin(1e-6).of(0.5)

        // -4194304 = -0.5 of 24-bit full scale, little-endian 3 bytes: 00 00 C0
        val pcm24 = ByteBuffer.wrap(byteArrayOf(0, 0, 0xC0.toByte()))
        val (sum24, count24) = Loudness.sumSquares(pcm24, 0, 3, PcmEncoding.PCM_24)
        assertThat(Loudness.rms(sum24, count24)).isWithin(1e-6).of(0.5)
    }

    @Test
    fun `positions map onto the 64 bars`() {
        assertThat(Loudness.barFor(0, 1_000_000)).isEqualTo(0)
        assertThat(Loudness.barFor(500_000, 1_000_000)).isEqualTo(32)
        assertThat(Loudness.barFor(2_000_000, 1_000_000)).isEqualTo(Loudness.BAR_COUNT - 1)
        assertThat(Loudness.barFor(10, 0)).isEqualTo(0)
    }

    @Test
    fun `normalize scales to the loudest bar and keeps unknown bars unknown`() {
        val heights = Loudness.normalize(floatArrayOf(0.8f, 0.4f, Float.NaN, 0f))

        assertThat(heights[0]).isEqualTo(1f)
        assertThat(heights[1]).isWithin(1e-6f).of(0.5f)
        assertThat(heights[2].isNaN()).isTrue()
        assertThat(heights[3]).isEqualTo(Loudness.MIN_BAR)
    }

    @Test
    fun `a quiet song is not stretched to full height`() {
        val heights = Loudness.normalize(floatArrayOf(0.2f, 0.1f))
        assertThat(heights[0]).isLessThan(0.5f)
    }

    @Test
    fun `live ticks fill the playing bar and skip seeks and pauses`() {
        val tracker = LiveLoudnessTracker()
        val duration = 64_000L // one bar per second

        assertThat(tracker.onTick(10_000, duration, true, 1.0, 10)).isFalse() // first tick: nothing to place yet
        assertThat(tracker.onTick(10_500, duration, true, 1.0, 10)).isTrue()
        assertThat(tracker.onTick(40_000, duration, true, 1.0, 10)).isFalse() // a seek
        assertThat(tracker.onTick(40_500, duration, true, 1.0, 10)).isTrue()
        assertThat(tracker.onTick(41_000, duration, false, 1.0, 10)).isFalse() // paused

        val levels = tracker.bars.levels()
        assertThat(levels[10].isNaN()).isFalse()
        assertThat(levels[40].isNaN()).isFalse()
        assertThat(levels.count { !it.isNaN() }).isEqualTo(2)
    }

    @Test
    fun `saved waveforms round trip`() {
        val store = WaveformStore(temp.newFolder("waveforms"))
        val levels = FloatArray(Loudness.BAR_COUNT) { if (it % 2 == 0) it / 64f else Float.NaN }

        store.save("youtube_music:abc", levels, complete = false)
        val loaded = store.load("youtube_music:abc")

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.complete).isFalse()
        assertThat(loaded.levels[2]).isEqualTo(levels[2])
        assertThat(loaded.levels[1].isNaN()).isTrue()
        assertThat(store.load("other")).isNull()
    }

    @Test
    fun `nothing measured is not saved`() {
        val store = WaveformStore(temp.newFolder("empty"))
        store.save("id", FloatArray(Loudness.BAR_COUNT) { Float.NaN }, complete = false)
        assertThat(store.load("id")).isNull()
    }

    @Test
    fun `the tap passes audio through unchanged and measures it`() {
        val tap = LoudnessTap()
        tap.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
        tap.flush()
        val input = pcm16Sine(0.5)
        val copy = input.duplicate()

        tap.queueInput(input)
        val output = tap.output

        assertThat(output).isEqualTo(copy)
        val (sum, count) = tap.drain()
        assertThat(count).isGreaterThan(0L)
        assertThat(Loudness.rms(sum, count)).isWithin(0.02).of(0.354)
        assertThat(tap.drain().second).isEqualTo(0L) // drained
    }

    @Test
    fun `the tap stays out of the way for formats it does not measure`() {
        val tap = LoudnessTap()
        tap.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT))
        assertThat(tap.isActive).isFalse()
    }
}
