@file:Suppress("ForbiddenImport")

package com.clibeats.data.download

import com.clibeats.domain.model.Track
import com.clibeats.playback.StreamResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.io.File
import java.io.RandomAccessFile

class DownloadStreamFetcherTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val streamResolver: dagger.Lazy<StreamResolver> = mock()

    @Test
    fun `TrackDownloadWorker toInputData and trackFromInput roundtrip track fields`() {
        val original =
            Track(
                id = "yt:abc",
                title = "Nightfall",
                artist = "Kavinsky",
                album = "OutRun",
                durationMs = 240_000L,
                artworkUrl = "https://img.example/cover.jpg",
                streamUrl = "https://cdn.example/stream.m4a",
                providerId = "youtube_music",
            )

        val restored = TrackDownloadWorker.trackFromInput(TrackDownloadWorker.toInputData(original))

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `DownloadStreamFetcher resumes from chunks checkpoint without re-downloading completed ranges`() =
        runTest {
            val payload = ByteArray(1_200_000) { idx -> (idx % 251).toByte() }
            val dir = tempFolder.newFolder("dl")
            val firstChunkLen = 1_048_576
            val partFile =
                File(dir, "resume.part").apply {
                    RandomAccessFile(this, "rw").use { raf ->
                        raf.setLength(payload.size.toLong())
                        raf.write(payload, 0, firstChunkLen)
                    }
                }
            val chunksFile =
                File(dir, "resume.chunks").apply {
                    writeText("v1:${payload.size}:m4a:audio/mp4\n0\n")
                }

            val server = MockWebServer()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val header = request.getHeader("Range").orEmpty().removePrefix("bytes=")
                        val (fromStr, toStr) = header.split("-")
                        val from = fromStr.toInt()
                        val to = toStr.toInt()
                        val slice = payload.copyOfRange(from, to + 1)
                        return MockResponse()
                            .setResponseCode(206)
                            .addHeader("Content-Range", "bytes $from-$to/${payload.size}")
                            .addHeader("Content-Type", "audio/mp4")
                            .setBody(Buffer().write(slice))
                    }
                }
            server.start()
            try {
                val fetcher = DownloadStreamFetcher(OkHttpClient(), streamResolver)
                val percents = mutableListOf<Int>()
                val format = fetcher.fetch(server.url("/track.m4a").toString(), partFile, chunksFile) { percents += it }

                assertThat(format).isEqualTo(DownloadFormat("m4a", "audio/mp4"))
                assertThat(server.requestCount).isEqualTo(1)
                assertThat(partFile.readBytes()).isEqualTo(payload)
                assertThat(percents.last()).isEqualTo(100)
            } finally {
                server.shutdown()
            }
        }
}
