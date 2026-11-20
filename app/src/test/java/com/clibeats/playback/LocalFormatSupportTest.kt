package com.clibeats.playback

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.PositionHolder
import androidx.media3.test.utils.FakeExtractorInput
import androidx.media3.test.utils.FakeExtractorOutput
import androidx.media3.test.utils.FakeTrackOutput
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Local files are played through ExoPlayer's default extractors (DefaultMediaSourceFactory), which detect
 * the container from the file's bytes, not its name. Each fixture in resources/audio is a real 0.25-0.5 s
 * file made with ffmpeg; this checks that one of the default extractors recognises it and reads its audio.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LocalFormatSupportTest {
    @Before
    fun silenceMedia3Log() {
        // Extractors log harmless warnings (e.g. a skipped WAV LIST chunk); android.util.Log isn't available here.
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
    }

    @Test fun `mp3 is read`() = assertReads("tone.mp3", MimeTypes.AUDIO_MPEG)

    @Test fun `m4a with aac is read`() = assertReads("tone.m4a", MimeTypes.AUDIO_AAC)

    @Test fun `m4a with alac is read`() = assertReads("tone_alac.m4a", MimeTypes.AUDIO_ALAC)

    @Test fun `flac is read`() = assertReads("tone.flac", MimeTypes.AUDIO_FLAC)

    @Test fun `ogg vorbis is read`() = assertReads("tone.ogg", MimeTypes.AUDIO_VORBIS)

    @Test fun `opus is read`() = assertReads("tone.opus", MimeTypes.AUDIO_OPUS)

    @Test fun `wav is read`() = assertReads("tone.wav", MimeTypes.AUDIO_RAW)

    @Test fun `aac adts is read`() = assertReads("tone.aac", MimeTypes.AUDIO_AAC)

    private fun assertReads(
        name: String,
        expectedMime: String,
    ) {
        val bytes = requireNotNull(javaClass.classLoader?.getResourceAsStream("audio/$name")) { name }.readBytes()
        val extractor =
            DefaultExtractorsFactory().createExtractors().firstOrNull { candidate ->
                runCatching { candidate.sniff(input(bytes)) }.getOrDefault(false)
            }
        assertThat(extractor).isNotNull()
        val output = FakeExtractorOutput()
        extractor!!.init(output)
        val input = input(bytes)
        val position = PositionHolder()
        var result = Extractor.RESULT_CONTINUE
        var guard = 0
        while (result != Extractor.RESULT_END_OF_INPUT && guard++ < MAX_READS) {
            result = extractor.read(input, position)
            if (result == Extractor.RESULT_SEEK) input.setPosition(position.position.toInt())
        }
        val audio =
            (0 until output.numberOfTracks)
                .map { output.trackOutputs.valueAt(it) as FakeTrackOutput }
                .firstOrNull { it.lastFormat?.sampleMimeType?.startsWith("audio/") == true }
        assertThat(audio).isNotNull()
        assertThat(audio!!.lastFormat!!.sampleMimeType).isEqualTo(expectedMime)
        assertThat(audio.sampleCount).isGreaterThan(0)
        assertThat(output.seekMap?.durationUs ?: C.TIME_UNSET).isNotEqualTo(0L)
    }

    private fun input(bytes: ByteArray) =
        FakeExtractorInput
            .Builder()
            .setData(bytes)
            .build()

    private companion object {
        const val MAX_READS = 100_000
    }
}
