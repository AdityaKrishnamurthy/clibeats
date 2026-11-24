@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.playlist

import com.clibeats.data.download.TrackDownloadManager
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.domain.repository.PlaylistRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var downloadManager: TrackDownloadManager
    private lateinit var viewModel: PlaylistViewModel

    private val fakePlaylistsFlow = MutableStateFlow<List<Playlist>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playlistRepository = mock()
        playbackRepository = mock()
        downloadManager = mock()
        whenever(playlistRepository.getAllPlaylistsAsFlow()).thenReturn(fakePlaylistsFlow)
        whenever(playlistRepository.getPlaylistCoversAsFlow()).thenReturn(flowOf(emptyMap()))
        whenever(downloadManager.getDownloadedSongs()).thenReturn(flowOf(emptyList()))
        viewModel =
            PlaylistViewModel(
                playlistRepository = playlistRepository,
                playbackRepository = playbackRepository,
                downloadManager = downloadManager,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState emits Success with empty list initially`() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            testDispatcher.scheduler.runCurrent()

            assertThat(viewModel.uiState.value).isInstanceOf(PlaylistUiState.Success::class.java)
            val success = viewModel.uiState.value as PlaylistUiState.Success
            assertThat(success.playlists).isEmpty()
        }

    @Test
    fun `createPlaylist invokes repository upsertPlaylist`() =
        runTest {
            viewModel.createPlaylist("My Favs", "Best tracks")
            testDispatcher.scheduler.runCurrent()
            verify(playlistRepository).upsertPlaylist(any())
        }

    @Test
    fun `deletePlaylist invokes repository deletePlaylist`() =
        runTest {
            viewModel.deletePlaylist("playlist_123")
            testDispatcher.scheduler.runCurrent()
            verify(playlistRepository).deletePlaylist("playlist_123")
        }

    @Test
    fun `openDownloaded opens downloaded playlist`() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            testDispatcher.scheduler.runCurrent()

            viewModel.openDownloaded()
            testDispatcher.scheduler.runCurrent()

            assertThat(viewModel.isPlaylistOpen).isTrue()
            val state = viewModel.uiState.value as PlaylistUiState.Success
            assertThat(state.isDownloadedOpen).isTrue()
        }

    @Test
    fun `removeFromDownloaded delegates to downloadManager`() =
        runTest {
            val track = Track("d1", "Test", "Artist", "Album", 1000L, null, null, "youtube_music")
            viewModel.removeFromDownloaded(track)
            testDispatcher.scheduler.runCurrent()
            verify(downloadManager).deleteDownload("d1")
        }

    @Test
    fun `clearAllDownloaded delegates to downloadManager`() =
        runTest {
            viewModel.clearAllDownloaded()
            testDispatcher.scheduler.runCurrent()
            verify(downloadManager).deleteAllDownloads()
        }
}
