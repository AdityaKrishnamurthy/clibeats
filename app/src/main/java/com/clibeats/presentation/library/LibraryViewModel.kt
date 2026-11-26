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

        private val _sort = MutableStateFlow(GroupSort.MOST_PLAYED)

        /** How Artists and Albums are ordered (most played first by default). */
        val sort: StateFlow<GroupSort> = _sort.asStateFlow()

        fun setSort(sort: GroupSort) {
            _sort.value = sort
        }

        /** Artists / Albums layout (grids, list, details); remembered across launches. */
        val groupView: StateFlow<PlaylistViewMode> =
            (appPreferences?.libraryGroupView ?: flowOf(null))
                .map { name -> name?.let(PlaylistViewMode::fromName) ?: PlaylistViewMode.GRID_LARGE }
                .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistViewMode.GRID_LARGE)

        fun setGroupView(mode: PlaylistViewMode) {
            viewModelScope.launch { appPreferences?.setLibraryGroupView(mode.name) }
        }

        private val _opened = MutableStateFlow<OpenedGroup?>(null)

        /** The artist or album open over the list; null when none is. */
        val opened: StateFlow<OpenedGroup?> = _opened.asStateFlow()

        fun open(group: OpenedGroup?) {
            _opened.value = group
        }

        private val playCounts = historyRepository?.getPlayCounts() ?: flowOf(emptyMap())

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
                }.let { tracksFlow -> combine(tracksFlow, playCounts, _sort, ::Triple) }
                .map { (tracks, counts, sort) ->
                    if (tracks.isEmpty()) {
                        LibraryUiState.Empty
                    } else {
                        LibraryUiState.Success(
                            tracks = tracks,
                            artists = buildArtists(tracks, counts, sort),
                            albums = buildAlbums(tracks, counts, sort),
                        )
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
    }
