@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.HistoryRepository
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.domain.repository.SongRepository
import com.clibeats.presentation.playlist.PlaylistViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Library sections, in the order of the switch at the top. PLAYLISTS = the playlists screen; ONLINE = songs
 * you actually played from online providers; LOCAL = scanned device music.
 */
enum class LibraryMode { PLAYLISTS, ONLINE, LOCAL }

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val songRepository: SongRepository,
        private val playbackRepository: PlaybackRepository,
        private val appPreferences: AppPreferences? = null,
        private val historyRepository: HistoryRepository? = null,
    ) : ViewModel() {
        private val _mode = MutableStateFlow(LibraryMode.ONLINE)
        val mode: StateFlow<LibraryMode> = _mode.asStateFlow()

        init {
            viewModelScope.launch {
                appPreferences?.librarySection?.first()
                    // "OFFLINE" was the Local section's name before it was renamed.
                    ?.let { saved -> if (saved == "OFFLINE") LibraryMode.LOCAL.name else saved }
                    ?.let { saved -> LibraryMode.entries.firstOrNull { it.name == saved } }
                    ?.let { _mode.value = it }
            }
        }

        val localFolderChosen: StateFlow<Boolean> =
            (appPreferences?.localMusicTreeUris ?: flowOf(emptyList()))
                .map { it.isNotEmpty() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), true)

        private fun sortFlow(saved: kotlinx.coroutines.flow.Flow<String?>?): StateFlow<GroupSort> =
            (saved ?: flowOf(null))
                .map { name -> GroupSort.entries.firstOrNull { it.name == name } ?: GroupSort.RECENT }
                .stateIn(viewModelScope, SharingStarted.Eagerly, GroupSort.RECENT)

        /** How Artists are ordered (recently played first unless changed; remembered). */
        val artistSort: StateFlow<GroupSort> = sortFlow(appPreferences?.libraryArtistSort)

        /** How Albums are ordered (recently played first unless changed; remembered separately). */
        val albumSort: StateFlow<GroupSort> = sortFlow(appPreferences?.libraryAlbumSort)

        fun setArtistSort(sort: GroupSort) {
            viewModelScope.launch { appPreferences?.setLibrarySort(albums = false, sort.name) }
        }

        fun setAlbumSort(sort: GroupSort) {
            viewModelScope.launch { appPreferences?.setLibrarySort(albums = true, sort.name) }
        }

        /** Artists / Albums layout (grids, list, details); remembered across launches. */
        val groupView: StateFlow<PlaylistViewMode> =
            (appPreferences?.libraryGroupView ?: flowOf(null))
                .map { name -> name?.let(PlaylistViewMode::fromName) ?: PlaylistViewMode.GRID_LARGE }
                .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistViewMode.GRID_LARGE)

        fun setGroupView(mode: PlaylistViewMode) {
            viewModelScope.launch { appPreferences?.setLibraryGroupView(mode.name) }
        }

        private val _tab = MutableStateFlow(TAB_TRACKS)

        /**
         * Tracks (0), Artists (1) or Albums (2). Kept here, not in the screen, so it's still Artists after
         * opening an artist and coming back.
         */
        val tab: StateFlow<Int> = _tab.asStateFlow()

        fun setTab(index: Int) {
            _tab.value = index
        }

        private val _opened = MutableStateFlow<OpenedGroup?>(null)

        /** The artist or album open over the list; null when none is. */
        val opened: StateFlow<OpenedGroup?> = _opened.asStateFlow()

        fun open(group: OpenedGroup?) {
            _opened.value = group
        }

        private val playCounts = historyRepository?.getPlayCounts() ?: flowOf(emptyMap())
        private val lastPlayed = historyRepository?.getLastPlayed() ?: flowOf(emptyMap())

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<LibraryUiState> =
            _mode
                .flatMapLatest { mode ->
                    when (mode) {
                        LibraryMode.ONLINE ->
                            (appPreferences?.libraryHistoryClearedAt ?: flowOf(0L)).flatMapLatest { since ->
                                songRepository.getPlayedOnlineTracksAsFlow(since)
                            }
                        LibraryMode.LOCAL -> songRepository.getLocalTracksAsFlow()
                        LibraryMode.PLAYLISTS -> flowOf(emptyList()) // shown by PlaylistScreen
                    }
                }.let { tracksFlow ->
                    combine(
                        tracksFlow,
                        playCounts,
                        lastPlayed,
                        artistSort,
                        albumSort,
                    ) { tracks, counts, last, aSort, alSort ->
                        if (tracks.isEmpty()) {
                            LibraryUiState.Empty
                        } else {
                            LibraryUiState.Success(
                                tracks = tracks,
                                artists = buildArtists(tracks, counts, aSort, last),
                                albums = buildAlbums(tracks, counts, alSort, last),
                            )
                        }
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000L),
                    initialValue = LibraryUiState.Loading,
                )

        /** Clears the Library's played list only; Home "Recently played" keeps its own. */
        fun clearPlayedHistory() {
            viewModelScope.launch { appPreferences?.setLibraryHistoryClearedAt(System.currentTimeMillis()) }
        }

        fun setMode(mode: LibraryMode) {
            _mode.value = mode
            _opened.value = null
            viewModelScope.launch { appPreferences?.setLibrarySection(mode.name) }
        }

        /** Re-tapping the Library tab: the next section along (Playlists → Online → Local → Playlists). */
        fun nextMode() {
            val all = LibraryMode.entries
            setMode(all[(all.indexOf(_mode.value) + 1) % all.size])
        }

        /** Plays an artist's or album's songs from [index]; "Playing from" shows its name. */
        fun playGroup(
            tracks: List<Track>,
            index: Int,
            source: String,
        ) {
            if (tracks.isNotEmpty()) playbackRepository.playFrom(tracks, index, source)
        }

        /**
         * Back (or tapping the Library tab again), one step at a time: an open artist / album → its list;
         * Artists / Albums → Tracks. False when there's nothing to step back from here.
         */
        fun stepBack(): Boolean =
            when {
                _opened.value != null -> {
                    _opened.value = null
                    true
                }
                _mode.value != LibraryMode.PLAYLISTS && _tab.value != TAB_TRACKS -> {
                    _tab.value = TAB_TRACKS
                    true
                }
                else -> false
            }

        @Suppress("UnusedParameter")
        fun onTrackClick(
            track: Track,
            tracks: List<Track>,
            index: Int,
        ) {
            viewModelScope.launch {
                // Autoplay on: the song starts a fresh radio of related songs (the old radio is dropped).
                // Autoplay off: the rest of the list plays after it, so the music doesn't stop.
                val radio = appPreferences?.autoplayEnabled?.first() ?: true
                if (radio) {
                    playbackRepository.playFrom(listOf(track), 0, "Library")
                } else {
                    playbackRepository.playFrom(tracks, index, "Library")
                }
            }
        }

        companion object {
            const val TAB_TRACKS = 0
        }
    }
