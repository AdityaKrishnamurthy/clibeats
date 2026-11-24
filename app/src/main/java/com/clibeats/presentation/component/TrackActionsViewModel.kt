package com.clibeats.presentation.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.LibraryRepository
import com.clibeats.domain.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared by every song list: swipe right adds to the queue, swipe left adds to ♥ Favorites, and the
 * hold menu's actions. Each one posts a short confirmation above the player bar.
 */
@HiltViewModel
class TrackActionsViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val playbackRepository: PlaybackRepository,
        private val notices: Notices,
    ) : ViewModel() {
        fun favorite(track: Track) {
            viewModelScope.launch {
                runCatching { libraryRepository.addToFavorites(track) }
                    .onSuccess { notices.show("♥ Added to favorites") }
            }
        }

        fun addToQueue(track: Track) {
            playbackRepository.addToQueue(track)
            notices.show("Added to queue")
        }

        fun playNext(track: Track) {
            playbackRepository.playNext(track)
            notices.show("Playing next")
        }
    }
