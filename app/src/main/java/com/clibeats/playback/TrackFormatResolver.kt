package com.clibeats.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.clibeats.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves and caches codec, bitrate, sample rate, and PCM bit-depth metadata for active tracks.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TrackFormatResolver(
    private val context: Context,
    private val streamResolver: StreamResolver,
    private val scope: CoroutineScope,
    private val onBitrateResolved: () -> Unit,
) {
    private val localBitrateKbps = ConcurrentHashMap<String, Int>()
    private val memoizedBitrateKbps = ConcurrentHashMap<String, Int>()

    fun inspectBitrate(
        track: Track?,
        format: Format?,
        durationMs: Long,
    ): Int? {
        val formatBitrate =
            format?.let { f ->
                listOf(f.averageBitrate, f.bitrate, f.peakBitrate).firstOrNull { it > 0 }?.div(BITS_PER_KBIT)
            }
        if (format != null && formatBitrate == null && track != null) {
            estimateLocalBitrate(track, durationMs)
        }
        if (formatBitrate == null && track != null && !memoizedBitrateKbps.containsKey(track.id)) {
            resolveTrackBitrate(track, durationMs)
        }
        return formatBitrate
            ?: track?.let { t ->
                localBitrateKbps[t.id]?.takeIf { it > 0 } ?: memoizedBitrateKbps[t.id]
            }
    }

    fun resolveTrackBitrate(
        track: Track,
        durationMs: Long,
    ) {
        if (memoizedBitrateKbps.containsKey(track.id)) return
        val resolvedDuration = durationMs.takeIf { it > 0 } ?: track.durationMs
        scope.launch(Dispatchers.IO) {
            val bitrate = streamResolver.knownBitrateKbps(track, resolvedDuration)
            if (bitrate != null && bitrate > 0) {
                memoizedBitrateKbps[track.id] = bitrate
                withContext(Dispatchers.Main) { onBitrateResolved() }
            }
        }
    }

    private fun estimateLocalBitrate(
        track: Track,
        durationMs: Long,
    ) {
        val uri = track.streamUrl?.takeIf { track.providerId == LOCAL_PROVIDER_ID } ?: return
        if (durationMs <= 0 || localBitrateKbps.containsKey(track.id)) return
        localBitrateKbps[track.id] = 0
        scope.launch {
            val bytes =
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver
                            .openAssetFileDescriptor(Uri.parse(uri), "r")
                            ?.use { it.length }
                    }.getOrNull()
                } ?: return@launch
            if (bytes > 0) {
                localBitrateKbps[track.id] = (bytes * BITS_PER_BYTE / durationMs).toInt()
                withContext(Dispatchers.Main) { onBitrateResolved() }
            }
        }
    }

    companion object {
        private const val BIT_DEPTH_8 = 8
        private const val BIT_DEPTH_16 = 16
        private const val BIT_DEPTH_24 = 24
        private const val BIT_DEPTH_32 = 32
        private const val BITS_PER_BYTE = 8L
        private const val BITS_PER_KBIT = 1000
        private const val LOCAL_PROVIDER_ID = "local"

        fun codecLabel(mime: String): String =
            when (mime) {
                MimeTypes.AUDIO_FLAC -> "FLAC"
                MimeTypes.AUDIO_MPEG -> "MP3"
                MimeTypes.AUDIO_OPUS -> "OPUS"
                MimeTypes.AUDIO_AAC -> "AAC"
                MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_WAV -> "WAV"
                MimeTypes.AUDIO_VORBIS -> "VORBIS"
                MimeTypes.AUDIO_ALAC -> "ALAC"
                else -> mime.substringAfter('/').uppercase()
            }

        fun bitDepthOf(pcmEncoding: Int): Int? =
            when (pcmEncoding) {
                C.ENCODING_PCM_16BIT -> BIT_DEPTH_16
                C.ENCODING_PCM_24BIT -> BIT_DEPTH_24
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> BIT_DEPTH_32
                C.ENCODING_PCM_8BIT -> BIT_DEPTH_8
                else -> null
            }
    }
}
