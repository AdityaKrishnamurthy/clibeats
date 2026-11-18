package com.clibeats.presentation.player

import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.repository.PlaybackRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mock()
        whenever(repository.playbackState).thenReturn(stateFlow)
        whenever(repository.playerMessages).thenReturn(emptyFlow())
        whenever(repository.pauseAtEndOfTrack).thenReturn(pauseAtEnd)
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
        viewModel.setSleepTimer(null)
        viewModel.setSleepTimer(0)
        verify(repository).setPauseAtEndOfTrack(false)
        assertThat(viewModel.sleepTimer.value).isEqualTo(SleepTimer.Off)
    }
}
