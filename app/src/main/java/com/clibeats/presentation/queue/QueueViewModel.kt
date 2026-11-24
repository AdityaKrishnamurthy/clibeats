package com.clibeats.presentation.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class QueueViewModel
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        val uiState: StateFlow<QueueUiState> =
            combine(
                playbackRepository.queueState,
                playbackRepository.playbackState,
            ) { queue, state ->
                if (queue.isEmpty()) {
                    QueueUiState.Empty
                } else {
                    QueueUiState.Success(
                        tracks = queue,
                        currentTrackId = state.currentTrack?.id,
                        currentIndex = state.currentIndex,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = QueueUiState.Empty,
            )

        fun onMove(
            from: Int,
            to: Int,
        ) {
            playbackRepository.moveTrackInQueue(from, to)
        }

        fun onRemove(index: Int) {
            playbackRepository.removeFromQueue(index)
        }

        /** UNDO after a removal: the song goes back where it was. */
        fun undoRemove(
            index: Int,
            track: Track,
        ) {
            playbackRepository.insertIntoQueue(index, track)
        }

        fun onClear() {
            playbackRepository.clearQueue()
        }

        fun onShuffle() {
            playbackRepository.toggleShuffle()
        }

        @Suppress("UnusedParameter")
        fun onTrackClick(
            track: Track,
            index: Int,
        ) {
            // Jump within the existing queue; re-setting the queue restarted every item.
            playbackRepository.skipToIndex(index)
        }
    }
