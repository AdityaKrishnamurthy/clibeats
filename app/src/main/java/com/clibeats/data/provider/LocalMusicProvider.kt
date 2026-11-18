@file:Suppress("ForbiddenImport", "TooGenericExceptionCaught")

package com.clibeats.data.provider

import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.MusicProvider
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.repository.SongRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MusicProvider over on-device music: songs scanned from the folder picked in
 * Settings → Local Music (see LocalMusicScanner), stored with providerId = "local".
 */
@Singleton
class LocalMusicProvider
    @Inject
    constructor(
        private val songRepository: SongRepository,
    ) : MusicProvider {
        override val providerId: String = "local"
        override val displayName: String = "Local Device Media"

        override suspend fun search(
            query: String,
            limit: Int,
        ): ProviderResult<List<Track>> =
            runCatching {
                val tracks = songRepository.searchLocalTracksAsFlow(query).first().map { it.withLocalFileUri() }
                ProviderResult.Success(tracks.take(limit))
            }.getOrElse { e ->
                ProviderResult.Error(e.message ?: "Local search failed", e)
            }

        override suspend fun trending(limit: Int): ProviderResult<List<Track>> =
            runCatching {
                val tracks = songRepository.getLocalTracksAsFlow().first().map { it.withLocalFileUri() }
                ProviderResult.Success(tracks.take(limit))
            }.getOrElse { e ->
                ProviderResult.Error(e.message ?: "Local library read failed", e)
            }

        override suspend fun getTrack(trackId: String): ProviderResult<Track> =
            runCatching {
                val track = songRepository.getTrackById(trackId)?.withLocalFileUri()
                if (track != null) ProviderResult.Success(track) else ProviderResult.Error("No local track: $trackId")
            }.getOrElse { e ->
                ProviderResult.Error(e.message ?: "Local track lookup failed", e)
            }

        override suspend fun stream(trackId: String): ProviderResult<String> =
            runCatching {
                val url = songRepository.getTrackById(trackId)?.withLocalFileUri()?.streamUrl
                if (url != null) ProviderResult.Success(url) else ProviderResult.Error("No local file for: $trackId")
            }.getOrElse { e ->
                ProviderResult.Error(e.message ?: "Local stream failed", e)
            }

        override suspend fun playlists(): ProviderResult<List<Playlist>> = ProviderResult.Success(emptyList())

        override suspend fun queue(): ProviderResult<List<Track>> = ProviderResult.Success(emptyList())

        /** Rewrites a bare local path into a file:// URI; content:// (SAF) and http URIs pass through. */
        private fun Track.withLocalFileUri(): Track =
            if (localPathIsSet(this) && !streamUrl.orEmpty().startsWith("file://")) {
                copy(streamUrl = "file://$streamUrl")
            } else {
                this
            }

        private fun localPathIsSet(track: Track): Boolean =
            track.streamUrl != null &&
                !track.streamUrl.startsWith("http://") &&
                !track.streamUrl.startsWith("https://") &&
                !track.streamUrl.startsWith("file://") &&
                !track.streamUrl.startsWith("content://")
    }
