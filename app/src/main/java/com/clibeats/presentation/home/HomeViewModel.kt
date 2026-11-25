@file:Suppress("ForbiddenImport", "ktlint:standard:function-naming")

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
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.repository.HistoryRepository
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.domain.repository.PlaylistRepository
import com.clibeats.playback.StreamResolver
import com.clibeats.presentation.playlist.PlaylistViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val TRENDING_LIMIT = 25
private const val TRENDING_RETRIES = 2
private const val RETRY_DELAY_MS = 1_500L
private const val NETWORK_WAIT_MS = 15_000L
private const val RECENT_SHOWN = 5
private const val LOCAL_ID = "local"
private const val WARM_TRACKS_COUNT = 3
private const val YOUTUBE_ID = "youtube_music"

/** The online home page. */
sealed interface FeedState {
    data object Loading : FeedState

    data class Loaded(
        val feed: HomeFeed,
    ) : FeedState

    /** A mood's feed couldn't be loaded (the chips stay, so another can be picked). */
    data object MoodFailed : FeedState
}

/** A playlist, album or artist opened from Home: [tracks] is null while it loads. */
data class OpenedCollection(
    val item: HomeItem.Collection,
    val tracks: List<Track>? = null,
    val title: String = item.title,
    val failed: Boolean = false,
    /** While a Spotify chart's songs are still being found ("finding songs 12/50…"). */
    val progress: String? = null,
)

@Suppress("LongParameterList", "LargeClass", "TooManyFunctions")
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
        /** YouTube Music's home feed; null (tests) keeps the plain Trending list. */
        private val homeFeedRepository: HomeFeedRepository? = null,
        private val playlistRepository: PlaylistRepository? = null,
        /** Local Home (mixes, albums, artists from your library); null (tests) keeps the plain song list. */
        private val localHomeRepository: LocalHomeRepository? = null,
    ) : ViewModel() {
        val providers: List<MusicProvider> = providerRegistry.providers

        val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

        val activeProviderId: StateFlow<String> =
            appPreferences.activeProviderId
                .map { it ?: ProviderRegistry.DEFAULT_PROVIDER_ID }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ProviderRegistry.DEFAULT_PROVIDER_ID)

        val recentlyPlayed: StateFlow<List<Track>> =
            // Home's [ CLEAR ] hides plays up to that moment without touching the Library's history.
            (
                historyRepository?.let { history ->
                    appPreferences.homeRecentsClearedAt.flatMapLatest { since ->
                        history.getRecentlyPlayedTracksSince(since, RECENT_SHOWN)
                    }
                } ?: flowOf(emptyList())
            )
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

        /** How Home lists songs (grids, list, details); remembered across launches. Details = bigger covers. */
        val viewMode: StateFlow<PlaylistViewMode> =
            appPreferences.homeView
                .map { name -> PlaylistViewMode.entries.firstOrNull { it.name == name } ?: PlaylistViewMode.DETAILS }
                .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistViewMode.DETAILS)

        fun setViewMode(mode: PlaylistViewMode) {
            viewModelScope.launch { appPreferences.setHomeView(mode.name) }
        }

        /** Online provider to return to when switching back from Local. */
        private var lastOnlineProviderId = ProviderRegistry.DEFAULT_PROVIDER_ID

        fun setActiveProvider(id: String) {
            if (id != LOCAL_ID) lastOnlineProviderId = id
            viewModelScope.launch { appPreferences.setActiveProviderId(id) }
        }

        /** Re-tapping the Home tab: Online <-> Local (back to the online provider used last). */
        fun toggleSource() {
            val current = activeProviderId.value
            if (current == LOCAL_ID) {
                setActiveProvider(lastOnlineProviderId)
            } else {
                lastOnlineProviderId = current
                setActiveProvider(LOCAL_ID)
            }
        }

        /** Plays [tracks] from [index]; queues the rest only if "auto-queue related" is on. */
        fun playFromList(
            tracks: List<Track>,
            index: Int,
        ) {
            val repo = playbackRepository ?: return
            viewModelScope.launch {
                // Autoplay on: the song starts a fresh radio of related songs (the old radio is dropped).
                // Autoplay off: the rest of the list plays after it, so the music doesn't stop.
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

        private val activeProvider: kotlinx.coroutines.flow.Flow<MusicProvider> =
            activeProviderId.map { id ->
                providerRegistry.getProvider(id) ?: providerRegistry.defaultProvider()
            }

        val activeProviderName: StateFlow<String> =
            activeProvider
                .map { it.displayName }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000L),
                    initialValue = "",
                )

        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)

        /**
         * Trending for the active source. Loaded once per source while the app is open (this ViewModel lives as
         * long as the screen does), so switching tabs never reloads it; [refreshTrending] reloads on request.
         */
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)

        /** True while [refreshTrending] reloads a list that's already shown. */
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val trendingCache = mutableMapOf<String, List<Track>>()
        private var shownProviderId: String? = null
        private var loadJob: Job? = null

        /** The reload button: fetches the home feed (or the active source's trending) again. */
        fun refreshTrending() {
            val id = shownProviderId ?: return
            when {
                id == YOUTUBE_ID && homeFeedRepository != null -> showFeed(force = true)
                id == LOCAL_ID && localHomeRepository != null -> showLocal(reshuffle = true)
                else -> showTrending(id, force = true)
            }
        }

        // ── YouTube Music home feed ──

        private val _feed = MutableStateFlow<FeedState?>(null)

        /** The online home page; null while Home shows the plain Trending list (Local, or the feed failed). */
        val feed: StateFlow<FeedState?> = _feed.asStateFlow()

        private val _selectedMood = MutableStateFlow<HomeMood?>(null)

        /** The mood chip that filters the feed, if any. */
        val selectedMood: StateFlow<HomeMood?> = _selectedMood.asStateFlow()

        /** The chips from the unfiltered feed; they stay put while a mood is picked. */
        private val _moods = MutableStateFlow<List<HomeMood>>(emptyList())
        val moods: StateFlow<List<HomeMood>> = _moods.asStateFlow()

        /** Languages the online Home is tuned to; empty = every language. */
        private val _languages = MutableStateFlow<Set<HomeLanguage>>(emptySet())
        val languages: StateFlow<Set<HomeLanguage>> = _languages.asStateFlow()

        /** False until the language picker was answered once; Home suggests it until then. */
        val languagesAsked: StateFlow<Boolean> =
            appPreferences.homeLanguagesAsked.stateIn(viewModelScope, SharingStarted.Eagerly, true)

        /** Saves the picked languages (empty = every language) and reloads Home for them. */
        fun setLanguages(picked: Set<HomeLanguage>) {
            viewModelScope.launch { appPreferences.setHomeLanguages(picked.mapTo(HashSet()) { it.name }) }
        }

        private val feedCache = mutableMapOf<String, HomeFeed>()

        private fun feedKey(params: String?): String {
            val picked = languages.value.map { it.name }.sorted().joinToString(",")
            return "${params.orEmpty()}|$picked"
        }

        private var feedJob: Job? = null

        /** Picks a mood chip; tapping the picked one again goes back to the full feed. */
        fun selectMood(mood: HomeMood) {
            // Moods filter the online page only.
            if (shownProviderId != YOUTUBE_ID) return
            _selectedMood.value = if (_selectedMood.value == mood) null else mood
            showFeed(force = false)
        }

        private fun showFeed(force: Boolean) {
            val repo = homeFeedRepository ?: return
            localJob?.cancel()
            val params = _selectedMood.value?.params
            val key = feedKey(params)
            val picked = languages.value
            feedJob?.cancel()
            val cached = feedCache[key]
            if (cached != null && !force) {
                _isRefreshing.value = false
                _feed.value = FeedState.Loaded(cached)
                return
            }
            val keepShown = force && _feed.value is FeedState.Loaded
            feedJob =
                viewModelScope.launch {
                    if (keepShown) _isRefreshing.value = true else _feed.value = FeedState.Loading
                    var result = repo.feed(params, picked)
                    if (result == null && !networkMonitor.isOnline.value) {
                        // Just started and not connected yet: wait for the network, then try once more.
                        withTimeoutOrNull(NETWORK_WAIT_MS) { networkMonitor.isOnline.first { it } }
                        result = repo.feed(params, picked)
                    }
                    _isRefreshing.value = false
                    when {
                        result != null -> {
                            if (params == null && result.moods.isNotEmpty()) _moods.value = result.moods
                            feedCache[key] = result
                            _feed.value = FeedState.Loaded(result)
                            streamResolver?.warmTracks(result.shelves.first().songs, WARM_TRACKS_COUNT)
                        }
                        keepShown -> Unit
                        params != null -> _feed.value = FeedState.MoodFailed
                        else -> {
                            // No feed at all: Home falls back to the plain Trending list.
                            _feed.value = null
                            showTrending(YOUTUBE_ID, force = false)
                        }
                    }
                }
        }

        // ── Local Home ──

        private val _local = MutableStateFlow<LocalHome?>(null)

        /** The local page while Home shows Local (for Shuffle all / Surprise album and the cards' songs). */
        val local: StateFlow<LocalHome?> = _local.asStateFlow()
        private var reshuffles = 0
        private var localJob: Job? = null
        private var libraryWatch: Job? = null

        /**
         * Builds the local page from the library and history (a quick database read). Shown again each time
         * Local is picked, so "Jump back in" follows what was just played; [reshuffle] (Refresh) gives new picks.
         */
        private fun showLocal(reshuffle: Boolean) {
            val repo = localHomeRepository ?: return
            feedJob?.cancel()
            loadJob?.cancel()
            if (reshuffle) reshuffles++
            localJob?.cancel()
            val keepShown = _feed.value is FeedState.Loaded && _local.value != null
            localJob =
                viewModelScope.launch {
                    if (keepShown) _isRefreshing.value = true else _feed.value = FeedState.Loading
                    val page = runCatching { repo.build(reshuffles) }.getOrNull()
                    _isRefreshing.value = false
                    if (page == null) {
                        _feed.value = null
                        _local.value = null
                        showTrending(LOCAL_ID, force = true)
                        return@launch
                    }
                    _local.value = page
                    _feed.value = FeedState.Loaded(HomeFeed(moods = emptyList(), shelves = page.shelves))
                }
            // A rescan that adds or removes songs rebuilds the page.
            if (libraryWatch == null) {
                libraryWatch =
                    viewModelScope.launch {
                        repo.libraryChanges().drop(1).collect {
                            if (shownProviderId == LOCAL_ID) showLocal(reshuffle = false)
                        }
                    }
            }
        }

        /** Folders and scan progress, for Local Home's set-up card while the library is empty. */
        val localSetup: StateFlow<LocalSetup> =
            (localHomeRepository?.setup ?: flowOf(LocalSetup()))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LocalSetup())

        /** A music folder picked on Local Home: saved and scanned; the page fills in when songs arrive. */
        fun addLocalFolder(treeUri: String) {
            val repo = localHomeRepository ?: return
            viewModelScope.launch {
                repo.addFolder(treeUri)
                if (shownProviderId == LOCAL_ID) showLocal(reshuffle = false)
            }
        }

        /** Shuffle all: the whole local library in random order. */
        fun shuffleAll() {
            val songs = _local.value?.allSongs.orEmpty()
            if (songs.isNotEmpty()) playCollection(songs.shuffled(), 0, "Shuffle all")
        }

        /** Surprise album: a random album from the library, in track order. */
        fun surpriseAlbum() {
            val album = _local.value?.albums?.randomOrNull() ?: return
            playCollection(album, 0, album.first().album)
        }

        /** A song in a Home row: online, like other Home songs (radio); local, the row plays as a list. */
        fun playFromRow(
            row: String,
            tracks: List<Track>,
            index: Int,
        ) {
            if (shownProviderId == LOCAL_ID && _local.value != null) {
                playCollection(tracks, index, row)
            } else {
                playFromList(tracks, index)
            }
        }

        private val _opened = MutableStateFlow<OpenedCollection?>(null)

        /** The playlist, album or artist opened from a Home card (shown over Home); null when none is open. */
        val opened: StateFlow<OpenedCollection?> = _opened.asStateFlow()
        private var openJob: Job? = null

        @Suppress("ReturnCount")
        fun openCollection(item: HomeItem.Collection) {
            _local.value?.collections?.get(item.browseId)?.let { tracks ->
                openJob?.cancel()
                _opened.value = OpenedCollection(item, tracks = tracks)
                return
            }
            val repo = homeFeedRepository ?: return
            _opened.value = OpenedCollection(item)
            openJob?.cancel()
            if (item.browseId.startsWith(HomeFeedRepository.SPOTIFY_PREFIX)) {
                openJob =
                    viewModelScope.launch {
                        repo.spotifyChart(item.browseId.removePrefix(HomeFeedRepository.SPOTIFY_PREFIX)).collect { p ->
                            _opened.value =
                                OpenedCollection(
                                    item,
                                    tracks = p.tracks.takeIf { it.isNotEmpty() || p.done },
                                    failed = p.done && p.tracks.isEmpty(),
                                    progress = if (p.done) null else "finding songs ${p.tried}/${p.total}\u2026",
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

        fun closeCollection() {
            openJob?.cancel()
            _opened.value = null
        }

        /** Plays an opened playlist or album from [index], keeping its order (like your own playlists). */
        fun playCollection(
            tracks: List<Track>,
            index: Int,
            source: String,
        ) {
            if (tracks.isNotEmpty()) playbackRepository?.playFrom(tracks, index, source)
        }

        /** Saves an opened playlist or album as one of your playlists, under its name. */
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

        private fun showTrending(
            providerId: String,
            force: Boolean,
        ) {
            shownProviderId = providerId
            val cached = trendingCache[providerId]
            loadJob?.cancel()
            if (cached != null && !force) {
                _isRefreshing.value = false
                _uiState.value = HomeUiState.Success(cached)
                return
            }
            // A reload keeps the current list on screen (with the button showing progress) until the new one arrives.
            val keepShown = force && _uiState.value is HomeUiState.Success
            loadJob =
                viewModelScope.launch {
                    if (keepShown) _isRefreshing.value = true else _uiState.value = HomeUiState.Loading
                    val provider = providerRegistry.getProvider(providerId) ?: providerRegistry.defaultProvider()
                    when (val result = loadTrending(provider)) {
                        is ProviderResult.Success -> {
                            // Local trending reflects the library, which can change with a rescan: not cached.
                            if (providerId != LOCAL_ID) trendingCache[providerId] = result.data
                            _uiState.value = HomeUiState.Success(result.data)
                            streamResolver?.warmTracks(result.data, WARM_TRACKS_COUNT)
                        }
                        is ProviderResult.Error -> if (!keepShown) _uiState.value = HomeUiState.Error(result.message)
                        is ProviderResult.Loading -> Unit
                    }
                    _isRefreshing.value = false
                }
        }

        /**
         * Right after the app starts the connection may not be up yet (that's the "fetch error" that went away on
         * its own): on failure, wait for the network if it's down, or briefly if it isn't, and try again.
         */
        private suspend fun loadTrending(provider: MusicProvider): ProviderResult<List<Track>> {
            var result = provider.trending(TRENDING_LIMIT)
            var attempt = 0
            while (result is ProviderResult.Error && attempt < TRENDING_RETRIES) {
                attempt++
                if (!networkMonitor.isOnline.value) {
                    withTimeoutOrNull(NETWORK_WAIT_MS) { networkMonitor.isOnline.first { it } }
                } else {
                    delay(RETRY_DELAY_MS * attempt)
                }
                result = provider.trending(TRENDING_LIMIT)
            }
            return result
        }

        // Last in the class on purpose: the first load runs right away (on creation), so every
        // field it touches must already be set up.
        init {
            viewModelScope.launch {
                // The saved languages first, so the first load is already tuned (no unfiltered flash).
                _languages.value = HomeLanguage.fromNames(appPreferences.homeLanguages.first())
                launch {
                    appPreferences.homeLanguages.map { HomeLanguage.fromNames(it) }.collect { picked ->
                        if (picked == _languages.value) return@collect
                        _languages.value = picked
                        if (shownProviderId == YOUTUBE_ID && homeFeedRepository != null) showFeed(force = false)
                    }
                }
                activeProviderId.collect { id ->
                    shownProviderId = id
                    _local.value = null
                    when {
                        id == YOUTUBE_ID && homeFeedRepository != null -> showFeed(force = false)
                        id == LOCAL_ID && localHomeRepository != null -> showLocal(reshuffle = false)
                        else -> {
                            _feed.value = null
                            showTrending(id, force = false)
                        }
                    }
                }
            }
        }
    }
