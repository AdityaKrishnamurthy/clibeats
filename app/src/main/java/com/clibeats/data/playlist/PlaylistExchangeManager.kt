@file:Suppress("ForbiddenImport", "TooGenericExceptionCaught", "LongParameterList")

package com.clibeats.data.playlist

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.clibeats.data.local.CliBeatsDatabase
import com.clibeats.data.local.mapper.toDomain
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.Album
import com.clibeats.domain.model.Artist
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
import java.io.File
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
@Suppress("TooManyFunctions")
class PlaylistExchangeManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playlistRepository: PlaylistRepository,
        private val songRepository: SongRepository,
        private val providerRegistry: ProviderRegistry,
        private val database: CliBeatsDatabase,
        private val libraryRepository: LibraryRepository,
        private val appPreferences: AppPreferences,
    ) {
        /**
         * Writes the app's own playlists plus ♥ Favorites to [target]; returns the number exported.
         * Local M3U/PLS mirrors are skipped: their source of truth is the file in the music folder.
         */
        suspend fun export(target: Uri): Result<Int> =
            runCatching {
                val file = buildFile()
                withContext(Dispatchers.IO) {
                    val out =
                        context.contentResolver.openOutputStream(target, "wt") ?: error("Cannot open file for writing")
                    out.bufferedWriter().use { it.write(CliBeatsFileCodec.encode(file)) }
                }
                file.playlists.size
            }

        /** Same payload as [export], written straight to an app-private file (auto snapshots). */
        suspend fun exportToFile(target: File): Result<Int> =
            runCatching {
                val file = buildFile()
                withContext(Dispatchers.IO) {
                    target.parentFile?.mkdirs()
                    target.writeText(CliBeatsFileCodec.encode(file))
                }
                file.playlists.size
            }

        /** Builds the complete portable library: playlists, favorites, albums, artists and safe prefs. */
        suspend fun buildFile(): CliBeatsFile {
            val history = playHistory()
            val appPlaylists =
                playlistRepository.getAllPlaylistsAsFlow().first().filterNot { it.id.startsWith(LOCAL_FILE_PREFIX) }
            val named =
                appPlaylists.map { it.name to playlistRepository.getSongsForPlaylistAsFlow(it.id).first() } +
                    listOfNotNull(
                        libraryRepository.getLikedSongs().first().takeIf { it.isNotEmpty() }?.let {
                            FAVORITES_NAME to it
                        },
                    )
            return CliBeatsFile(
                playlists =
                    named.map { (name, songs) ->
                        CliBeatsPlaylist(
                            name = name,
                            tracks = songs.map(::exchangeTrack),
                        )
                    },
                prefs = currentPrefs(),
                settings = appPreferences.exportSettings().mapValues { (_, v) -> CliBeatsSetting(v.first, v.second) },
                historyTracks = history.first,
                history = history.second,
                downloads = downloadedSongs().map(::exchangeTrack),
                albums =
                    libraryRepository.getSavedAlbums().first().map { album ->
                        CliBeatsAlbum(
                            id = album.id,
                            title = album.title,
                            artist = album.artist,
                            year = album.year,
                            artworkUrl = album.artworkUrl,
                            trackCount = album.trackCount,
                            providerId = album.providerId,
                        )
                    },
                artists =
                    libraryRepository.getSavedArtists().first().map { artist ->
                        CliBeatsArtist(
                            id = artist.id,
                            name = artist.name,
                            artworkUrl = artist.artworkUrl,
                            providerId = artist.providerId,
                        )
                    },
            )
        }

        /** A song as it's written in the file (online stream links expire, so they're left out). */
        private fun exchangeTrack(song: Track): CliBeatsTrack {
            val safeSourceUrl = song.streamUrl?.takeIf { song.providerId == "local" || !it.contains("expire") }
            return CliBeatsTrack(
                providerId = song.providerId,
                sourceId = ProviderId.rawSourceId(song.providerId, song.id),
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                artworkUrl = song.artworkUrl,
                sourceUrl = safeSourceUrl,
            )
        }

        /**
         * The play history (newest first): the latest [MAX_HISTORY] plays, plus the last play of every older song,
         * so every song in Library → Online and Recently played comes back. Each song once, plays pointing at it.
         */
        private suspend fun playHistory(): Pair<List<CliBeatsTrack>, List<CliBeatsPlay>> {
            val all = database.historyDao().getAllAsFlow().first()
            val kept = historyToKeep(all.map { it.songId to it.playedAt }, MAX_HISTORY).toHashSet()
            val plays = all.filter { (it.songId to it.playedAt) in kept }
            if (plays.isEmpty()) return emptyList<CliBeatsTrack>() to emptyList()
            val ids = plays.map { it.songId }.distinct()
            val songs =
                ids.chunked(SQL_CHUNK).flatMap { database.historyDao().getSongsByIds(it) }
                    .associateBy { it.id }
            val exchanged = songs.mapValues { exchangeTrack(it.value.toDomain()) }
            return packHistory(plays.map { it.songId to it.playedAt }, exchanged)
        }

        /** Songs downloaded for offline (just which ones; the audio isn't backed up). */
        private suspend fun downloadedSongs(): List<Track> {
            val ids = database.downloadedTrackDao().getAll().map { it.songId }
            return ids.chunked(SQL_CHUNK).flatMap { database.historyDao().getSongsByIds(it) }.map { it.toDomain() }
        }

        /** Songs from the last restored backup that were downloaded there and aren't here yet. */
        private val _pendingDownloads = kotlinx.coroutines.flow.MutableStateFlow<List<Track>>(emptyList())
        val pendingDownloads: kotlinx.coroutines.flow.StateFlow<List<Track>> = _pendingDownloads

        fun clearPendingDownloads() {
            _pendingDownloads.value = emptyList()
        }

        /** Non-secret settings only: never tokens, history or search queries. */
        private suspend fun currentPrefs(): CliBeatsPrefs =
            CliBeatsPrefs(
                themeMode = appPreferences.themeMode.first(),
                accentColorName = appPreferences.accentColorName.first(),
                activeProviderId = appPreferences.activeProviderId.first(),
                cacheMaxMb = appPreferences.cacheMaxMb.first(),
                artworkCacheMb = appPreferences.artworkCacheMb.first(),
                highQualityStreaming = appPreferences.highQualityStreaming.first(),
                autoQueueRelated = appPreferences.autoQueueRelated.first(),
                onboardingDone = appPreferences.onboardingDone.first(),
            )

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
                                .mapNotNull(::importedTrack)
                                .distinctBy { it.id }
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
                restoreExtras(cliBeatsFile)
                restoreHistory(cliBeatsFile)
                offerDownloads(cliBeatsFile)
                importedTracks
            }

        /** A song from the file, checked (known provider, sane lengths, safe links); null when it isn't usable. */
        private fun importedTrack(trackData: CliBeatsTrack): Track? {
            val sourceId =
                trackData.sourceId.takeIf {
                    providerRegistry.getProvider(trackData.providerId) != null &&
                        it.isNotBlank() && it.length <= MAX_SOURCE_ID_LENGTH
                } ?: return null
            return Track(
                id = ProviderId.composite(trackData.providerId, sourceId),
                title = trackData.title.take(MAX_TITLE_LENGTH),
                artist = trackData.artist.take(MAX_TITLE_LENGTH),
                album = trackData.album.take(MAX_TITLE_LENGTH),
                durationMs = trackData.durationMs.coerceAtLeast(0L),
                artworkUrl = safeArtworkUrl(trackData.artworkUrl),
                streamUrl = safeStreamUrl(trackData.providerId, trackData.sourceUrl),
                providerId = trackData.providerId,
            )
        }

        /** Adds the backup's plays (Most played, Recently played); plays already here aren't added twice. */
        private suspend fun restoreHistory(file: CliBeatsFile) {
            if (file.history.isEmpty()) return
            val tracks = file.historyTracks.take(MAX_HISTORY_READ).map(::importedTrack)
            songRepository.upsertTracks(tracks.filterNotNull().distinctBy { it.id })
            val dao = database.historyDao()
            val have = dao.getAllAsFlow().first().mapTo(HashSet()) { it.songId to it.playedAt }
            val toAdd = playsToRestore(file.history.take(MAX_HISTORY_READ), tracks, have)
            database.withTransaction {
                toAdd.forEach { (track, at) ->
                    dao.insert(
                        com.clibeats.data.local.entity.HistoryEntity(
                            songId = track.id,
                            playedAt = at,
                            providerId = track.providerId,
                        ),
                    )
                }
            }
        }

        /** The backup's downloaded songs that aren't downloaded here: the app offers to download them again. */
        private suspend fun offerDownloads(file: CliBeatsFile) {
            if (file.downloads.isEmpty()) return
            val have = database.downloadedTrackDao().getAll().mapTo(HashSet()) { it.songId }
            val listed = file.downloads.take(MAX_TRACKS_PER_PLAYLIST).mapNotNull(::importedTrack)
            val missing = downloadsToOffer(listed, have)
            songRepository.upsertTracks(missing)
            _pendingDownloads.value = missing
        }

        /** Applies the settings and saved albums/artists a snapshot carries. */
        private suspend fun restoreExtras(file: CliBeatsFile) {
            file.prefs?.let { applyPrefs(it) }
            // A full backup (v3) carries every setting; applied after the older fields so they win.
            if (file.settings.isNotEmpty()) {
                appPreferences.importSettings(file.settings.mapValues { (_, v) -> v.type to v.value })
            }
            file.albums.forEach { album ->
                if (!libraryRepository.isAlbumSaved(album.id).first()) {
                    libraryRepository.toggleSaveAlbum(
                        Album(
                            id = album.id,
                            title = album.title,
                            artist = album.artist,
                            year = album.year,
                            artworkUrl = safeArtworkUrl(album.artworkUrl),
                            trackCount = album.trackCount.coerceAtLeast(0),
                            providerId = album.providerId,
                        ),
                    )
                }
            }
            file.artists.forEach { artist ->
                if (!libraryRepository.isArtistSaved(artist.id).first()) {
                    libraryRepository.toggleSaveArtist(
                        Artist(
                            id = artist.id,
                            name = artist.name,
                            artworkUrl = safeArtworkUrl(artist.artworkUrl),
                            providerId = artist.providerId,
                        ),
                    )
                }
            }
        }

        private suspend fun applyPrefs(prefs: CliBeatsPrefs) {
            prefs.themeMode?.let { appPreferences.setThemeMode(it) }
            prefs.accentColorName?.let { appPreferences.setAccentColorName(it) }
            prefs.activeProviderId
                ?.takeIf { providerRegistry.getProvider(it) != null }
                ?.let { appPreferences.setActiveProviderId(it) }
            prefs.cacheMaxMb
                ?.takeIf { it in MIN_CACHE_MB..MAX_CACHE_MB }
                ?.let { appPreferences.setCacheMaxMb(it) }
            prefs.artworkCacheMb
                ?.takeIf { it in MIN_ARTWORK_MB..MAX_ARTWORK_MB }
                ?.let { appPreferences.setArtworkCacheMb(it) }
            prefs.highQualityStreaming?.let { appPreferences.setHighQualityStreaming(it) }
            prefs.autoQueueRelated?.let { appPreferences.setAutoQueueRelated(it) }
            // prefs.onboardingDone is ignored: a restore from the setup screen would otherwise close
            // setup before the user picks a music folder and a default source.
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
                            require(out.length <= MAX_FILE_CHARS) { "File is too large (max 8 MB)" }
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
            require(file.settings.size <= MAX_SETTINGS) { "Too many settings" }
            return file
        }

        companion object {
            const val FILE_NAME = "clibeats.json"
            const val BACKUP_FILE_NAME = "clibeats-backup.json"
            const val FAVORITES_NAME = "♥ Favorites"
            private const val LOCAL_FILE_PREFIX = "localpl:"
            private const val MAX_FILE_CHARS = 8 * 1024 * 1024
            private const val MAX_HISTORY = 20_000

            /** Plays read from a file: the kept ones plus one per older song. */
            private const val MAX_HISTORY_READ = 60_000
            private const val MAX_SETTINGS = 300
            private const val SQL_CHUNK = 500
            private const val READ_BUFFER_CHARS = 8 * 1024
            private const val MAX_PLAYLISTS = 200
            private const val MAX_TOTAL_TRACKS = 20_000
            private const val MAX_TRACKS_PER_PLAYLIST = 5_000
            private const val MAX_NAME_LENGTH = 200
            private const val MAX_TITLE_LENGTH = 500
            private const val MAX_SOURCE_ID_LENGTH = 512
            private const val MIN_CACHE_MB = 64
            private const val MAX_CACHE_MB = 4096
            private const val MIN_ARTWORK_MB = 32
            private const val MAX_ARTWORK_MB = 1024

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

/**
 * The play history as written in a backup: [plays] (song id, time; newest first) become each song once
 * (in [songs], first-played order) and every play pointing at its song. Plays of unknown songs are left out.
 */
internal fun packHistory(
    plays: List<Pair<String, Long>>,
    songs: Map<String, CliBeatsTrack>,
): Pair<List<CliBeatsTrack>, List<CliBeatsPlay>> {
    val order = plays.map { it.first }.distinct().filter { it in songs }
    val index = order.withIndex().associate { (i, id) -> id to i }
    val tracks = order.map { songs.getValue(it) }
    val entries = plays.mapNotNull { (id, at) -> index[id]?.let { CliBeatsPlay(it, at) } }
    return tracks to entries
}

/**
 * The backup's plays to add: each with its song ([tracks] by index; null = unusable), skipping bad times and plays
 * already here ([have]: song id and time), so restoring the same backup twice doesn't double the counts.
 */
internal fun playsToRestore(
    plays: List<CliBeatsPlay>,
    tracks: List<Track?>,
    have: Set<Pair<String, Long>>,
): List<Pair<Track, Long>> {
    val seen = HashSet(have)
    return plays.mapNotNull { play ->
        val track = tracks.getOrNull(play.track) ?: return@mapNotNull null
        if (play.at <= 0L || !seen.add(track.id to play.at)) return@mapNotNull null
        track to play.at
    }
}

/** The backup's downloaded songs worth offering: online ones not downloaded here ([have]), once each. */
internal fun downloadsToOffer(
    tracks: List<Track>,
    have: Set<String>,
): List<Track> = tracks.filter { it.id !in have && it.providerId != "local" }.distinctBy { it.id }

/**
 * Which plays a backup keeps ([plays]: song id and time, newest first): the newest [max], then the latest play of
 * each song not in those, so no played song is left out (only some of its older plays).
 */
internal fun historyToKeep(
    plays: List<Pair<String, Long>>,
    max: Int,
): List<Pair<String, Long>> {
    val recent = plays.take(max)
    val seen = recent.mapTo(HashSet()) { it.first }
    val older = plays.drop(max).filter { seen.add(it.first) }
    return recent + older
}
