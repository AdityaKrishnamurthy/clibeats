package com.clibeats.presentation.component

import com.clibeats.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A song swiped with "Add to playlist": the app shows the playlist picker for it. */
@Singleton
class PlaylistPicks
    @Inject
    constructor() {
        private val _requested = MutableStateFlow<Track?>(null)
        val requested: StateFlow<Track?> = _requested.asStateFlow()

        fun request(track: Track) {
            _requested.value = track
        }

        fun done() {
            _requested.value = null
        }
    }
