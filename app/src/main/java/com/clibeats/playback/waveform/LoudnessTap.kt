package com.clibeats.playback.waveform

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pass-through step in ExoPlayer's audio output that measures how loud the audio is. It doesn't change the
 * sound. [WaveformRepository] drains it every position tick and files the loudness under the bar that's
 * playing, so streamed songs (which can't be decoded twice) still get a waveform from their real audio.
 *
 * Only 16-bit PCM is measured (what ExoPlayer outputs unless float output is on); for anything else the
 * step turns itself off and audio flows through untouched.
 */
@OptIn(UnstableApi::class)
@Singleton
class LoudnessTap
    @Inject
    constructor() : BaseAudioProcessor() {
        private val lock = Any()
        private var sumSquares = 0.0
        private var samples = 0L

        override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
            if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioFormat.NOT_SET

        override fun queueInput(inputBuffer: ByteBuffer) {
            val start = inputBuffer.position()
            val end = inputBuffer.limit()
            if (end <= start) return
            val (sum, count) = Loudness.sumSquares(inputBuffer, start, end, PcmEncoding.PCM_16, stride = STRIDE)
            synchronized(lock) {
                sumSquares += sum
                samples += count
            }
            replaceOutputBuffer(end - start).put(inputBuffer).flip()
        }

        /** Loudness measured since the last call (sum of squares, sample count), then starts over. */
        fun drain(): Pair<Double, Long> =
            synchronized(lock) {
                val result = sumSquares to samples
                sumSquares = 0.0
                samples = 0L
                result
            }

        // A seek or a new song: what was measured belongs to the old position.
        override fun onFlush() {
            drain()
        }

        override fun onReset() {
            drain()
        }

        private companion object {
            /** Every 3rd sample (odd, so both stereo channels are read) keeps the audio thread light. */
            const val STRIDE = 3
        }
    }
