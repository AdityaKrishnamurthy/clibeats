@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.player

import com.clibeats.data.download.ActiveDownload
import com.clibeats.data.download.DownloadStatus
import com.clibeats.data.download.TrackDownloadManager
import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.repository.PlaybackRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class PlayerViewModelTest {
    private lateinit var repository: PlaybackRepository
    private lateinit var viewModel: PlayerViewModel
    private val stateFlow =
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

    private val pauseAtEnd = MutableStateFlow(false)
    private val sleepCountdown = MutableStateFlow<Int?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mock()
        whenever(repository.playbackState).thenReturn(stateFlow)
        whenever(repository.playerMessages).thenReturn(emptyFlow())
        whenever(repository.pauseAtEndOfTrack).thenReturn(pauseAtEnd)
        whenever(repository.sleepTimerCountdown).thenReturn(sleepCountdown)
        viewModel = PlayerViewModel(repository)
    }

    @Test
    fun onPlayPauseClick_whenNotPlaying_callsPlay() {
        stateFlow.value = stateFlow.value.copy(isPlaying = false)
        viewModel.onPlayPauseClick()
        verify(repository).play()
    }

    @Test
    fun onPlayPauseClick_whenPlaying_callsPause() {
        stateFlow.value = stateFlow.value.copy(isPlaying = true)
        viewModel.onPlayPauseClick()
        verify(repository).pause()
    }

    @Test
    fun onSkipNextClick_callsSkipNext() {
        viewModel.onSkipNextClick()
        verify(repository).skipToNext()
    }

    @Test
    fun onSkipPreviousClick_callsSkipPrevious() {
        viewModel.onSkipPreviousClick()
        verify(repository).skipToPrevious()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun endOfTrackSleep_armsPlayer_andTurnsOffWhenSongEnds() {
        whenever(repository.setPauseAtEndOfTrack(true)).then { pauseAtEnd.value = true }
        viewModel.setSleepTimer(null)
        verify(repository).setPauseAtEndOfTrack(true)
        assertThat(viewModel.sleepTimer.value).isEqualTo(SleepTimer.EndOfTrack)

        // The player paused at the end of the song (works with repeat-one too) and disarmed itself.
        pauseAtEnd.value = false
        assertThat(viewModel.sleepTimer.value).isEqualTo(SleepTimer.Off)
    }

    @Test
    fun cancellingEndOfTrackSleep_disarmsPlayer() {
        whenever(repository.setPauseAtEndOfTrack(true)).then { pauseAtEnd.value = true }
        whenever(repository.setPauseAtEndOfTrack(false)).then { pauseAtEnd.value = false }
        viewModel.setSleepTimer(null)
        viewModel.setSleepTimer(0)
        verify(repository).setPauseAtEndOfTrack(false)
        assertThat(viewModel.sleepTimer.value).isEqualTo(SleepTimer.Off)
    }

    @Test
    fun downloadBanner_tracksProgressAndSurfacesFailures() =
        runTest {
            val active = MutableStateFlow<ActiveDownload?>(null)
            val downloadManager: TrackDownloadManager = mock()
            whenever(downloadManager.activeDownload).thenReturn(active)
            val vm = PlayerViewModel(repository, downloadManager = downloadManager)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.downloadBanner.collect()
            }

            active.value = ActiveDownload("t1", "Song A", DownloadStatus.Downloading(45))
            assertThat(vm.downloadBanner.value).isEqualTo(
                DownloadBanner(text = "Downloading \u201cSong A\u201d \u00b7 45%", progressPercent = 45),
            )

            active.value = ActiveDownload("t1", "Song A", DownloadStatus.Failed("HTTP 403"))
            assertThat(vm.downloadBanner.value).isEqualTo(
                DownloadBanner(text = "Download failed: \u201cSong A\u201d (HTTP 403)", isError = true),
            )

            advanceTimeBy(3_100L)
            assertThat(vm.downloadBanner.value).isNull()
        }
}
