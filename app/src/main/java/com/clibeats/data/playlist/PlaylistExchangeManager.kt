@file:Suppress("ForbiddenImport", "TooGenericExceptionCaught")

package com.clibeats.data.playlist

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.clibeats.data.local.CliBeatsDatabase
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.ProviderId
import com.clibeats.domain.repository.LibraryRepository
import com.clibeats.domain.repository.PlaylistRepository
import com.clibeats.domain.repository.SongRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports and imports playlists as a `clibeats.json` file the user picks through the
 * system file picker (SAF), so the file can live anywhere (Downloads, Drive, ...).
 *
 * Export writes provider + source ids; import re-persists them under composite ids.
 * Online stream URLs are never trusted from the file: they are re-resolved at playback.
 */
@Singleton
class PlaylistExchangeManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playlistRepository: PlaylistRepository,
        private val songRepository: SongRepository,
        private val database: CliBeatsDatabase? = null,
        private val libraryRepository: LibraryRepository? = null,
    ) {
        /**
         * Writes the app's own playlists plus ♥ Favorites to [target]; returns the number exported.
         * Local M3U/PLS mirrors are skipped: their source of truth is the file in the music folder.
         */
        suspend fun export(target: Uri): Result<Int> =
            runCatching {
                val appPlaylists =
                    playlistRepository.getAllPlaylistsAsFlow().first().filterNot { it.id.startsWith(LOCAL_FILE_PREFIX) }
                val named =
                    appPlaylists.map { it.name to playlistRepository.getSongsForPlaylistAsFlow(it.id).first() } +
                        listOfNotNull(
                            libraryRepository?.getLikedSongs()?.first()?.takeIf { it.isNotEmpty() }?.let {
                                FAVORITES_NAME to it
                            },
                        )
                val file =
                    CliBeatsFile(
                        playlists =
                            named.map { (name, songs) ->
                                CliBeatsPlaylist(
                                    name = name,
                                    tracks =
                                        songs.map { song ->
                                            CliBeatsTrack(
                                                providerId = song.providerId,
                                                sourceId = ProviderId.rawSourceId(song.providerId, song.id),
                                                title = song.title,
                                                artist = song.artist,
                                                album = song.album,
                                                durationMs = song.durationMs,
                                                artworkUrl = song.artworkUrl,
                                                sourceUrl = song.streamUrl,
                                            )
                                        },
                                )
                            },
                    )
                withContext(Dispatchers.IO) {
                    val out =
                        context.contentResolver.openOutputStream(target, "wt") ?: error("Cannot open file for writing")
                    out.bufferedWriter().use { it.write(CliBeatsFileCodec.encode(file)) }
                }
                named.size
            }

        /** Reads playlists from [source]; returns the number of tracks imported. */
        suspend fun import(source: Uri): Result<Int> =
            runCatching {
                val text =
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(source) ?: error("Cannot open file")
                        input.bufferedReader().use { it.readText() }
                    }
                val cliBeatsFile = CliBeatsFileCodec.decode(text)
                var importedTracks = 0
                val write: suspend () -> Unit = {
                    cliBeatsFile.playlists.forEach { playlistData ->
                        if (playlistData.name.isBlank()) return@forEach
                        // Stable per name (re-import merges) and collision-free, unlike String.hashCode().
                        val playlistId = "imported_" + UUID.nameUUIDFromBytes(playlistData.name.toByteArray())
                        val tracks =
                            playlistData.tracks.map { trackData ->
                                Track(
                                    id = ProviderId.composite(trackData.providerId, trackData.sourceId),
                                    title = trackData.title,
                                    artist = trackData.artist,
                                    album = trackData.album,
                                    durationMs = trackData.durationMs,
                                    artworkUrl = trackData.artworkUrl,
                                    streamUrl = safeStreamUrl(trackData.providerId, trackData.sourceUrl),
                                    providerId = trackData.providerId,
                                )
                            }.distinctBy { it.id }
                        songRepository.upsertTracks(tracks)
                        importedTracks += tracks.size
                        if (playlistData.name == FAVORITES_NAME && libraryRepository != null) {
                            tracks.forEach { libraryRepository.addToFavorites(it) }
                            return@forEach
                        }
                        playlistRepository.upsertPlaylist(
                            Playlist(
                                id = playlistId,
                                name = playlistData.name,
                                description = "Imported from clibeats.json",
                                artworkUrl = null,
                                trackCount = tracks.size,
                                isOwned = true,
                                providerId = "local",
                            ),
                        )
                        // Replace (not append): re-importing an updated file must not leave stale songs.
                        playlistRepository.reorderPlaylistSongs(playlistId, tracks.map { it.id })
                    }
                }
                // All-or-nothing: a malformed entry must not leave half-imported playlists.
                if (database != null) database.withTransaction { write() } else write()
                importedTracks
            }

        companion object {
            const val FILE_NAME = "clibeats.json"
            const val FAVORITES_NAME = "♥ Favorites"
            private const val LOCAL_FILE_PREFIX = "localpl:"

            /**
             * Online URLs expire and must be re-resolved; only SAF content:// URIs are kept for
             * local tracks. Anything else (file://, javascript:, ...) from the file is dropped.
             */
            fun safeStreamUrl(
                providerId: String,
                url: String?,
            ): String? = url?.takeIf { providerId == "local" && it.startsWith("content://") }
        }
    }
