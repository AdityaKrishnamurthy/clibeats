@file:Suppress("ForbiddenImport")

package com.clibeats.data.download

import android.content.Context
import com.clibeats.data.local.dao.DownloadedTrackDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.DownloadedTrackEntity
import com.clibeats.domain.model.Track
import com.clibeats.playback.StreamResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class TrackDownloadManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mock()
    private val okHttpClient: OkHttpClient = mock()
    private val streamResolver: dagger.Lazy<StreamResolver> = mock()
    private val downloadedTrackDao: DownloadedTrackDao = mock()
    private val songDao: SongDao = mock()

    private lateinit var manager: TrackDownloadManager
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(downloadedTrackDao.getAllFlow()).thenReturn(flowOf(emptyList()))
        whenever(downloadedTrackDao.getDownloadedSongItemsFlow()).thenReturn(flowOf(emptyList()))
        runBlocking { whenever(downloadedTrackDao.getAll()).thenReturn(emptyList()) }

        manager =
            TrackDownloadManager(
                context = context,
                okHttpClient = okHttpClient,
                streamResolver = streamResolver,
                downloadedTrackDao = downloadedTrackDao,
                songDao = songDao,
            )
    }

    @Test
    fun `isTrackDownloaded returns true when entity exists and file exists on disk`() =
        runTest {
            val audioFile =
                File(filesDir, "downloads/test.m4a").apply {
                    parentFile?.mkdirs()
                    writeBytes(byteArrayOf(1, 2, 3))
                }
            whenever(downloadedTrackDao.getById("song1")).thenReturn(
                DownloadedTrackEntity(
                    songId = "song1",
                    filePath = audioFile.absolutePath,
                    sizeBytes = 3L,
                    downloadedAt = System.currentTimeMillis(),
                    mimeType = "audio/mp4",
                ),
            )

            assertThat(manager.isTrackDownloaded("song1")).isTrue()
        }

    @Test
    fun `isTrackDownloaded returns false when file does not exist on disk`() =
        runTest {
            whenever(downloadedTrackDao.getById("song2")).thenReturn(
                DownloadedTrackEntity(
                    songId = "song2",
                    filePath = "/non/existent/file.m4a",
                    sizeBytes = 0L,
                    downloadedAt = System.currentTimeMillis(),
                    mimeType = "audio/mp4",
                ),
            )

            assertThat(manager.isTrackDownloaded("song2")).isFalse()
        }

    @Test
    fun `deleteDownload removes entity and deletes file from disk`() =
        runTest {
            val audioFile =
                File(filesDir, "downloads/test_delete.m4a").apply {
                    parentFile?.mkdirs()
                    writeBytes(byteArrayOf(1, 2, 3))
                }
            whenever(downloadedTrackDao.getById("song3")).thenReturn(
                DownloadedTrackEntity(
                    songId = "song3",
                    filePath = audioFile.absolutePath,
                    sizeBytes = 3L,
                    downloadedAt = System.currentTimeMillis(),
                    mimeType = "audio/mp4",
                ),
            )

            assertThat(audioFile.exists()).isTrue()
            manager.deleteDownload("song3")
            verify(downloadedTrackDao).deleteById("song3")
            assertThat(audioFile.exists()).isFalse()
        }

    @Test
    fun `deleteAllDownloads deletes all files and calls deleteAll on dao`() =
        runTest {
            val audioFile1 =
                File(filesDir, "downloads/test1.m4a").apply {
                    parentFile?.mkdirs()
                    writeBytes(byteArrayOf(1, 2, 3))
                }
            val audioFile2 =
                File(filesDir, "downloads/test2.m4a").apply {
                    parentFile?.mkdirs()
                    writeBytes(byteArrayOf(4, 5, 6))
                }

            assertThat(audioFile1.exists()).isTrue()
            assertThat(audioFile2.exists()).isTrue()

            manager.deleteAllDownloads()

            verify(downloadedTrackDao).deleteAll()
            assertThat(audioFile1.exists()).isFalse()
            assertThat(audioFile2.exists()).isFalse()
        }

    @Test
    fun `downloadTrack invalidates and retries with fresh URL on HTTP 403`() =
        runTest {
            val resolver: StreamResolver = mock()
            whenever(streamResolver.get()).thenReturn(resolver)
            val track = fakeTrack("yt1", "Test Song", "https://stale.example/audio")
            whenever(resolver.resolve(eq(track), any(), eq(true))).thenReturn(
                track.copy(streamUrl = "https://fresh.example/audio"),
            )

            val staleCall: Call = mock()
            val freshCall: Call = mock()
            whenever(staleCall.execute()).thenReturn(
                httpResponse("https://stale.example/audio", 403, byteArrayOf()),
            )
            whenever(freshCall.execute()).thenReturn(
                httpResponse("https://fresh.example/audio", 200, byteArrayOf(1, 2, 3, 4)),
            )
            whenever(okHttpClient.newCall(any())).thenAnswer { inv ->
                val req = inv.getArgument<Request>(0)
                if (req.url.toString().contains("stale")) staleCall else freshCall
            }

            manager.downloadTrack(track)

            val finalStatus = awaitTerminalStatus(track.id)

            assertThat(finalStatus).isInstanceOf(DownloadStatus.Completed::class.java)
            verify(resolver).invalidate("youtube_music", "yt1")
            verify(downloadedTrackDao).insert(any())
            assertThat(manager.activeDownload.value?.status).isInstanceOf(DownloadStatus.Completed::class.java)
        }

    @Test
    fun `downloadTrack reports Failed and leaves no part file when all attempts fail`() =
        runTest {
            val resolver: StreamResolver = mock()
            whenever(streamResolver.get()).thenReturn(resolver)
            val track = fakeTrack("yt2", "Fail Song", "https://stale.example/audio")
            whenever(resolver.resolve(eq(track), any(), eq(true))).thenReturn(
                track.copy(streamUrl = "https://fresh.example/audio"),
            )

            val call: Call = mock()
            whenever(call.execute()).thenAnswer {
                httpResponse("https://any.example/audio", 403, byteArrayOf())
            }
            whenever(okHttpClient.newCall(any())).thenReturn(call)

            manager.downloadTrack(track)

            val finalStatus = awaitTerminalStatus(track.id)

            assertThat(finalStatus).isInstanceOf(DownloadStatus.Failed::class.java)
            assertThat((finalStatus as DownloadStatus.Failed).message).contains("403")
            val downloadsDir = File(filesDir, "downloads")
            assertThat(downloadsDir.listFiles()?.filter { it.name.endsWith(".part") }).isEmpty()
        }

    @Test
    fun `downloadTrack deletes final file and marks Failed when DB insert fails`() =
        runTest {
            val resolver: StreamResolver = mock()
            whenever(streamResolver.get()).thenReturn(resolver)
            val track = fakeTrack("yt3", "DbFail Song", "https://ok.example/audio")
            whenever(resolver.resolve(eq(track), any(), eq(true))).thenReturn(track)
            whenever(downloadedTrackDao.insert(any())).thenThrow(RuntimeException("disk full"))

            val call: Call = mock()
            whenever(call.execute()).thenAnswer {
                httpResponse("https://ok.example/audio", 200, byteArrayOf(1, 2, 3))
            }
            whenever(okHttpClient.newCall(any())).thenReturn(call)

            manager.downloadTrack(track)

            val finalStatus = awaitTerminalStatus(track.id)

            assertThat(finalStatus).isInstanceOf(DownloadStatus.Failed::class.java)
            assertThat((finalStatus as DownloadStatus.Failed).message).contains("disk full")
            val downloadsDir = File(filesDir, "downloads")
            assertThat(downloadsDir.listFiles().orEmpty()).isEmpty()
        }

    private suspend fun awaitTerminalStatus(trackId: String): DownloadStatus? =
        withContext(Dispatchers.Default) {
            withTimeout(5_000L) {
                manager.downloads.first { map ->
                    val s = map[trackId]
                    s is DownloadStatus.Completed || s is DownloadStatus.Failed
                }[trackId]
            }
        }

    private fun fakeTrack(
        id: String,
        title: String,
        streamUrl: String,
    ): Track =
        Track(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            durationMs = 120_000L,
            artworkUrl = null,
            streamUrl = streamUrl,
            providerId = "youtube_music",
        )

    private fun httpResponse(
        url: String,
        code: Int,
        bytes: ByteArray,
    ): Response =
        Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Status $code")
            .header("Content-Type", "audio/webm")
            .body(bytes.toResponseBody("audio/webm".toMediaType()))
            .build()
}
