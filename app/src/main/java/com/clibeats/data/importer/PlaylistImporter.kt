@file:Suppress("ForbiddenImport")

package com.clibeats.data.importer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.clibeats.R
import com.clibeats.data.local.dao.PlaylistDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.mapper.toEntity
import com.clibeats.data.provider.YouTubeMusicProvider
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.repository.PlaylistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One Spotify song and what the import found for it. [index] is its position in the Spotify playlist. */
data class ImportedSong(
    val index: Int,
    val song: SpotifySong,
    val match: Track?,
    /** True when YouTube Music couldn't be reached (no internet), as opposed to "no good result". */
    val searchFailed: Boolean = false,
    /** Picked by the user from the "Couldn't match" list. */
    val manual: Boolean = false,
)

data class PlaylistImport(
    val name: String,
    val total: Int,
    val results: List<ImportedSong> = emptyList(),
    /** Set once the playlist is saved. */
    val playlistId: String? = null,
    /** Cancelled before this playlist started: nothing was saved. */
    val skipped: Boolean = false,
) {
    val matched: List<ImportedSong> get() = results.filter { it.match != null }
    val missed: List<ImportedSong> get() = results.filter { it.match == null }.sortedBy { it.index }
    val done: Int get() = results.size
}

sealed interface ImportState {
    data object Idle : ImportState

    data class Running(
        val playlists: List<PlaylistImport>,
        val current: Int,
    ) : ImportState

    data class Finished(
        val playlists: List<PlaylistImport>,
        val cancelled: Boolean,
    ) : ImportState
}

/**
 * Turns Exportify playlists into CLIBeats playlists by finding each song on YouTube Music.
 *
 * Runs in its own app-wide scope, so it keeps going when the user leaves the import screen or locks the
 * phone (a progress notification shows it). Songs are searched a few at a time with a short pause, which
 * keeps YouTube from rate-limiting the app. The saved playlist keeps Spotify's song order.
 */
@Suppress("TooManyFunctions") // import steps plus its progress notification, kept together
@Singleton
class PlaylistImporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val youtube: YouTubeMusicProvider,
        private val playlistRepository: PlaylistRepository,
        private val playlistDao: PlaylistDao,
        private val songDao: SongDao,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
        val state: StateFlow<ImportState> = _state.asStateFlow()
        private var job: Job? = null

        /** Names of the user's own playlists, to warn before an import would duplicate one. */
        suspend fun existingNames(): Set<String> =
            playlistRepository.getAllPlaylistsAsFlow().first()
                .filter { it.isOwned && !it.id.startsWith(LOCAL_FILE_PREFIX) }
                .map { it.name }
                .toSet()

        /**
         * Starts importing [playlists]. With [replaceExisting], a playlist of yours with the same name is
         * replaced; otherwise the import gets a new name like "Nihon (2)". Ignored while an import runs.
         */
        fun start(
            playlists: List<SpotifyPlaylist>,
            replaceExisting: Boolean,
        ) {
            if (_state.value is ImportState.Running || playlists.isEmpty()) return
            _state.value = ImportState.Running(playlists.map { PlaylistImport(it.name, it.songs.size) }, current = 0)
            job =
                scope.launch {
                    var cancelled = false
                    try {
                        playlists.forEachIndexed { i, playlist ->
                            _state.update { (it as? ImportState.Running)?.copy(current = i) ?: it }
                            importOne(i, playlist, replaceExisting)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        cancelled = true
                        throw e
                    } finally {
                        withContext(NonCancellable) { finish(cancelled) }
                    }
                }
        }

        /** Stops the import. Songs matched so far in the current playlist are still saved. */
        fun cancel() {
            job?.cancel()
        }

        /** Clears the finished summary (the Done button). */
        fun dismiss() {
            if (_state.value is ImportState.Finished) _state.value = ImportState.Idle
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        /** YouTube Music results for the "Find" sheet of a song that didn't match. */
        suspend fun searchCandidates(query: String): List<Track> =
            (youtube.search(query, MANUAL_RESULTS) as? ProviderResult.Success)?.data.orEmpty()

        /** The user picked [track] for a song that didn't match: add it to the saved playlist in its Spotify spot. */
        suspend fun resolveManually(
            playlistIndex: Int,
            songIndex: Int,
            track: Track,
        ) {
            val finished = _state.value as? ImportState.Finished ?: return
            val playlist = finished.playlists.getOrNull(playlistIndex) ?: return
            val results =
                playlist.results.map {
                    if (it.index == songIndex) it.copy(match = track, manual = true, searchFailed = false) else it
                }
            songDao.insertAllIfAbsent(listOf(track.storable().toEntity()))
            // Nothing matched during the import, so no playlist was saved yet: create it now.
            val id =
                playlist.playlistId
                    ?: playlistRepository.createAppPlaylist(uniqueName(playlist.name, existingNames()), emptyList())
            // Rewrites the order from the import results, so the song lands where it was on Spotify.
            playlistDao.reorderPlaylistSongs(id, orderedIds(results))
            playlistDao.refreshTrackCount(id)
            _state.update { state ->
                if (state !is ImportState.Finished) return@update state
                state.copy(
                    playlists =
                        state.playlists.mapIndexed { i, p ->
                            if (i == playlistIndex) p.copy(results = results, playlistId = id) else p
                        },
                )
            }
        }

        private suspend fun importOne(
            index: Int,
            playlist: SpotifyPlaylist,
            replaceExisting: Boolean,
        ) {
            try {
                coroutineScope {
                    val gate = Semaphore(PARALLEL_SEARCHES)
                    playlist.songs.mapIndexed { songIndex, song ->
                        async {
                            gate.withPermit {
                                val result = matchSong(songIndex, song)
                                updatePlaylist(index) { it.copy(results = it.results + result) }
                                notifyProgress()
                                delay(PAUSE_BETWEEN_SEARCHES_MS)
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                // Also on cancel: keep what was matched so far.
                withContext(NonCancellable) { save(index, playlist.name, replaceExisting) }
            }
        }

        private suspend fun matchSong(
            index: Int,
            song: SpotifySong,
        ): ImportedSong {
            val main = song.artists.firstOrNull().orEmpty()
            val queries = listOf("${song.title} $main".trim(), song.title).distinct()
            var reached = false
            for (query in queries) {
                val results = searchWithRetry(query) ?: continue
                reached = true
                TrackMatcher.pick(song, results)?.let { return ImportedSong(index, song, it.storable()) }
            }
            return ImportedSong(index, song, match = null, searchFailed = !reached)
        }

        /** Search results, or null if YouTube Music couldn't be reached after a few tries. */
        private suspend fun searchWithRetry(query: String): List<Track>? {
            repeat(SEARCH_ATTEMPTS) { attempt ->
                when (val result = youtube.search(query, SEARCH_RESULTS)) {
                    is ProviderResult.Success -> return result.data
                    else -> delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
            return null
        }

        @Suppress("ReturnCount")
        private suspend fun save(
            index: Int,
            name: String,
            replaceExisting: Boolean,
        ) {
            val running = _state.value as? ImportState.Running ?: return
            val import = running.playlists.getOrNull(index) ?: return
            val matched = import.matched
            if (matched.isEmpty()) return
            songDao.insertAllIfAbsent(matched.mapNotNull { it.match?.toEntity() })
            val mine =
                playlistRepository.getAllPlaylistsAsFlow().first()
                    .filter { it.isOwned && !it.id.startsWith(LOCAL_FILE_PREFIX) }
            val finalName =
                if (replaceExisting) {
                    mine.filter { it.name == name }.forEach { playlistRepository.deletePlaylist(it.id) }
                    name
                } else {
                    uniqueName(name, mine.map { it.name }.toSet())
                }
            val id = playlistRepository.createAppPlaylist(finalName, orderedIds(import.results))
            updatePlaylist(index) { it.copy(name = finalName, playlistId = id) }
        }

        private fun finish(cancelled: Boolean) {
            val running = _state.value as? ImportState.Running ?: return
            val playlists =
                running.playlists.mapIndexed { i, p -> if (i > running.current) p.copy(skipped = true) else p }
            _state.value = ImportState.Finished(playlists, cancelled)
            notifyFinished(playlists, cancelled)
        }

        private fun updatePlaylist(
            index: Int,
            change: (PlaylistImport) -> PlaylistImport,
        ) {
            _state.update { state ->
                if (state !is ImportState.Running) return@update state
                state.copy(playlists = state.playlists.mapIndexed { i, p -> if (i == index) change(p) else p })
            }
        }

        /** Matched song ids in the Spotify playlist's order. */
        private fun orderedIds(results: List<ImportedSong>): List<String> {
            val inOrder = results.sortedBy { it.index }
            return inOrder.mapNotNull { it.match?.id }.distinct()
        }

        // ── Notification ────────────────────────────────────────────────────────────

        @Suppress("ReturnCount")
        private fun notifyProgress() {
            val running = _state.value as? ImportState.Running ?: return
            val playlist = running.playlists.getOrNull(running.current) ?: return
            // Every few songs is enough; posting on every one can get the app's notifications throttled.
            if (playlist.done % NOTIFY_EVERY != 0 && playlist.done != playlist.total) return
            val text =
                if (running.playlists.size > 1) {
                    "${playlist.name} · ${playlist.done} / ${playlist.total} " +
                        "(playlist ${running.current + 1} of ${running.playlists.size})"
                } else {
                    "${playlist.name} · ${playlist.done} / ${playlist.total}"
                }
            post(
                builder("Importing from Spotify", text)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(playlist.total, playlist.done, false),
            )
        }

        private fun notifyFinished(
            playlists: List<PlaylistImport>,
            cancelled: Boolean,
        ) {
            val done = playlists.filterNot { it.skipped }
            val text = done.joinToString(" · ") { "${it.name}: ${it.matched.size} of ${it.total} found" }
            post(builder(if (cancelled) "Import stopped" else "Import finished", text).setAutoCancel(true))
        }

        private fun builder(
            title: String,
            text: String,
        ): NotificationCompat.Builder {
            ensureChannel()
            val open =
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                    PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
                }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_clibeats)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setPriority(NotificationCompat.PRIORITY_LOW)
        }

        private fun post(builder: NotificationCompat.Builder) {
            val allowed =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!allowed) return
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
        }

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Playlist import", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }

        companion object {
            private const val PARALLEL_SEARCHES = 3
            private const val PAUSE_BETWEEN_SEARCHES_MS = 150L
            private const val SEARCH_RESULTS = 10
            private const val MANUAL_RESULTS = 15
            private const val SEARCH_ATTEMPTS = 3
            private const val RETRY_DELAY_MS = 1_000L
            private const val NOTIFY_EVERY = 5
            private const val NOTIFICATION_ID = 4_201
            private const val CHANNEL_ID = "playlist_import"
            private const val LOCAL_FILE_PREFIX = "localpl:"

            /** "Nihon" → "Nihon (2)" when "Nihon" is taken, and so on. */
            internal fun uniqueName(
                name: String,
                taken: Set<String>,
            ): String {
                if (name !in taken) return name
                var n = 2
                while ("$name ($n)" in taken) n++
                return "$name ($n)"
            }
        }
    }

/** Search results can carry an expiring stream URL; it's resolved again at playback, so don't store it. */
private fun Track.storable(): Track = copy(streamUrl = null)
