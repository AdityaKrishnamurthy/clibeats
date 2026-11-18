package com.clibeats.presentation.player

import androidx.lifecycle.ViewModel
import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        val playbackState: StateFlow<PlaybackState> = playbackRepository.playbackState

        fun playTrack(track: Track) {
            playbackRepository.playTrack(track)
        }

        /** Plays [tracks] as the queue from [index], so next/previous and auto-advance work. */
        fun playQueue(
            tracks: List<Track>,
            index: Int,
        ) {
            playbackRepository.setQueue(tracks, index)
        }

        fun onPlayPauseClick() {
            if (playbackState.value.isPlaying) {
                playbackRepository.pause()
            } else {
                playbackRepository.play()
            }
        }

        fun onSkipNextClick() {
            playbackRepository.skipToNext()
        }

        fun onSkipPreviousClick() {
            playbackRepository.skipToPrevious()
        }

        /** Cycles OFF -> ALL (repeat queue) -> ONE (repeat track) -> OFF. */
        fun onRepeatClick() {
            val next =
                when (playbackState.value.repeatMode) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
            playbackRepository.setRepeatMode(next)
        }

        fun seekTo(positionMs: Long) {
            playbackRepository.seekTo(positionMs)
        }
    }
