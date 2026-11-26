@file:Suppress("ForbiddenImport")

package com.clibeats.integration

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.ImageLoader
import com.clibeats.data.local.dao.QueueDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.QueueEntity
import com.clibeats.data.local.mapper.toEntity
import com.clibeats.data.network.NetworkMonitor
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.data.repository.PlaybackRepositoryImpl
import com.clibeats.data.repository.QueuePersistenceCoordinator
import com.clibeats.data.repository.RadioAutoplayCoordinator
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.MusicProvider
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.repository.HistoryRepository
import com.clibeats.playback.PlayerAdapter
import com.clibeats.playback.StreamResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackIntegrationTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = mock()
    private val exoPlayer: ExoPlayer = mock()
    private val streamResolver: StreamResolver = mock()
    private val queueDao: QueueDao = mock()
    private val songDao: SongDao = mock()
    private val appPreferences: AppPreferences = mock()
    private val historyRepository: HistoryRepository = mock()
    private val providerRegistry: ProviderRegistry = mock()
    private val provider: MusicProvider = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val imageLoader: dagger.Lazy<ImageLoader> = dagger.Lazy { mock() }

    private val storedQueueItems = mutableListOf<QueueEntity>()
    private val storedSongs = mutableMapOf<String, Track>()
    private val mediaItems = mutableListOf<MediaItem>()
    private var currentIdx = 0

    private fun track(
        id: String,
        providerId: String = "youtube_music",
    ) = Track(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        artworkUrl = null,
        streamUrl = "https://example.com/$id.m4a",
        providerId = providerId,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(exoPlayer.currentPosition).thenReturn(0L)
        whenever(exoPlayer.bufferedPosition).thenReturn(0L)
        whenever(exoPlayer.isPlaying).thenReturn(false)
        whenever(exoPlayer.currentMediaItemIndex).thenAnswer { currentIdx }
        whenever(exoPlayer.mediaItemCount).thenAnswer { mediaItems.size }
        whenever(exoPlayer.setMediaItems(any<List<MediaItem>>(), any<Int>(), any<Long>())).doAnswer { inv ->
            val items = inv.getArgument<List<MediaItem>>(0)
            mediaItems.clear()
            mediaItems.addAll(items)
            currentIdx = inv.getArgument(1)
            Unit
        }
        whenever(exoPlayer.addMediaItems(any<List<MediaItem>>())).doAnswer { inv ->
            mediaItems.addAll(inv.getArgument(0))
            Unit
        }
        whenever(exoPlayer.addMediaItem(any<Int>(), any<MediaItem>())).doAnswer { inv ->
            mediaItems.add(inv.getArgument(0), inv.getArgument(1))
            Unit
        }
        whenever(exoPlayer.hasNextMediaItem()).thenAnswer { currentIdx < mediaItems.lastIndex }
        whenever(exoPlayer.seekToNextMediaItem()).doAnswer {
            if (currentIdx < mediaItems.lastIndex) currentIdx++
            Unit
        }

        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
        whenever(appPreferences.autoplayEnabled).thenReturn(flowOf(true))
        whenever(appPreferences.lastQueueIndex).thenReturn(flowOf(0))
        whenever(appPreferences.lastPlaybackPosition).thenReturn(flowOf(0L))
        whenever(appPreferences.savedRepeatMode).thenReturn(flowOf(RepeatMode.OFF.name))
        whenever(appPreferences.savedShuffleEnabled).thenReturn(flowOf(false))
        whenever(historyRepository.getRecentlyPlayedTracks(any())).thenReturn(flowOf(emptyList()))
        whenever(providerRegistry.getProvider(any())).thenReturn(provider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildRepo(adapter: PlayerAdapter): PlaybackRepositoryImpl =
        PlaybackRepositoryImpl(
            playerAdapter = adapter,
            streamResolver = streamResolver,
            queuePersistence = QueuePersistenceCoordinator(queueDao, songDao, appPreferences),
            radioAutoplay =
                RadioAutoplayCoordinator(
                    appPreferences = appPreferences,
                    historyRepository = historyRepository,
                    providerRegistry = providerRegistry,
                    networkMonitor = networkMonitor,
                ),
            songDao = songDao,
            historyRepository = historyRepository,
            imageLoader = imageLoader,
            appContext = context,
            ioDispatcher = testDispatcher,
        )

    private fun sortedQueueSongs() =
        storedQueueItems
            .sortedBy { it.position }
            .mapNotNull { item -> storedSongs[item.songId]?.toEntity() }

    @Test
    fun queueRadioBoundaryPersistsAcrossRestart() =
        runTest(testDispatcher) {
            whenever(queueDao.getQueueSongs()).thenAnswer { sortedQueueSongs() }
            whenever(queueDao.getQueueItems()).thenAnswer { storedQueueItems.toList() }
            whenever(queueDao.replaceQueue(any())).doAnswer { inv ->
                storedQueueItems.clear()
                storedQueueItems.addAll(inv.getArgument(0))
                Unit
            }
            whenever(songDao.insertAllIfAbsent(any())).doAnswer { Unit }
            whenever(songDao.getById(any())).thenReturn(null)
            whenever(songDao.upsert(any())).doAnswer { Unit }

            val seed = track("seed")
            val r1 = track("r1")
            val r2 = track("r2")
            val queued = track("queued")
            listOf(seed, r1, r2, queued).forEach { storedSongs[it.id] = it }

            whenever(streamResolver.resolve(seed)).thenReturn(seed)
            whenever(provider.relatedTracks(any(), any())).thenReturn(ProviderResult.Success(listOf(r1, r2)))

            val adapter1 = PlayerAdapter(exoPlayer, streamResolver, context)
            val repo1 = buildRepo(adapter1)
            advanceUntilIdle()

            repo1.playFrom(listOf(seed), startIndex = 0, source = "Home")
            advanceUntilIdle()

            assertThat(adapter1.queueFlow.value.map { it.id }).containsExactly("seed", "r1", "r2").inOrder()
            assertThat(adapter1.radioTrackIds.value).containsExactly("r1", "r2")

            // Move to first radio track r1 (index 1) and queue a user track.
            currentIdx = 1
            repo1.addToQueue(queued)
            advanceUntilIdle()

            // Wait/assert user track appears after r1 and strictly before remaining radio r2.
            assertThat(adapter1.queueFlow.value.map { it.id }).containsExactly("seed", "r1", "queued", "r2").inOrder()
            val persistedRadio = storedQueueItems.filter { it.isRadio }.map { it.songId }
            assertThat(persistedRadio).containsExactly("r1", "r2")

            // Simulate process restart with new PlayerAdapter + PlaybackRepositoryImpl restoring persisted queue.
            whenever(appPreferences.lastQueueIndex).thenReturn(flowOf(1))
            val adapter2 = PlayerAdapter(exoPlayer, streamResolver, context)
            val repo2 = buildRepo(adapter2)
            advanceUntilIdle()

            val extra = track("extra")
            storedSongs[extra.id] = extra
            currentIdx = 1
            repo2.addToQueue(extra)
            advanceUntilIdle()

            assertThat(adapter2.queueFlow.value.map { it.id })
                .containsExactly("seed", "r1", "queued", "extra", "r2")
                .inOrder()
        }
}
