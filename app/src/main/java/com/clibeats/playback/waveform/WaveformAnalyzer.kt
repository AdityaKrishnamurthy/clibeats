@file:Suppress("TooGenericExceptionCaught")

package com.clibeats.playback.waveform

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes a whole audio file (a local song, or a fully downloaded one) with Android's own decoders and
 * measures each bar's loudness. Emits the bars measured so far every few bars, so the waveform fills in
 * while it works, then the complete waveform last. Emits nothing if the file can't be decoded.
 */
@Singleton
class WaveformAnalyzer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        sealed interface Source {
            data class ContentUri(
                val uri: Uri,
            ) : Source

            data class LocalFile(
                val file: File,
            ) : Source
        }

        /** Emits [Progress] while decoding; the last one has `complete = true`. */
        data class Progress(
            val levels: FloatArray,
            val complete: Boolean,
        )

        @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
        fun analyze(
            source: Source,
            durationHintMs: Long,
        ): Flow<Progress> =
            flow {
                val extractor = MediaExtractor()
                var codec: MediaCodec? = null
                try {
                    when (source) {
                        is Source.ContentUri -> extractor.setDataSource(context, source.uri, null)
                        is Source.LocalFile -> extractor.setDataSource(source.file.absolutePath)
                    }
                    val track =
                        (0 until extractor.trackCount).firstOrNull { i ->
                            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                        } ?: return@flow
                    extractor.selectTrack(track)
                    val format = extractor.getTrackFormat(track)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: return@flow
                    val durationUs =
                        format.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION)
                            ?.takeIf { it > 0 }
                            ?: (durationHintMs * US_PER_MS)
                    if (durationUs <= 0) return@flow

                    val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
                    decoder.configure(format, null, null, 0)
                    decoder.start()
                    val info = MediaCodec.BufferInfo()
                    val bars = WaveformAccumulator()
                    var encoding = PcmEncoding.PCM_16
                    var inputDone = false
                    var outputDone = false
                    var lastEmittedBar = -1
                    while (!outputDone) {
                        currentCoroutineContext().ensureActive()
                        if (!inputDone) {
                            val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                            if (inIndex >= 0) {
                                val buffer = decoder.getInputBuffer(inIndex)
                                val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                                if (size < 0) {
                                    decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                        when {
                            outIndex >= 0 -> {
                                val out = decoder.getOutputBuffer(outIndex)
                                if (out != null && info.size > 0) {
                                    val (sum, count) =
                                        Loudness.sumSquares(out, info.offset, info.offset + info.size, encoding, STRIDE)
                                    val bar = Loudness.barFor(info.presentationTimeUs, durationUs)
                                    bars.add(bar, sum, count)
                                    // Show progress every few bars so the waveform fills in as it's measured.
                                    if (bar >= lastEmittedBar + EMIT_EVERY_BARS) {
                                        lastEmittedBar = bar
                                        emit(Progress(bars.levels(), complete = false))
                                    }
                                }
                                decoder.releaseOutputBuffer(outIndex, false)
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            }
                            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                                encoding = encodingOf(decoder.outputFormat) ?: return@flow
                        }
                    }
                    if (bars.hasAny()) emit(Progress(bars.levels(), complete = true))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Unsupported codec, unreadable file...: the live measurement still fills bars as it plays.
                    Log.w(TAG, "waveform analysis failed: ${e.javaClass.simpleName}: ${e.message}")
                } finally {
                    runCatching { codec?.stop() }
                    runCatching { codec?.release() }
                    runCatching { extractor.release() }
                }
            }.flowOn(Dispatchers.Default)

        private fun encodingOf(format: MediaFormat): PcmEncoding? {
            val pcm =
                if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    AudioFormat.ENCODING_PCM_16BIT
                }
            return when {
                pcm == AudioFormat.ENCODING_PCM_16BIT -> PcmEncoding.PCM_16
                pcm == AudioFormat.ENCODING_PCM_FLOAT -> PcmEncoding.FLOAT
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pcm == AudioFormat.ENCODING_PCM_24BIT_PACKED ->
                    PcmEncoding.PCM_24
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pcm == AudioFormat.ENCODING_PCM_32BIT ->
                    PcmEncoding.PCM_32
                else -> null
            }
        }

        private companion object {
            const val TAG = "WaveformAnalyzer"
            const val TIMEOUT_US = 10_000L
            const val US_PER_MS = 1000L

            /** Every 3rd sample (odd, so both stereo channels are read): same loudness, a third of the work. */
            const val STRIDE = 3
            const val EMIT_EVERY_BARS = 4
        }
    }
