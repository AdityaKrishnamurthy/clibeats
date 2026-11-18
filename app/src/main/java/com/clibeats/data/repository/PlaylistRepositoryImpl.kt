// ForbiddenImport: data-layer self-imports are legitimate; Phase 0 com.clibeats.data.* pattern is over-broad.
@file:Suppress("ForbiddenImport")

package com.clibeats.data.repository

import com.clibeats.data.local.dao.PlaylistDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.PlaylistEntity
import com.clibeats.data.local.entity.PlaylistSongCrossRef
import com.clibeats.data.local.mapper.toDomain
import com.clibeats.data.local.mapper.toEntity
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Indentation: detekt 1.23.6 misparses ktlint_official @Inject constructor() style (false positive).
// Line length: single-line expression bodies fit ktlint's tolerance; detekt's 120 default flags them.
@Suppress("Indentation", "MaxLineLength", "MaximumLineLength", "Wrapping")
@Singleton
class PlaylistRepositoryImpl
    @Inject
    constructor(
        private val playlistDao: PlaylistDao,
        private val songDao: SongDao? = null,
    ) : PlaylistRepository {
        override fun getAllPlaylistsAsFlow(): Flow<List<Playlist>> = playlistDao.getAllAsFlow().map { list -> list.map { it.toDomain() } }

        override suspend fun getPlaylistById(id: String): Playlist? = playlistDao.getById(id)?.toDomain()

        override suspend fun upsertPlaylist(playlist: Playlist) {
            val existing = playlistDao.getById(playlist.id)
            playlistDao.upsert(
                playlist.toEntity(
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

        override suspend fun deletePlaylist(id: String) = playlistDao.deleteById(id)

        override fun getSongsForPlaylistAsFlow(playlistId: String): Flow<List<Track>> =
            playlistDao.getSongsForPlaylistAsFlow(playlistId).map { list ->
                list.map { it.toDomain() }
            }

        override suspend fun addSongToPlaylist(
            playlistId: String,
            songId: String,
            position: Int,
        ) = playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId, position))

        override suspend fun removeSongFromPlaylist(
            playlistId: String,
            songId: String,
        ) {
            playlistDao.removeSongFromPlaylist(playlistId, songId)
            playlistDao.refreshTrackCount(playlistId)
        }

        override suspend fun reorderPlaylistSongs(
            playlistId: String,
            songIds: List<String>,
        ) = playlistDao.reorderPlaylistSongs(playlistId, songIds)

        override suspend fun addTrackToPlaylist(
            playlistId: String,
            track: Track,
        ) {
            // The cross-ref has a FK on songs.id; search results aren't in the songs table yet.
            // Persist without the (expiring) stream URL; it is re-resolved at playback.
            val storable = if (track.streamUrl?.contains("expire") == true) track.copy(streamUrl = null) else track
            songDao?.upsertAll(listOf(storable.toEntity()))
            playlistDao.addSongToPlaylist(
                PlaylistSongCrossRef(playlistId, track.id, playlistDao.nextPosition(playlistId)),
            )
            playlistDao.refreshTrackCount(playlistId)
        }

        override suspend fun createAppPlaylist(
            name: String,
            songIds: List<String>,
        ): String {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            playlistDao.upsert(
                PlaylistEntity(
                    id = id,
                    name = name,
                    description = null,
                    artworkUrl = null,
                    trackCount = 0,
                    isOwned = true,
                    providerId = "local",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            // Same transaction helper as reorder: inserts cross refs in order.
            playlistDao.reorderPlaylistSongs(id, songIds.distinct())
            playlistDao.refreshTrackCount(id)
            return id
        }
    }
