@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.download.DownloadStatus
import com.clibeats.data.download.TrackDownloadManager
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.LibraryRepository
import com.clibeats.domain.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        private val downloadManager: TrackDownloadManager,
        private val notices: Notices,
        private val appPreferences: com.clibeats.data.preferences.AppPreferences? = null,
        private val playlistPicks: PlaylistPicks? = null,
    ) : ViewModel() {
        private val gestures =
            (appPreferences?.gestures ?: kotlinx.coroutines.flow.flowOf(com.clibeats.domain.model.GestureSettings()))
                .stateIn(viewModelScope, SharingStarted.Eagerly, com.clibeats.domain.model.GestureSettings())

        /** Swiping a song right: whatever Settings → Gestures says (add to queue by default). */
        fun swipeRight(track: Track) = perform(gestures.value.songSwipeRight, track)

        /** Swiping a song left: whatever Settings → Gestures says (add to ♥ Favorites by default). */
        fun swipeLeft(track: Track) = perform(gestures.value.songSwipeLeft, track)

        fun perform(
            action: com.clibeats.domain.model.SwipeAction,
            track: Track,
        ) {
            when (action) {
                com.clibeats.domain.model.SwipeAction.QUEUE -> addToQueue(track)
                com.clibeats.domain.model.SwipeAction.PLAY_NEXT -> playNext(track)
                com.clibeats.domain.model.SwipeAction.FAVORITE -> favorite(track)
                com.clibeats.domain.model.SwipeAction.ADD_TO_PLAYLIST -> playlistPicks?.request(track)
                com.clibeats.domain.model.SwipeAction.REMOVE, com.clibeats.domain.model.SwipeAction.OFF -> Unit
            }
        }

        val downloadedTrackIds: StateFlow<Set<String>> = downloadManager.downloadedTrackIds
        val downloads: StateFlow<Map<String, DownloadStatus>> = downloadManager.downloads

        /** Songs queued or downloading (shown greyed out in Downloaded until done). */
        val pendingTrackIds: StateFlow<Set<String>> =
            downloadManager.pendingTracks
                .map { list -> list.mapTo(HashSet()) { it.id } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        /** The song playing now, so every list can highlight it. */
        val playingTrackId: StateFlow<String?> =
            playbackRepository.playbackState
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** Selected songs: download the ones not downloaded yet (they show in Downloaded at once, greyed out). */
        fun downloadAll(tracks: List<Track>) {
            val have = downloadedTrackIds.value
            val todo = tracks.filter { it.id !in have && it.providerId != "local" }
            if (todo.isEmpty()) {
                notices.show("Already downloaded")
                return
            }
            downloadManager.downloadAll(todo)
            notices.show("Downloading ${todo.size} ${if (todo.size == 1) "song" else "songs"}")
        }

        /** Selected songs, in order, after the songs already queued. */
        fun addAllToQueue(tracks: List<Track>) {
            tracks.forEach(playbackRepository::addToQueue)
            notices.show("Added ${tracks.size} to queue")
        }

        /** Selected songs: right after the song playing now, keeping their order. */
        fun playAllNext(tracks: List<Track>) {
            // Each one goes straight after the current song, so add them last to first.
            tracks.asReversed().forEach(playbackRepository::playNext)
            notices.show(if (tracks.size == 1) "Playing next" else "Playing ${tracks.size} songs next")
        }

        /** Selected songs: add all to ♥ Favorites. */
        fun favoriteAll(tracks: List<Track>) {
            viewModelScope.launch {
                tracks.forEach { runCatching { libraryRepository.addToFavorites(it) } }
                notices.show("♥ Added ${tracks.size} to favorites")
            }
        }

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

        fun download(track: Track) {
            downloadManager.downloadTrack(track)
            notices.show("Downloading \u201c${track.title}\u201d")
        }

        fun removeDownload(track: Track) {
            viewModelScope.launch {
                downloadManager.deleteDownload(track.id)
                notices.show("Removed download")
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
