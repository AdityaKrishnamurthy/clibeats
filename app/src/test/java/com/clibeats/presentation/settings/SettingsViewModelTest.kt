@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.settings

import com.clibeats.data.download.TrackDownloadManager
import com.clibeats.data.playlist.PlaylistExchangeManager
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.repository.SongRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appPreferences: AppPreferences
    private lateinit var playlistExchangeManager: PlaylistExchangeManager
    private lateinit var downloadManager: TrackDownloadManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appPreferences = mock()
        playlistExchangeManager = mock()
        downloadManager = mock()

        whenever(appPreferences.cacheMaxMb).thenReturn(flowOf(512))
        whenever(appPreferences.highQualityStreaming).thenReturn(flowOf(true))
        whenever(appPreferences.mirrorBackupsToDownloads).thenReturn(flowOf(true))
        whenever(appPreferences.themeMode).thenReturn(flowOf("DARK"))
        whenever(appPreferences.accentColorName).thenReturn(flowOf("GREEN"))
        whenever(appPreferences.themePalette).thenReturn(flowOf(null))
        whenever(appPreferences.autoQueueRelated).thenReturn(flowOf(true))
        whenever(appPreferences.localMusicTreeUris).thenReturn(flowOf(emptyList()))
        whenever(appPreferences.artworkCacheMb).thenReturn(flowOf(128))
        whenever(downloadManager.getDownloadedSongItems()).thenReturn(flowOf(emptyList()))
        whenever(downloadManager.getTotalDownloadedSizeBytes()).thenReturn(flowOf(0L))

        val songRepository: SongRepository = mock()
        whenever(songRepository.getLocalTrackCountAsFlow()).thenReturn(flowOf(0))

        viewModel =
            SettingsViewModel(
                appPreferences = appPreferences,
                playlistExchangeManager = playlistExchangeManager,
                songRepository = songRepository,
                downloadManager = downloadManager,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects preferences`() =
        runTest(testDispatcher) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            assertThat(state.cacheMaxMb).isEqualTo(512)
            assertThat(state.highQualityStreaming).isTrue()
            assertThat(state.mirrorBackupsToDownloads).isTrue()
        }

    @Test
    fun `setMirrorBackupsToDownloads delegates to appPreferences`() =
        runTest(testDispatcher) {
            viewModel.setMirrorBackupsToDownloads(false)
            testDispatcher.scheduler.advanceUntilIdle()
            verify(appPreferences).setMirrorBackupsToDownloads(false)
        }

    @Test
    fun `setSongCacheMb delegates to appPreferences`() =
        runTest(testDispatcher) {
            viewModel.setSongCacheMb(1024)
            testDispatcher.scheduler.advanceUntilIdle()
            verify(appPreferences).setCacheMaxMb(1024)
        }

    @Test
    fun `exportPlaylists surfaces manager result`() =
        runTest(testDispatcher) {
            val target = org.mockito.kotlin.mock<android.net.Uri>()
            whenever(playlistExchangeManager.export(target)).thenReturn(Result.success(2))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.exchangeMessage.collect()
            }

            viewModel.exportPlaylists(target)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.exchangeMessage.value).contains("Exported")
        }

    @Test
    fun `deleteDownload delegates to downloadManager`() =
        runTest(testDispatcher) {
            viewModel.deleteDownload("s1")
            testDispatcher.scheduler.advanceUntilIdle()
            verify(downloadManager).deleteDownload("s1")
        }

    @Test
    fun `clearAllDownloads delegates to downloadManager`() =
        runTest(testDispatcher) {
            viewModel.clearAllDownloads()
            testDispatcher.scheduler.advanceUntilIdle()
            verify(downloadManager).deleteAllDownloads()
        }
}
