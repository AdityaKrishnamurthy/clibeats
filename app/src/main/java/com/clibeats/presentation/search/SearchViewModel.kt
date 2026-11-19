@file:Suppress("ForbiddenImport", "ktlint:standard:function-naming")

package com.clibeats.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.MusicProvider
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val providerRegistry: ProviderRegistry,
        private val appPreferences: AppPreferences,
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        val providers: List<MusicProvider> = providerRegistry.providers

        val activeProviderId: StateFlow<String> =
            appPreferences.activeProviderId
                .map { it ?: ProviderRegistry.DEFAULT_PROVIDER_ID }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ProviderRegistry.DEFAULT_PROVIDER_ID)

        fun setActiveProvider(id: String) {
            viewModelScope.launch { appPreferences.setActiveProviderId(id) }
        }

        /** Plays the tapped result with the rest of the results queued after it. */
        fun onPlay(
            tracks: List<Track>,
            index: Int,
        ) {
            viewModelScope.launch {
                // "Auto-queue related" off: play only the tapped song, no suggestions after it.
                if (appPreferences.autoQueueRelated.first()) {
                    playbackRepository.setQueue(tracks, index)
                } else {
                    playbackRepository.setQueue(listOf(tracks[index]), 0)
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
                        flowOf(SearchUiState.Idle)
                    } else {
                        activeProviderId.flatMapLatest { id ->
                            val provider = providerRegistry.getProvider(id) ?: providerRegistry.defaultProvider()
                            flow {
                                emit(SearchUiState.Loading)
                                emit(
                                    when (val result = provider.search(q)) {
                                        is ProviderResult.Success -> SearchUiState.Success(result.data)
                                        is ProviderResult.Error -> SearchUiState.Error(result.message)
                                        is ProviderResult.Loading -> SearchUiState.Loading
                                    },
                                )
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

        fun onAddToQueue(track: Track) {
            playbackRepository.addToQueue(track)
        }

        fun onPlayNext(track: Track) {
            playbackRepository.playNext(track)
        }
    }
