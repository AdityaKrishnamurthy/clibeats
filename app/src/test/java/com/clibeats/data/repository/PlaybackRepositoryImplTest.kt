@file:Suppress("ForbiddenImport")

package com.clibeats.data.repository

import com.clibeats.data.local.dao.QueueDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.SongEntity
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.clibeats.playback.PlayerAdapter
import com.clibeats.playback.StreamResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRepositoryImplTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var playerAdapter: PlayerAdapter
    private lateinit var streamResolver: StreamResolver
    private lateinit var queueDao: QueueDao
    private lateinit var songDao: SongDao
    private lateinit var appPreferences: AppPreferences
    private lateinit var repository: PlaybackRepositoryImpl

    private val queueFlow = MutableStateFlow<List<Track>>(emptyList())
    private val playbackStateFlow =
        MutableStateFlow(
            PlaybackState(
                currentTrack = null,
                isPlaying = false,
                positionMs = 0L,
                bufferedPositionMs = 0L,
                repeatMode = RepeatMode.OFF,
                shuffleEnabled = false,
            ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        playerAdapter = mock()
        streamResolver = mock()
        queueDao = mock()
        songDao = mock()
        appPreferences = mock()

        whenever(playerAdapter.queueFlow).thenReturn(queueFlow)
        whenever(playerAdapter.playbackState).thenReturn(playbackStateFlow)

        whenever(appPreferences.lastQueueIndex).thenReturn(flowOf(0))
        whenever(appPreferences.lastPlaybackPosition).thenReturn(flowOf(0L))
        whenever(appPreferences.savedRepeatMode).thenReturn(flowOf("OFF"))
        whenever(appPreferences.savedShuffleEnabled).thenReturn(flowOf(false))

        repository =
            PlaybackRepositoryImpl(
                playerAdapter,
                streamResolver,
                queueDao,
                songDao,
                appPreferences,
                testDispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `playbackState delegates to playerAdapter`() {
        assertThat(repository.playbackState.value).isEqualTo(playbackStateFlow.value)
    }

    @Test
    fun `queueState delegates to playerAdapter`() {
        assertThat(repository.queueState.value).isEqualTo(queueFlow.value)
    }

    @Test
    fun `play delegates to playerAdapter`() {
        repository.play()
        verify(playerAdapter).play()
    }

    @Test
    fun `pause delegates to playerAdapter`() {
        repository.pause()
        verify(playerAdapter).pause()
    }

    @Test
    fun `seekTo delegates to playerAdapter`() {
        repository.seekTo(1500L)
        verify(playerAdapter).seekTo(1500L)
    }

    @Test
    fun `restores queue when saved songs exist`() =
        runTest(testDispatcher) {
            val entity =
                SongEntity(
                    id = "s1",
                    title = "Restored Track",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 200000L,
                    artworkUrl = null,
                    streamUrl = "http://stream/s1",
                    providerId = "ytmusic",
                )
            whenever(queueDao.getQueueSongs()).thenReturn(listOf(entity))

            PlaybackRepositoryImpl(
                playerAdapter,
                streamResolver,
                queueDao,
                songDao,
                appPreferences,
                testDispatcher,
            )

            verify(playerAdapter).restoreQueue(
                tracks = any(),
                startIndex = eq(0),
                positionMs = eq(0L),
                repeatMode = eq(RepeatMode.OFF),
                shuffleEnabled = eq(false),
            )
        }

    @Test
    fun `startup does not overwrite the saved queue or position before restoring`() =
        runTest(testDispatcher) {
            val entity =
                SongEntity(
                    id = "s2",
                    title = "Second",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 200000L,
                    artworkUrl = null,
                    streamUrl = null,
                    providerId = "ytmusic",
                )
            whenever(queueDao.getQueueSongs()).thenReturn(listOf(entity, entity.copy(id = "s3")))
            whenever(appPreferences.lastQueueIndex).thenReturn(flowOf(1))
            whenever(appPreferences.lastPlaybackPosition).thenReturn(flowOf(42_000L))

            PlaybackRepositoryImpl(playerAdapter, streamResolver, queueDao, songDao, appPreferences, testDispatcher)

            verify(playerAdapter).restoreQueue(
                tracks = any(),
                startIndex = eq(1),
                positionMs = eq(42_000L),
                repeatMode = eq(RepeatMode.OFF),
                shuffleEnabled = eq(false),
            )
            // The player's empty initial state must never be saved over the real queue / song / position.
            verify(appPreferences, never()).saveQueueMetadata(any(), any(), any(), any())
            verify(queueDao, never()).clearQueue()
        }

    @Test
    fun `removeFromQueue delegates to playerAdapter`() {
        repository.removeFromQueue(2)
        verify(playerAdapter).removeFromQueue(2)
    }

    @Test
    fun `clearQueue delegates to playerAdapter`() {
        repository.clearQueue()
        verify(playerAdapter).clearQueue()
    }

    @Test
    fun `toggleShuffle delegates to playerAdapter`() {
        repository.toggleShuffle()
        verify(playerAdapter).toggleShuffle()
    }

    @Test
    fun `setRepeatMode delegates to playerAdapter`() {
        repository.setRepeatMode(RepeatMode.ONE)
        verify(playerAdapter).setRepeatMode(RepeatMode.ONE)
    }

    private fun track(
        id: String,
        providerId: String = "youtube_music",
    ) = Track(id, "Song $id", "Artist", "Album", 1_000L, null, null, providerId)

    private fun nowPlaying(track: Track) {
        playbackStateFlow.value = playbackStateFlow.value.copy(currentTrack = track)
    }

    @Test
    fun `previous plays the song from an earlier queue before the current one`() {
        val local = track("a", providerId = "local")
        val online = track("b")
        nowPlaying(local)
        queueFlow.value = listOf(online) // a new song was tapped, starting a new queue
        nowPlaying(online)
        whenever(playerAdapter.currentIndex()).thenReturn(0)

        repository.skipToPrevious()

        verify(playerAdapter).playBeforeCurrent(local)
        verify(playerAdapter, never()).skipToPrevious()
    }

    @Test
    fun `previous steps back in the queue when the previous song is right before`() {
        val first = track("a")
        val second = track("b")
        queueFlow.value = listOf(first, second)
        nowPlaying(first)
        nowPlaying(second)
        whenever(playerAdapter.currentIndex()).thenReturn(1)

        repository.skipToPrevious()

        verify(playerAdapter).skipToPrevious()
        verify(playerAdapter, never()).playBeforeCurrent(any())
    }

    @Test
    fun `previous restarts the song when it is more than 3 seconds in`() {
        val first = track("a")
        val second = track("b")
        queueFlow.value = listOf(first, second)
        nowPlaying(first)
        nowPlaying(second)
        whenever(playerAdapter.currentIndex()).thenReturn(1)
        whenever(playerAdapter.currentPositionMs()).thenReturn(10_000L)

        repository.skipToPrevious()

        verify(playerAdapter).seekTo(0L)
        verify(playerAdapter, never()).skipToPrevious()
        verify(playerAdapter, never()).playBeforeCurrent(any())
    }

    @Test
    fun `previous pressed again near the start goes to the song before`() {
        val first = track("a")
        val second = track("b")
        queueFlow.value = listOf(first, second)
        nowPlaying(first)
        nowPlaying(second)
        whenever(playerAdapter.currentIndex()).thenReturn(1)
        whenever(playerAdapter.currentPositionMs()).thenReturn(10_000L, 200L)

        repository.skipToPrevious() // restarts "b"
        repository.skipToPrevious() // now at 0:00, goes back to "a"

        verify(playerAdapter).seekTo(0L)
        verify(playerAdapter).skipToPrevious()
    }

    @Test
    fun `previous falls back to the queue when nothing played before`() {
        nowPlaying(track("a"))

        repository.skipToPrevious()

        verify(playerAdapter).skipToPrevious()
        verify(playerAdapter, never()).playBeforeCurrent(any())
    }
}
