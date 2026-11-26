@file:Suppress("ForbiddenImport", "ktlint:standard:function-naming")

package com.clibeats.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.home.HomeFeedRepository
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.MusicProvider
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.domain.repository.PlaylistRepository
import com.clibeats.playback.StreamResolver
import com.clibeats.presentation.home.OpenedCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEBOUNCE_MS = 300L
private const val MIN_QUERY_LENGTH = 2
private const val LOCAL_ID = "local"
private const val WARM_TRACKS_COUNT = 3

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val providerRegistry: ProviderRegistry,
        private val appPreferences: AppPreferences,
        private val playbackRepository: PlaybackRepository,
        private val streamResolver: StreamResolver? = null,
        private val homeFeedRepository: HomeFeedRepository? = null,
        private val playlistRepository: PlaylistRepository? = null,
    ) : ViewModel() {
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        val providers: List<MusicProvider> = providerRegistry.providers

        val activeProviderId: StateFlow<String> =
            appPreferences.activeProviderId
                .map { it ?: ProviderRegistry.DEFAULT_PROVIDER_ID }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ProviderRegistry.DEFAULT_PROVIDER_ID)

        /** Online provider to return to when switching back from Local. */
        private var lastOnlineProviderId = ProviderRegistry.DEFAULT_PROVIDER_ID

        fun setActiveProvider(id: String) {
            if (id != LOCAL_ID) lastOnlineProviderId = id
            viewModelScope.launch { appPreferences.setActiveProviderId(id) }
        }

        /** Tapping the Search tab again: Online <-> Local (back to the online provider used last). */
        fun toggleSource() {
            val current = activeProviderId.value
            if (current == LOCAL_ID) {
                setActiveProvider(lastOnlineProviderId)
            } else {
                lastOnlineProviderId = current
                setActiveProvider(LOCAL_ID)
            }
        }

        /** Plays the tapped result with the rest of the results queued after it. */
        fun onPlay(
            tracks: List<Track>,
            index: Int,
        ) {
            viewModelScope.launch {
                appPreferences.addRecentSearchTrack(tracks[index])
                // Autoplay on: the song starts a fresh radio of related songs, in its own language and style,
                // instead of keeping the old radio. Autoplay off: the other results play after it.
                if (appPreferences.autoplayEnabled.first()) {
                    playbackRepository.playFrom(listOf(tracks[index]), 0, SOURCE)
                } else {
                    playbackRepository.playFrom(tracks, index, SOURCE)
                }
            }
        }

        val activeProviderName: StateFlow<String> =
            activeProviderId
                .map { id -> providerRegistry.getProvider(id)?.displayName ?: id }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000L),
                    initialValue = "",
                )

        @Suppress("OPT_IN_USAGE")
        val searchResults: StateFlow<SearchUiState> =
            _query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    if (q.length < MIN_QUERY_LENGTH) {
                        flowOf<SearchUiState>(SearchUiState.Idle)
                    } else {
                        activeProviderId.flatMapLatest { id ->
                            val provider = providerRegistry.getProvider(id) ?: providerRegistry.defaultProvider()
                            flow<SearchUiState> {
                                emit(SearchUiState.Loading)
                                when (val result = provider.search(q)) {
                                    is ProviderResult.Success -> {
                                        emit(SearchUiState.Success(result.data))
                                        streamResolver?.warmTracks(result.data, WARM_TRACKS_COUNT)
                                    }
                                    is ProviderResult.Error -> emit(SearchUiState.Error(result.message))
                                    is ProviderResult.Loading -> emit(SearchUiState.Loading)
                                }
                            }
                        }
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000L),
                    initialValue = SearchUiState.Idle,
                )

        fun onQueryChange(query: String) {
            _query.value = query
        }

        fun clearQuery() {
            _query.value = ""
        }

        // Anything done with a result (play, queue, play next, favorite, add to playlist) makes it a recent search.

        /** Lists [track] under recent searches (used for favorite / add to playlist from the results). */
        fun rememberPick(track: Track) {
            viewModelScope.launch { appPreferences.addRecentSearchTrack(track) }
        }

        /** Songs picked from search results, shown while the search box is empty. */
        val recentSearches: StateFlow<List<Track>> =
            appPreferences.recentSearchTracks
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

        fun clearRecentSearches() {
            viewModelScope.launch { appPreferences.clearRecentSearchTracks() }
        }

        /** Plays a song from Recent searches (the rest of the list follows only with auto-queue on). */
        fun onPlayRecent(
            tracks: List<Track>,
            index: Int,
        ) = onPlay(tracks, index)

        /**
         * Playlists matching the query on YouTube Music (shown in a row above the songs). Empty for Local and
         * other providers, for short queries, and when YouTube Music can't be reached.
         */
        @Suppress("OPT_IN_USAGE")
        val playlistResults: StateFlow<List<HomeItem.Collection>> =
            _query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    activeProviderId.flatMapLatest { id ->
                        val repo = homeFeedRepository
                        if (q.length < MIN_QUERY_LENGTH || repo == null || id != YOUTUBE_MUSIC_ID) {
                            flowOf(emptyList())
                        } else {
                            flow<List<HomeItem.Collection>> {
                                emit(emptyList())
                                emit(repo.searchPlaylists(q).orEmpty())
                            }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

        private val _opened = MutableStateFlow<OpenedCollection?>(null)

        /** A playlist opened from the results (shown over Search); null when none is open. */
        val opened: StateFlow<OpenedCollection?> = _opened.asStateFlow()
        private var openJob: Job? = null

        fun openPlaylist(item: HomeItem.Collection) {
            val repo = homeFeedRepository ?: return
            _opened.value = OpenedCollection(item)
            openJob?.cancel()
            // A Spotify Top 50 from Home: its songs are found on YouTube Music as it opens, shown as they come.
            if (item.browseId.startsWith(HomeFeedRepository.SPOTIFY_PREFIX)) {
                openJob =
                    viewModelScope.launch {
                        repo.spotifyChart(item.browseId.removePrefix(HomeFeedRepository.SPOTIFY_PREFIX)).collect { p ->
                            _opened.value =
                                OpenedCollection(
                                    item,
                                    tracks = p.tracks.takeIf { it.isNotEmpty() || p.done },
                                    failed = p.done && p.tracks.isEmpty(),
                                    progress = if (p.done) null else "finding songs ${p.tried}/${p.total}…",
                                )
                        }
                    }
                return
            }
            openJob =
                viewModelScope.launch {
                    val page = repo.collection(item.browseId)
                    _opened.value =
                        if (page == null) {
                            OpenedCollection(item, failed = true)
                        } else {
                            OpenedCollection(item, tracks = page.second, title = page.first.ifBlank { item.title })
                        }
                }
        }

        fun closePlaylist() {
            openJob?.cancel()
            _opened.value = null
        }

        /** Plays an opened playlist from [index]; "Playing from" shows its name. */
        fun playCollection(
            tracks: List<Track>,
            index: Int,
            source: String,
        ) {
            if (tracks.isNotEmpty()) playbackRepository.playFrom(tracks, index, source)
        }

        /** Saves an opened playlist as one of your playlists, under its name. */
        fun saveCollection(
            name: String,
            tracks: List<Track>,
        ) {
            val repo = playlistRepository ?: return
            viewModelScope.launch {
                runCatching {
                    val id = repo.createAppPlaylist(name, emptyList())
                    tracks.forEach { repo.addTrackToPlaylist(id, it) }
                }
            }
        }

        private companion object {
            const val SOURCE = "Search"
            const val YOUTUBE_MUSIC_ID = "youtube_music"
        }
    }
