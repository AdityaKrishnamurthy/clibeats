@file:Suppress("ForbiddenImport", "TooGenericExceptionCaught")

package com.clibeats.data.playlist

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.clibeats.data.local.CliBeatsDatabase
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.ProviderId
import com.clibeats.domain.provider.ProviderRegistry
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
        private val providerRegistry: ProviderRegistry,
        private val database: CliBeatsDatabase,
        private val libraryRepository: LibraryRepository,
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
                            libraryRepository.getLikedSongs().first().takeIf { it.isNotEmpty() }?.let {
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
                                            val safeSourceUrl =
                                                song.streamUrl?.takeIf {
                                                    song.providerId == "local" || !it.contains("expire")
                                                }
                                            CliBeatsTrack(
                                                providerId = song.providerId,
                                                sourceId = ProviderId.rawSourceId(song.providerId, song.id),
                                                title = song.title,
                                                artist = song.artist,
                                                album = song.album,
                                                durationMs = song.durationMs,
                                                artworkUrl = song.artworkUrl,
                                                sourceUrl = safeSourceUrl,
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
                val cliBeatsFile = readFile(source)
                var importedTracks = 0
                val write: suspend () -> Unit = {
                    cliBeatsFile.playlists.forEach { playlistData ->
                        if (playlistData.name.isBlank() || playlistData.name.length > MAX_NAME_LENGTH) return@forEach
                        // Stable per name (re-import merges) and collision-free, unlike String.hashCode().
                        val playlistId = "imported_" + UUID.nameUUIDFromBytes(playlistData.name.toByteArray())
                        val tracks =
                            playlistData.tracks
                                .take(MAX_TRACKS_PER_PLAYLIST)
                                .mapNotNull { trackData ->
                                    if (providerRegistry.getProvider(trackData.providerId) == null) {
                                        return@mapNotNull null
                                    }
                                    val sourceId =
                                        trackData.sourceId
                                            .takeIf { it.isNotBlank() && it.length <= MAX_SOURCE_ID_LENGTH }
                                            ?: return@mapNotNull null
                                    Track(
                                        id = ProviderId.composite(trackData.providerId, sourceId),
                                        title = trackData.title.take(MAX_TITLE_LENGTH),
                                        artist = trackData.artist.take(MAX_TITLE_LENGTH),
                                        album = trackData.album.take(MAX_TITLE_LENGTH),
                                        durationMs = trackData.durationMs.coerceAtLeast(0L),
                                        artworkUrl = safeArtworkUrl(trackData.artworkUrl),
                                        streamUrl = safeStreamUrl(trackData.providerId, trackData.sourceUrl),
                                        providerId = trackData.providerId,
                                    )
                                }.distinctBy { it.id }
                        songRepository.upsertTracks(tracks)
                        importedTracks += tracks.size
                        if (playlistData.name == FAVORITES_NAME) {
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
                database.withTransaction { write() }
                importedTracks
            }

        /**
         * Reads and validates an exchange file: bounded size, playlist/track counts, known providers,
         * and only http(s)/content artwork. Anything invalid is skipped rather than trusted.
         */
        private suspend fun readFile(source: Uri): CliBeatsFile {
            val text =
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(source) ?: error("Cannot open file")
                    input.bufferedReader().use { reader ->
                        val out = StringBuilder()
                        val buffer = CharArray(READ_BUFFER_CHARS)
                        while (true) {
                            val read = reader.read(buffer)
                            if (read < 0) break
                            out.append(buffer, 0, read)
                            require(out.length <= MAX_FILE_CHARS) { "File is too large (max 2 MB)" }
                        }
                        out.toString()
                    }
                }
            val file = CliBeatsFileCodec.decode(text)
            require(file.version <= CliBeatsFile.CURRENT_VERSION) {
                "This backup was made by a newer CLIBeats version"
            }
            require(file.playlists.size <= MAX_PLAYLISTS) { "Too many playlists (max $MAX_PLAYLISTS)" }
            require(file.playlists.sumOf { it.tracks.size } <= MAX_TOTAL_TRACKS) { "Too many tracks" }
            return file
        }

        companion object {
            const val FILE_NAME = "clibeats.json"
            const val BACKUP_FILE_NAME = "clibeats-backup.json"
            const val FAVORITES_NAME = "♥ Favorites"
            private const val LOCAL_FILE_PREFIX = "localpl:"
            private const val MAX_FILE_CHARS = 2 * 1024 * 1024
            private const val READ_BUFFER_CHARS = 8 * 1024
            private const val MAX_PLAYLISTS = 200
            private const val MAX_TOTAL_TRACKS = 20_000
            private const val MAX_TRACKS_PER_PLAYLIST = 5_000
            private const val MAX_NAME_LENGTH = 200
            private const val MAX_TITLE_LENGTH = 500
            private const val MAX_SOURCE_ID_LENGTH = 512

            /**
             * Online URLs expire and must be re-resolved; only SAF content:// URIs are kept for
             * local tracks. Anything else (file://, javascript:, ...) from the file is dropped.
             */
            fun safeStreamUrl(
                providerId: String,
                url: String?,
            ): String? = url?.takeIf { providerId == "local" && it.startsWith("content://") }

            /** Remote artwork must be http(s) or a SAF content URI; data:/javascript:/file: are dropped. */
            fun safeArtworkUrl(url: String?): String? {
                val value = url?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_SOURCE_ID_LENGTH } ?: return null
                val scheme = runCatching { java.net.URI(value).scheme?.lowercase() }.getOrNull()
                return value.takeIf { scheme == "http" || scheme == "https" || scheme == "content" }
            }
        }
    }
