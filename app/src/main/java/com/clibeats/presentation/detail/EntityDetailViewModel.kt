@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.provider.YouTubeMusicProvider
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.MusicProvider
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EntityDetailUiState {
    data object Loading : EntityDetailUiState

    data class Success(
        val title: String,
        val subtitle: String?,
        val tracks: List<Track>,
    ) : EntityDetailUiState

    data class Error(val message: String) : EntityDetailUiState
}

@HiltViewModel
class EntityDetailViewModel
    @Inject
    constructor(
        private val providerRegistry: ProviderRegistry,
        private val songRepository: SongRepository,
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<EntityDetailUiState>(EntityDetailUiState.Loading)
        val uiState: StateFlow<EntityDetailUiState> = _uiState.asStateFlow()

        fun loadArtist(
            artistName: String,
            browseId: String?,
            providerId: String,
        ) {
            viewModelScope.launch {
                _uiState.value = EntityDetailUiState.Loading
                val localMatches = songRepository.getTracksByArtist(artistName)
                val tracks =
                    if (providerId == LOCAL_PROVIDER_ID && localMatches.isNotEmpty()) {
                        localMatches
                    } else {
                        val provider = getProvider(providerId)
                        // The artist's own full song list first (search alone misses many of their songs).
                        val main = artistName.split(", ", " & ", " x ", " feat. ", " ft. ").first().trim()
                        val browsed =
                            (provider as? YouTubeMusicProvider)?.artistSongs(main, browseId).orEmpty()
                        val online =
                            browsed.ifEmpty {
                                val searchResult = provider?.search(artistName, SEARCH_LIMIT)
                                (searchResult as? ProviderResult.Success)?.data
                                    ?.filter { it.artist.contains(artistName, ignoreCase = true) }
                                    ?.ifEmpty { (searchResult as? ProviderResult.Success)?.data.orEmpty() }
                                    .orEmpty()
                            }
                        online.ifEmpty { localMatches }
                    }
                _uiState.value =
                    EntityDetailUiState.Success(
                        title = artistName,
                        subtitle = "Artist · ${tracks.size} tracks",
                        tracks = tracks,
                    )
            }
        }

        fun loadAlbum(
            albumName: String,
            artistName: String,
            browseId: String?,
            providerId: String,
        ) {
            viewModelScope.launch {
                _uiState.value = EntityDetailUiState.Loading
                val localMatches = songRepository.getTracksByAlbum(albumName, artistName)
                val tracks =
                    if (providerId == LOCAL_PROVIDER_ID && localMatches.isNotEmpty()) {
                        localMatches
                    } else {
                        val provider = getProvider(providerId)
                        val browsed =
                            browseId?.takeIf { it.isNotBlank() }?.let { id ->
                                (provider as? YouTubeMusicProvider)?.page(id)?.second
                            }.orEmpty()
                        val online =
                            browsed.ifEmpty {
                                val query = if (artistName.isNotBlank()) "$albumName $artistName" else albumName
                                val searchResult = provider?.search(query, SEARCH_LIMIT)
                                (searchResult as? ProviderResult.Success)?.data
                                    ?.filter {
                                        it.album.contains(albumName, ignoreCase = true) ||
                                            it.title.contains(albumName, ignoreCase = true)
                                    }?.ifEmpty { (searchResult as? ProviderResult.Success)?.data.orEmpty() }
                                    .orEmpty()
                            }
                        online.ifEmpty { localMatches }
                    }
                _uiState.value =
                    EntityDetailUiState.Success(
                        title = albumName,
                        subtitle = if (artistName.isNotBlank()) "Album · $artistName" else "Album",
                        tracks = tracks,
                    )
            }
        }

        fun playAll(
            tracks: List<Track>,
            startIndex: Int = 0,
            source: String,
        ) {
            if (tracks.isNotEmpty()) {
                playbackRepository.playFrom(tracks, startIndex, source)
            }
        }

        fun shuffleAll(
            tracks: List<Track>,
            source: String,
        ) {
            if (tracks.isNotEmpty()) {
                playbackRepository.playFrom(tracks.shuffled(), 0, source)
            }
        }

        private fun getProvider(providerId: String): MusicProvider? =
            runCatching { providerRegistry.getProvider(providerId) ?: providerRegistry.defaultProvider() }
                .getOrNull()

        companion object {
            private const val SEARCH_LIMIT = 30
            private const val LOCAL_PROVIDER_ID = "local"
        }
    }
