package com.clibeats.presentation.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared by every song list: swipe left on a row adds the song to ♥ Favorites. */
@HiltViewModel
class FavoritesViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
    ) : ViewModel() {
        fun add(track: Track) {
            viewModelScope.launch { runCatching { libraryRepository.addToFavorites(track) } }
        }
    }
