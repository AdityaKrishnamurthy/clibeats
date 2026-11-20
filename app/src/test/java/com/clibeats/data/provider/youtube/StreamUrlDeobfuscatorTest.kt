package com.clibeats.data.provider.youtube

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class StreamUrlDeobfuscatorTest {
    @Test
    fun `audio in formats is used when adaptiveFormats is absent`() {
        val streamingData =
            Json.parseToJsonElement(
                """
                {"formats":[
                  {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":129000,
                   "url":"https://rr1.googlevideo.com/videoplayback?expire=1700000000&x=1"}
                ]}
                """.trimIndent(),
            )

        val extracted = StreamUrlDeobfuscator.extractAudioStream(streamingData, "test")

        assertThat(extracted).isNotNull()
        assertThat(requireNotNull(extracted).itag).isEqualTo(140)
        assertThat(extracted.host).isEqualTo("rr1.googlevideo.com")
    }

    @Test
    fun `the highest bitrate audio format wins`() {
        val streamingData =
            Json.parseToJsonElement(
                """
                {"adaptiveFormats":[
                  {"itag":139,"mimeType":"audio/mp4","bitrate":48000,
                   "url":"https://a.example/low"},
                  {"itag":251,"mimeType":"audio/webm","bitrate":160000,
                   "url":"https://a.example/high"},
                  {"itag":18,"mimeType":"video/mp4","bitrate":900000,
                   "url":"https://a.example/video"}
                ]}
                """.trimIndent(),
            )

        val extracted = StreamUrlDeobfuscator.extractAudioStream(streamingData, "test")

        assertThat(requireNotNull(extracted).itag).isEqualTo(251)
    }

    @Test
    fun `cipher-only formats fail instead of returning a bogus url`() {
        val streamingData =
            Json.parseToJsonElement(
                """
                {"adaptiveFormats":[
                  {"itag":140,"mimeType":"audio/mp4","signatureCipher":"s=abc&url=https%3A%2F%2Fa.example%2Fx"}
                ]}
                """.trimIndent(),
            )

        assertThat(StreamUrlDeobfuscator.extractAudioStream(streamingData, "test")).isNull()
    }
}
