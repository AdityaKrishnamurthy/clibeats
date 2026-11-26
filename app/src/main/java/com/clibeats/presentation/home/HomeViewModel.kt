@file:Suppress("ForbiddenImport", "TooManyFunctions")

package com.clibeats.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.home.HomeFeedRepository
import com.clibeats.data.home.HomeLanguage
import com.clibeats.data.home.LocalHome
import com.clibeats.data.home.LocalHomeRepository
import com.clibeats.data.home.LocalSetup
import com.clibeats.data.network.NetworkMonitor
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.HomeFeed
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeMood
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.MusicProvider
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.domain.repository.HistoryRepository
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.domain.repository.PlaylistRepository
import com.clibeats.playback.StreamResolver
import com.clibeats.presentation.playlist.PlaylistViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RECENT_SHOWN = 5
private const val LOCAL_ID = "local"
private const val YOUTUBE_ID = "youtube_music"

sealed interface FeedState {
    data object Loading : FeedState

    data class Loaded(
        val feed: HomeFeed,
    ) : FeedState

    data object MoodFailed : FeedState
}

data class OpenedCollection(
    val item: HomeItem.Collection,
    val tracks: List<Track>? = null,
    val title: String = item.title,
    val failed: Boolean = false,
    val progress: String? = null,
)

@Suppress("LongParameterList")
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val providerRegistry: ProviderRegistry,
        private val appPreferences: AppPreferences,
        private val networkMonitor: NetworkMonitor,
        private val streamResolver: StreamResolver? = null,
        private val historyRepository: HistoryRepository? = null,
        private val playbackRepository: PlaybackRepository? = null,
        private val homeFeedRepository: HomeFeedRepository? = null,
        private val playlistRepository: PlaylistRepository? = null,
        private val localHomeRepository: LocalHomeRepository? = null,
    ) : ViewModel() {
        private val coordinator =
            HomeFeedCoordinator(
                providerRegistry = providerRegistry,
                networkMonitor = networkMonitor,
                streamResolver = streamResolver,
                homeFeedRepository = homeFeedRepository,
                localHomeRepository = localHomeRepository,
                playlistRepository = playlistRepository,
            )

        val providers: List<MusicProvider> = providerRegistry.providers
        val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

        val activeProviderId: StateFlow<String> =
            appPreferences.activeProviderId
                .map { it ?: ProviderRegistry.DEFAULT_PROVIDER_ID }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ProviderRegistry.DEFAULT_PROVIDER_ID)

        @Suppress("OPT_IN_USAGE")
        val recentlyPlayed: StateFlow<List<Track>> =
            (
                historyRepository?.let { history ->
                    appPreferences.homeRecentsClearedAt.flatMapLatest { since ->
                        history.getRecentlyPlayedTracksSince(since, RECENT_SHOWN)
                    }
                } ?: flowOf(emptyList())
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

        val viewMode: StateFlow<PlaylistViewMode> =
            appPreferences.homeView
                .map { name -> PlaylistViewMode.entries.firstOrNull { it.name == name } ?: PlaylistViewMode.DETAILS }
                .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistViewMode.DETAILS)

        val activeProviderName: StateFlow<String> =
            activeProviderId
                .map { id -> (providerRegistry.getProvider(id) ?: providerRegistry.defaultProvider()).displayName }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), "")

        val uiState: StateFlow<HomeUiState> = coordinator.uiState
        val isRefreshing: StateFlow<Boolean> = coordinator.isRefreshing
        val feed: StateFlow<FeedState?> = coordinator.feed
        val selectedMood: StateFlow<HomeMood?> = coordinator.selectedMood
        val moods: StateFlow<List<HomeMood>> = coordinator.moods
        val local: StateFlow<LocalHome?> = coordinator.local
        val opened: StateFlow<OpenedCollection?> = coordinator.opened

        private val _languages = MutableStateFlow<Set<HomeLanguage>>(emptySet())
        val languages: StateFlow<Set<HomeLanguage>> = _languages.asStateFlow()

        val languagesAsked: StateFlow<Boolean> =
            appPreferences.homeLanguagesAsked.stateIn(viewModelScope, SharingStarted.Eagerly, true)

        val localSetup: StateFlow<LocalSetup> =
            (localHomeRepository?.setup ?: flowOf(LocalSetup()))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LocalSetup())

        private var lastOnlineProviderId = ProviderRegistry.DEFAULT_PROVIDER_ID
        private var shownProviderId: String? = null

        fun setViewMode(mode: PlaylistViewMode) {
            viewModelScope.launch { appPreferences.setHomeView(mode.name) }
        }

        fun setActiveProvider(id: String) {
            if (id != LOCAL_ID) lastOnlineProviderId = id
            viewModelScope.launch { appPreferences.setActiveProviderId(id) }
        }

        fun toggleSource() {
            val current = activeProviderId.value
            if (current == LOCAL_ID) {
                setActiveProvider(lastOnlineProviderId)
            } else {
                lastOnlineProviderId = current
                setActiveProvider(LOCAL_ID)
            }
        }

        fun playFromList(
            tracks: List<Track>,
            index: Int,
        ) {
            val repo = playbackRepository ?: return
            viewModelScope.launch {
                if (appPreferences.autoplayEnabled.first()) {
                    repo.playFrom(listOf(tracks[index]), 0, "Home")
                } else {
                    repo.playFrom(tracks, index, "Home")
                }
            }
        }

        fun clearRecentlyPlayed() {
            viewModelScope.launch { appPreferences.setHomeRecentsClearedAt(System.currentTimeMillis()) }
        }

        fun refreshTrending() {
            val id = shownProviderId ?: return
            when {
                id == YOUTUBE_ID && homeFeedRepository != null ->
                    coordinator.showFeed(viewModelScope, _languages.value, force = true)
                id == LOCAL_ID && localHomeRepository != null ->
                    coordinator.showLocal(viewModelScope, reshuffle = true) { shownProviderId == LOCAL_ID }
                else -> coordinator.showTrending(viewModelScope, id, force = true)
            }
        }

        fun setLanguages(picked: Set<HomeLanguage>) {
            viewModelScope.launch { appPreferences.setHomeLanguages(picked.mapTo(HashSet()) { it.name }) }
        }

        fun selectMood(mood: HomeMood) {
            if (shownProviderId != YOUTUBE_ID) return
            coordinator.toggleMood(viewModelScope, mood, _languages.value)
        }

        fun addLocalFolder(treeUri: String) {
            val repo = localHomeRepository ?: return
            viewModelScope.launch {
                repo.addFolder(treeUri)
                if (shownProviderId == LOCAL_ID) {
                    coordinator.showLocal(viewModelScope, reshuffle = false) { shownProviderId == LOCAL_ID }
                }
            }
        }

        fun shuffleAll() {
            val songs = local.value?.allSongs.orEmpty()
            if (songs.isNotEmpty()) playCollection(songs.shuffled(), 0, "Shuffle all")
        }

        fun surpriseAlbum() {
            val album = local.value?.albums?.randomOrNull() ?: return
            playCollection(album, 0, album.first().album)
        }

        fun playFromRow(
            row: String,
            tracks: List<Track>,
            index: Int,
        ) {
            if (shownProviderId == LOCAL_ID && local.value != null) {
                playCollection(tracks, index, row)
            } else {
                playFromList(tracks, index)
            }
        }

        fun openCollection(item: HomeItem.Collection) = coordinator.openCollection(viewModelScope, item)

        fun closeCollection() = coordinator.closeCollection()

        fun playCollection(
            tracks: List<Track>,
            index: Int,
            source: String,
        ) {
            if (tracks.isNotEmpty()) playbackRepository?.playFrom(tracks, index, source)
        }

        fun saveCollection(
            name: String,
            tracks: List<Track>,
        ) {
            viewModelScope.launch { coordinator.saveCollection(name, tracks) }
        }

        init {
            localHomeRepository?.let { repo ->
                viewModelScope.launch {
                    repo.onlineClears.drop(1).collect {
                        coordinator.rebuildYourRows(applyToVisible = shownProviderId == YOUTUBE_ID)
                    }
                }
            }
            viewModelScope.launch {
                _languages.value = HomeLanguage.fromNames(appPreferences.homeLanguages.first())
                launch {
                    appPreferences.homeLanguages.map { HomeLanguage.fromNames(it) }.collect { picked ->
                        if (picked != _languages.value) {
                            _languages.value = picked
                            if (shownProviderId == YOUTUBE_ID && homeFeedRepository != null) {
                                coordinator.showFeed(viewModelScope, picked, force = false)
                            }
                        }
                    }
                }
                activeProviderId.collect { id ->
                    shownProviderId = id
                    coordinator.clearLocal()
                    when {
                        id == YOUTUBE_ID && homeFeedRepository != null ->
                            coordinator.showFeed(viewModelScope, _languages.value, force = false)
                        id == LOCAL_ID && localHomeRepository != null ->
                            coordinator.showLocal(viewModelScope, reshuffle = false) { shownProviderId == LOCAL_ID }
                        else -> {
                            coordinator.clearFeed()
                            coordinator.showTrending(viewModelScope, id, force = false)
                        }
                    }
                }
            }
        }
    }
