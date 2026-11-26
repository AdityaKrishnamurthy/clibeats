@file:Suppress("ktlint:standard:property-naming", "PropertyName", "ForbiddenImport", "TooManyFunctions")

package com.clibeats.presentation.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.download.TrackDownloadManager
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.HistoryRepository
import com.clibeats.domain.repository.LibraryRepository
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistViewModel
    @Inject
    constructor(
        private val playlistRepository: PlaylistRepository,
        private val playbackRepository: PlaybackRepository,
        private val libraryRepository: LibraryRepository? = null,
        private val historyRepository: HistoryRepository? = null,
        private val appPreferences: AppPreferences? = null,
        private val downloadManager: TrackDownloadManager? = null,
    ) : ViewModel() {
        private val _selectedPlaylistId = MutableStateFlow<String?>(null)

        private val favorites: Flow<List<Track>> =
            libraryRepository?.getLikedSongs() ?: flowOf(emptyList())

        /** Songs still downloading come first (greyed out in the list), then the finished ones. */
        private val downloadedSongs: Flow<List<Track>> =
            downloadManager?.let { manager ->
                combine(manager.getDownloadedSongs(), manager.pendingTracks) { done, waiting ->
                    val doneIds = done.mapTo(HashSet()) { it.id }
                    waiting.filterNot { it.id in doneIds } + done
                }
            } ?: flowOf(emptyList())

        // Most Played has its own clear / per-song reset times; the listening history itself is kept.
        @OptIn(ExperimentalCoroutinesApi::class)
        private val mostPlayed: Flow<List<Track>> =
            when {
                historyRepository == null -> flowOf(emptyList())
                appPreferences == null -> historyRepository.getMostPlayedTracks(MOST_PLAYED_LIMIT)
                else ->
                    combine(appPreferences.mostPlayedClearedAt, appPreferences.mostPlayedSongResets, ::Pair)
                        .flatMapLatest { (since, resets) ->
                            historyRepository.getMostPlayedTracksSince(since, resets, MOST_PLAYED_LIMIT)
                        }
            }

        private data class InputsPart(
            val playlists: List<Playlist>,
            val selectedId: String?,
            val favorites: List<Track>,
            val mostPlayed: List<Track>,
            val downloaded: List<Track>,
        )

        private data class Inputs(
            val playlists: List<Playlist>,
            val selectedId: String?,
            val favorites: List<Track>,
            val mostPlayed: List<Track>,
            val downloaded: List<Track>,
            val covers: Map<String, String?>,
        )

        // Selection is part of the combine so opening a playlist updates immediately.
        val uiState: StateFlow<PlaylistUiState> =
            combine(
                combine(
                    playlistRepository.getAllPlaylistsAsFlow(),
                    _selectedPlaylistId,
                    favorites,
                    mostPlayed,
                    downloadedSongs,
                    ::InputsPart,
                ),
                playlistRepository.getPlaylistCoversAsFlow(),
            ) { part, covers ->
                Inputs(
                    playlists = part.playlists,
                    selectedId = part.selectedId,
                    favorites = part.favorites,
                    mostPlayed = part.mostPlayed,
                    downloaded = part.downloaded,
                    covers = covers,
                )
            }.flatMapLatest { input ->
                val base =
                    PlaylistUiState.Success(
                        playlists = input.playlists,
                        favoritesCount = input.favorites.size,
                        mostPlayedCount = input.mostPlayed.size,
                        downloadedCount = input.downloaded.size,
                        favoritesCover = input.favorites.firstNotNullOfOrNull { it.artworkUrl },
                        mostPlayedCover = input.mostPlayed.firstNotNullOfOrNull { it.artworkUrl },
                        downloadedCover = input.downloaded.firstNotNullOfOrNull { it.artworkUrl },
                        covers = input.covers,
                    )
                when (input.selectedId) {
                    null -> flowOf(base)
                    FAVORITES_ID -> flowOf(base.copy(selectedPlaylistTracks = input.favorites, isFavoritesOpen = true))
                    MOST_PLAYED_ID ->
                        flowOf(base.copy(selectedPlaylistTracks = input.mostPlayed, isMostPlayedOpen = true))
                    DOWNLOADED_ID ->
                        flowOf(base.copy(selectedPlaylistTracks = input.downloaded, isDownloadedOpen = true))
                    else ->
                        playlistRepository.getSongsForPlaylistAsFlow(input.selectedId).map { songs ->
                            base.copy(
                                selectedPlaylist = input.playlists.find { it.id == input.selectedId },
                                selectedPlaylistTracks = songs,
                            )
                        }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = PlaylistUiState.Loading,
            )

        /** How the Playlists overview is shown (grid sizes, list, details); remembered across launches. */
        val viewMode: StateFlow<PlaylistViewMode> =
            (appPreferences?.playlistView ?: flowOf(null))
                .map(PlaylistViewMode::fromName)
                .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistViewMode.DEFAULT)

        fun setViewMode(mode: PlaylistViewMode) {
            viewModelScope.launch { appPreferences?.setPlaylistView(mode.name) }
        }

        fun createPlaylist(
            name: String,
            description: String?,
        ) {
            if (name.isBlank()) return
            viewModelScope.launch {
                val newPlaylist =
                    Playlist(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        description = description?.trim(),
                        artworkUrl = null,
                        trackCount = 0,
                        isOwned = true,
                        providerId = "local",
                    )
                playlistRepository.upsertPlaylist(newPlaylist)
            }
        }

        fun deletePlaylist(playlistId: String) {
            viewModelScope.launch {
                if (_selectedPlaylistId.value == playlistId) {
                    _selectedPlaylistId.value = null
                }
                playlistRepository.deletePlaylist(playlistId)
            }
        }

        /** True while a playlist (or Favorites / Most Played) is open rather than the playlist list. */
        val isPlaylistOpen: Boolean get() = _selectedPlaylistId.value != null

        fun selectPlaylist(playlistId: String?) {
            _selectedPlaylistId.value = playlistId
        }

        fun openFavorites() {
            _selectedPlaylistId.value = FAVORITES_ID
        }

        fun openMostPlayed() {
            _selectedPlaylistId.value = MOST_PLAYED_ID
        }

        fun removeSongFromPlaylist(
            playlistId: String,
            songId: String,
        ) {
            viewModelScope.launch {
                playlistRepository.removeSongFromPlaylist(playlistId, songId)
            }
        }

        /** Saves the order the user dragged ♥ Favorites into (top first). */
        fun reorderFavorites(tracks: List<Track>) {
            viewModelScope.launch { libraryRepository?.reorderFavorites(tracks.map { it.id }) }
        }

        /** Saves the order the user dragged an app playlist into. */
        fun reorderPlaylist(
            playlistId: String,
            tracks: List<Track>,
        ) {
            viewModelScope.launch { playlistRepository.reorderPlaylistSongs(playlistId, tracks.map { it.id }) }
        }

        /** Takes [track] off Most Played; it comes back only if it's played often again. */
        fun removeFromMostPlayed(track: Track) {
            viewModelScope.launch { appPreferences?.resetMostPlayedSong(track.id, System.currentTimeMillis()) }
        }

        fun clearMostPlayed() {
            viewModelScope.launch { appPreferences?.clearMostPlayed(System.currentTimeMillis()) }
        }

        fun clearFavorites() {
            viewModelScope.launch { libraryRepository?.clearFavorites() }
        }

        /** Removes [track] from ♥ Favorites (it is currently liked, so toggling unlikes it). */
        fun removeFromFavorites(track: Track) {
            viewModelScope.launch { libraryRepository?.toggleLike(track) }
        }

        /** Copies a read-only local file playlist into a new editable app playlist and opens it. */
        fun saveAsAppPlaylist(
            source: Playlist,
            tracks: List<Track>,
        ) {
            viewModelScope.launch {
                val id = playlistRepository.createAppPlaylist(source.name, tracks.map { it.id })
                _selectedPlaylistId.value = id
            }
        }

        /** Plays [tracks] from [startIndex]; [source] (the playlist's name) shows as "Playing from …". */
        fun playPlaylist(
            tracks: List<Track>,
            startIndex: Int = 0,
            source: String? = null,
        ) {
            if (tracks.isEmpty()) return
            if (source != null) {
                playbackRepository.playFrom(tracks, startIndex, source)
            } else {
                playbackRepository.setQueue(tracks, startIndex)
            }
        }

        /**
         * The play button on a playlist cover: plays the whole playlist from the first song without opening it.
         * [playlist] is null for the pinned lists, picked by [pinned] ("fav", "most", "downloaded").
         */
        fun playNow(
            playlist: Playlist?,
            pinned: String? = null,
        ) {
            viewModelScope.launch {
                val (tracks, source) =
                    when {
                        playlist != null ->
                            playlistRepository.getSongsForPlaylistAsFlow(playlist.id).first() to playlist.name
                        pinned == PINNED_FAVORITES -> favorites.first() to "♥ Favorites"
                        pinned == PINNED_MOST_PLAYED -> mostPlayed.first() to "Most Played"
                        // Only finished downloads: songs still waiting in the queue aren't on the phone yet.
                        pinned == PINNED_DOWNLOADED ->
                            (downloadManager?.getDownloadedSongs()?.first() ?: emptyList()) to "Downloaded"
                        else -> return@launch
                    }
                playPlaylist(tracks, 0, source)
            }
        }

        /** Renames one of your playlists (blank names are ignored). Its songs and cover are untouched. */
        fun renamePlaylist(
            playlist: Playlist,
            name: String,
        ) {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed == playlist.name) return
            viewModelScope.launch { playlistRepository.upsertPlaylist(playlist.copy(name = trimmed)) }
        }

        /**
         * Sets a custom cover image for [playlistId].
         * Pass [artworkUrl] = null to clear the custom cover and revert to the first-song thumbnail.
         */
        fun updatePlaylistArtwork(
            playlistId: String,
            artworkUrl: String?,
        ) {
            viewModelScope.launch {
                playlistRepository.updatePlaylistArtwork(playlistId, artworkUrl)
            }
        }

        /** Copies an image picked on the device into app storage and uses it as the cover. */
        fun setPlaylistCoverImage(
            playlistId: String,
            imageUri: String,
        ) {
            viewModelScope.launch {
                playlistRepository.setPlaylistCoverImage(playlistId, imageUri)
            }
        }

        fun openDownloaded() {
            _selectedPlaylistId.value = DOWNLOADED_ID
        }

        fun removeFromDownloaded(track: Track) {
            viewModelScope.launch {
                downloadManager?.deleteDownload(track.id)
            }
        }

        fun clearAllDownloaded() {
            viewModelScope.launch {
                downloadManager?.deleteAllDownloads()
            }
        }

        companion object {
            private const val FAVORITES_ID = "__favorites__"
            private const val MOST_PLAYED_ID = "__most_played__"
            private const val DOWNLOADED_ID = "__downloaded__"
            private const val MOST_PLAYED_LIMIT = 50

            /** Keys of the pinned lists, for [playNow]. */
            const val PINNED_FAVORITES = "fav"
            const val PINNED_MOST_PLAYED = "most"
            const val PINNED_DOWNLOADED = "downloaded"
        }
    }
