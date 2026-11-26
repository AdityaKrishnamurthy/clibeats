@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.home

import com.clibeats.data.home.HomeFeedRepository
import com.clibeats.data.home.HomeLanguage
import com.clibeats.data.home.LocalHome
import com.clibeats.data.home.LocalHomeRepository
import com.clibeats.data.network.NetworkMonitor
import com.clibeats.domain.model.HomeFeed
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeMood
import com.clibeats.domain.model.HomeShelf
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.MusicProvider
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.repository.PlaylistRepository
import com.clibeats.playback.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coordinates home feed loading, caching, retry, local fallback, and collection preview state.
 */
@Suppress("LongParameterList")
internal class HomeFeedCoordinator(
    private val providerRegistry: ProviderRegistry,
    private val networkMonitor: NetworkMonitor,
    private val streamResolver: StreamResolver? = null,
    private val homeFeedRepository: HomeFeedRepository? = null,
    private val localHomeRepository: LocalHomeRepository? = null,
    private val playlistRepository: PlaylistRepository? = null,
) {
    private val trendingCache = mutableMapOf<String, List<Track>>()
    private val feedCache = mutableMapOf<String, HomeFeed>()
    private var yourCollections: Map<String, List<Track>> = emptyMap()
    private var yourShelves: List<HomeShelf> = emptyList()
    private var yourReshuffles = 0
    private var localReshuffles = 0
    private var loadJob: Job? = null
    private var feedJob: Job? = null
    private var localJob: Job? = null
    private var libraryWatch: Job? = null
    private var openJob: Job? = null

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _feed = MutableStateFlow<FeedState?>(null)
    val feed: StateFlow<FeedState?> = _feed.asStateFlow()
    private val _selectedMood = MutableStateFlow<HomeMood?>(null)
    val selectedMood: StateFlow<HomeMood?> = _selectedMood.asStateFlow()
    private val _moods = MutableStateFlow<List<HomeMood>>(emptyList())
    val moods: StateFlow<List<HomeMood>> = _moods.asStateFlow()
    private val _local = MutableStateFlow<LocalHome?>(null)
    val local: StateFlow<LocalHome?> = _local.asStateFlow()
    private val _opened = MutableStateFlow<OpenedCollection?>(null)
    val opened: StateFlow<OpenedCollection?> = _opened.asStateFlow()

    fun clearLocal() {
        _local.value = null
    }

    fun clearFeed() {
        _feed.value = null
    }

    fun toggleMood(
        scope: CoroutineScope,
        mood: HomeMood,
        languages: Set<HomeLanguage>,
    ) {
        _selectedMood.value = if (_selectedMood.value == mood) null else mood
        showFeed(scope, languages, force = false)
    }

    fun showFeed(
        scope: CoroutineScope,
        languages: Set<HomeLanguage>,
        force: Boolean,
    ) {
        if (homeFeedRepository == null) return
        localJob?.cancel()
        val params = _selectedMood.value?.params
        val key = "${params.orEmpty()}|${languages.map { it.name }.sorted().joinToString(",")}"
        feedJob?.cancel()
        val cached = feedCache[key]
        if (cached != null && !force) {
            _isRefreshing.value = false
            _feed.value = FeedState.Loaded(cached)
            return
        }
        val keepShown = force && _feed.value is FeedState.Loaded
        feedJob =
            scope.launch {
                if (keepShown) _isRefreshing.value = true else _feed.value = FeedState.Loading
                val result = loadOnlineFeed(params, languages, force)
                _isRefreshing.value = false
                applyOnlineFeedResult(scope, result, key, params, keepShown)
            }
    }

    fun showLocal(
        scope: CoroutineScope,
        reshuffle: Boolean,
        isShowingLocal: () -> Boolean,
    ) {
        val repo = localHomeRepository ?: return
        feedJob?.cancel()
        loadJob?.cancel()
        localJob?.cancel()
        val keepShown = _feed.value is FeedState.Loaded && _local.value != null
        localJob =
            scope.launch {
                if (keepShown) _isRefreshing.value = true else _feed.value = FeedState.Loading
                if (reshuffle) localReshuffles++
                val page = runCatching { repo.build(localReshuffles) }.getOrNull()
                _isRefreshing.value = false
                if (page == null) {
                    _feed.value = null
                    _local.value = null
                    showTrending(scope, LOCAL_ID, force = true)
                } else {
                    _local.value = page
                    _feed.value = FeedState.Loaded(HomeFeed(moods = emptyList(), shelves = page.shelves))
                }
            }
        if (libraryWatch == null) {
            libraryWatch =
                scope.launch {
                    repo.libraryChanges().drop(1).collect {
                        if (isShowingLocal()) showLocal(scope, reshuffle = false, isShowingLocal)
                    }
                }
        }
    }

    fun showTrending(
        scope: CoroutineScope,
        providerId: String,
        force: Boolean,
    ) {
        val cached = trendingCache[providerId]
        loadJob?.cancel()
        if (cached != null && !force) {
            _isRefreshing.value = false
            _uiState.value = HomeUiState.Success(cached)
            return
        }
        val keepShown = force && _uiState.value is HomeUiState.Success
        loadJob =
            scope.launch {
                if (keepShown) _isRefreshing.value = true else _uiState.value = HomeUiState.Loading
                val provider = providerRegistry.getProvider(providerId) ?: providerRegistry.defaultProvider()
                when (val result = loadTrendingWithRetry(provider)) {
                    is ProviderResult.Success -> {
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

    fun openCollection(
        scope: CoroutineScope,
        item: HomeItem.Collection,
    ) {
        val localTracks = _local.value?.collections?.get(item.browseId) ?: yourCollections[item.browseId]
        val repo = homeFeedRepository
        openJob?.cancel()
        when {
            localTracks != null -> _opened.value = OpenedCollection(item, tracks = localTracks)
            repo == null -> Unit
            item.browseId.startsWith(HomeFeedRepository.SPOTIFY_PREFIX) -> {
                _opened.value = OpenedCollection(item)
                val chartId = item.browseId.removePrefix(HomeFeedRepository.SPOTIFY_PREFIX)
                openJob =
                    scope.launch {
                        repo.spotifyChart(chartId).collect { p ->
                            _opened.value =
                                OpenedCollection(
                                    item = item,
                                    tracks = p.tracks.takeIf { it.isNotEmpty() || p.done },
                                    failed = p.done && p.tracks.isEmpty(),
                                    progress = if (p.done) null else "finding songs ${p.tried}/${p.total}\u2026",
                                )
                        }
                    }
            }
            else -> {
                _opened.value = OpenedCollection(item)
                openJob =
                    scope.launch {
                        val page = repo.collection(item.browseId)
                        _opened.value =
                            if (page == null) {
                                OpenedCollection(item, failed = true)
                            } else {
                                OpenedCollection(item, tracks = page.second, title = page.first.ifBlank { item.title })
                            }
                    }
            }
        }
    }

    fun closeCollection() {
        openJob?.cancel()
        _opened.value = null
    }

    suspend fun rebuildYourRows(applyToVisible: Boolean) {
        val repo = localHomeRepository
        val page = if (repo != null) runCatching { repo.buildOnline(yourReshuffles) }.getOrNull() else null
        page?.let { updated ->
            val old = yourShelves.toSet()

            fun swap(feed: HomeFeed) = feed.copy(shelves = feed.shelves.filterNot { it in old } + updated.shelves)
            yourCollections = updated.collections
            yourShelves = updated.shelves
            feedCache.replaceAll { key, feed -> if (key.startsWith("|")) swap(feed) else feed }
            val loaded = _feed.value as? FeedState.Loaded
            if (applyToVisible && _selectedMood.value == null && loaded != null) {
                _feed.value = FeedState.Loaded(swap(loaded.feed))
            }
        }
    }

    suspend fun saveCollection(
        name: String,
        tracks: List<Track>,
    ) {
        val repo = playlistRepository ?: return
        runCatching {
            val id = repo.createAppPlaylist(name, emptyList())
            tracks.forEach { repo.addTrackToPlaylist(id, it) }
        }
    }

    private suspend fun applyOnlineFeedResult(
        scope: CoroutineScope,
        result: HomeFeed?,
        key: String,
        params: String?,
        keepShown: Boolean,
    ) {
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
                _feed.value = null
                showTrending(scope, YOUTUBE_ID, force = false)
            }
        }
    }

    private suspend fun loadOnlineFeed(
        params: String?,
        languages: Set<HomeLanguage>,
        force: Boolean,
    ): HomeFeed? {
        val repo = homeFeedRepository
        val localRepo = localHomeRepository
        var feed = repo?.feed(params, languages, refresh = force)
        if (repo != null && feed == null && !networkMonitor.isOnline.value) {
            withTimeoutOrNull(NETWORK_WAIT_MS) { networkMonitor.isOnline.first { it } }
            feed = repo.feed(params, languages)
        }
        return if (feed != null && params == null && localRepo != null) {
            if (force) yourReshuffles++
            val page = runCatching { localRepo.buildOnline(yourReshuffles) }.getOrNull()
            if (page != null) {
                yourCollections = page.collections
                yourShelves = page.shelves
                feed.copy(shelves = feed.shelves + page.shelves)
            } else {
                feed
            }
        } else {
            feed
        }
    }

    private suspend fun loadTrendingWithRetry(provider: MusicProvider): ProviderResult<List<Track>> {
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

    companion object {
        private const val TRENDING_LIMIT = 25
        private const val TRENDING_RETRIES = 2
        private const val RETRY_DELAY_MS = 1_500L
        private const val NETWORK_WAIT_MS = 15_000L
        private const val WARM_TRACKS_COUNT = 3
        private const val LOCAL_ID = "local"
        private const val YOUTUBE_ID = "youtube_music"
    }
}
