@file:Suppress("ForbiddenImport")

package com.clibeats.playback.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import coil.ImageLoader
import com.clibeats.R
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.repository.LibraryRepository
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.playback.ArtworkBitmapLoader
import com.clibeats.playback.PlayerMediaBridge
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Background playback + system media controls (notification shade, lock screen, Bluetooth).
 *
 * Media3 owns the notification: high-res artwork (MediaItem.artworkUri), foreground while playing,
 * taps open the app via the session activity.
 *
 * Why a MediaLibraryService: Android's System UI only keeps a "resume" card for apps that answer a
 * media-browser request for the *recent* item (root hint EXTRA_RECENT). A plain MediaSessionService
 * doesn't, so the card never appeared. Here the recent root returns the last played song, and
 * tapping the card restores the saved queue via [MediaSession.Callback.onPlaybackResumption].
 */
@OptIn(UnstableApi::class)
@Suppress("TooManyFunctions") // session callbacks plus the notification buttons
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject lateinit var player: ExoPlayer

    @Inject lateinit var playbackRepository: PlaybackRepository

    @Inject lateinit var imageLoader: ImageLoader

    @Inject lateinit var libraryRepository: LibraryRepository

    @Inject lateinit var androidAutoBrowser: AndroidAutoBrowser

    /** Kept alive by the service so the effect follows the player's audio session. */
    @Inject lateinit var equalizerController: com.clibeats.playback.EqualizerController

    /** Whether the playing song is in Favorites; drives the heart on the notification. */
    @Volatile private var currentLiked = false

    private var librarySession: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_stat_clibeats)
            },
        )
        val session =
            MediaLibrarySession.Builder(this, previousFromHistory(player), LibraryCallback())
                .setBitmapLoader(
                    // Cached covers with fallbacks; re-draw the notification when the high-res one arrives.
                    ArtworkBitmapLoader(this, imageLoader) { source -> scope.launch { showSharpArtwork(source) } },
                ).apply { sessionActivity()?.let(::setSessionActivity) }
                .build()
        librarySession = session
        session.setCustomLayout(customLayout())
        player.addListener(repeatListener)
        watchFavorite()
        // The app drives ExoPlayer directly (no MediaController), so register the session explicitly;
        // otherwise Media3 never posts the media notification.
        addSession(session)
    }

    /**
     * The high-res cover landed after a smaller one was shown. The notification is simply redrawn, but the
     * lock screen and the media card only reload artwork when the song's metadata changes, so the current
     * item gets the same cover URL with a `#hq` marker. Same stream URI, so ExoPlayer updates it in place
     * (no re-buffering); the loader then hands back the sharp cover it already holds.
     */
    private fun showSharpArtwork(source: String) {
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val artworkUri = metadata?.artworkUri
        val sameSong = item != null && metadata?.extras?.getString(ArtworkBitmapLoader.ARTWORK_SOURCE_KEY) == source
        if (sameSong && artworkUri != null && artworkUri.fragment != SHARP_ARTWORK) {
            val sharpUri = artworkUri.buildUpon().fragment(SHARP_ARTWORK).build()
            val sharp = metadata.buildUpon().setArtworkUri(sharpUri).build()
            player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setMediaMetadata(sharp).build())
        }
        librarySession?.let { onUpdateNotification(it) }
    }

    // Every controller is accepted on purpose. A package allow-list breaks real controls: Samsung's lock
    // screen and Edge panel, Galaxy/other watches, car head units, scrobblers, and platform controllers
    // that Media3 reports under a placeholder package name. Controllers can only play/pause/skip.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    /**
     * Notification, lock-screen and headset "previous" and "next" behave exactly like the in-app buttons:
     * previous goes through the play history (whatever played before, from any queue) and stays available at
     * the start of a queue; next returns to the songs you went back from before following the queue.
     */
    private fun previousFromHistory(player: Player): Player =
        object : ForwardingPlayer(player) {
            override fun seekToPrevious() = playbackRepository.skipToPrevious()

            override fun seekToPreviousMediaItem() = playbackRepository.skipToPrevious()

            override fun seekToNext() = playbackRepository.skipToNext()

            override fun seekToNextMediaItem() = playbackRepository.skipToNext()

            // Shuffle from a headset, car or watch reorders the queue the same way as the in-app button.
            override fun setShuffleModeEnabled(enabled: Boolean) {
                if (enabled != playbackRepository.playbackState.value.shuffleEnabled) playbackRepository.toggleShuffle()
            }

            override fun getShuffleModeEnabled(): Boolean = playbackRepository.playbackState.value.shuffleEnabled

            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands()
                    .buildUpon()
                    .apply { NAV_COMMANDS.forEach(::add) }
                    .build()

            override fun isCommandAvailable(command: Int): Boolean {
                val navigation = command in NAV_COMMANDS
                return navigation || super.isCommandAvailable(command)
            }
        }

    /**
     * Swiped away from recents: keep the service and session alive (playing or paused) so the media
     * card's play button talks to a live player and resumes the same song. Only stop when there's
     * nothing to play. If Android later kills the process, the resume card restores via
     * [LibraryCallback.onPlaybackResumption].
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "taskRemoved playing=${player.isPlaying} items=${player.mediaItemCount}")
        if (player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        player.removeListener(repeatListener)
        // The ExoPlayer is a DI singleton owned by the app (PlayerAdapter), not by this service:
        // releasing it here would kill playback permanently.
        librarySession?.run {
            release()
            librarySession = null
        }
        super.onDestroy()
    }

    /** Keeps the repeat button's icon in step with the mode, whichever screen or device changed it. */
    private val repeatListener =
        object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                librarySession?.setCustomLayout(customLayout())
            }
        }

    /** Follows the playing song's ♥ (changed here, in the app, or from another song) and redraws the heart. */
    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private fun watchFavorite() {
        scope.launch {
            playbackRepository.playbackState
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(false) else libraryRepository.isLiked(id) }
                .collect { liked ->
                    currentLiked = liked
                    librarySession?.setCustomLayout(customLayout())
                }
        }
    }

    /** Notification / lock-screen buttons next to play controls: repeat, then ♥ favourite. */
    private fun customLayout(): List<CommandButton> = repeatLayout() + favoriteButton()

    private fun favoriteButton(): CommandButton =
        CommandButton.Builder()
            .setDisplayName(if (currentLiked) "Remove from favorites" else "Add to favorites")
            .setIconResId(
                if (currentLiked) {
                    androidx.media3.session.R.drawable.media3_icon_heart_filled
                } else {
                    androidx.media3.session.R.drawable.media3_icon_heart_unfilled
                },
            ).setSessionCommand(FAVORITE_COMMAND)
            .build()

    /** Same as tapping ♥ in Now Playing: adds the playing song to Favorites, or removes it. */
    private fun toggleFavorite() {
        val track = playbackRepository.playbackState.value.currentTrack ?: return
        scope.launch { runCatching { libraryRepository.toggleLike(track) } }
    }

    /** The repeat button on the notification / lock-screen card; its icon shows the current mode. */
    private fun repeatLayout(): List<CommandButton> {
        val (icon, label) =
            when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> androidx.media3.session.R.drawable.media3_icon_repeat_all to "Repeat: all"
                Player.REPEAT_MODE_ONE -> androidx.media3.session.R.drawable.media3_icon_repeat_one to "Repeat: one"
                else -> androidx.media3.session.R.drawable.media3_icon_repeat_off to "Repeat: off"
            }
        return listOf(
            CommandButton.Builder()
                .setDisplayName(label)
                .setIconResId(icon)
                .setSessionCommand(REPEAT_COMMAND)
                .build(),
        )
    }

    /** Same order as the in-app button: off → all → one → off. */
    private fun cycleRepeat() {
        val next =
            when (playbackRepository.playbackState.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        playbackRepository.setRepeatMode(next)
    }

    /** Tapping the notification / lock-screen card brings the existing app task to the front. */
    private fun sessionActivity(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

    /** Runs [block] after the saved queue is restored into the shared player, as a ListenableFuture. */
    private fun <T> afterRestore(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            runCatching {
                playbackRepository.awaitRestored()
                block()
            }.fold(result::set, result::setException)
        }
        return result
    }

    private fun queueItems(): List<MediaItem> = (0 until player.mediaItemCount).map(player::getMediaItemAt)

    private fun currentQueue(startId: String? = null): MediaSession.MediaItemsWithStartPosition {
        val items = queueItems()
        val requested = startId?.let { id -> items.indexOfFirst { it.mediaId == id } }?.takeIf { it >= 0 }
        val index = requested ?: player.currentMediaItemIndex.coerceAtLeast(0)
        val resumesCurrent = requested == null || requested == player.currentMediaItemIndex
        val position = if (resumesCurrent) player.currentPosition else 0L
        return MediaSession.MediaItemsWithStartPosition(items, index, position.coerceAtLeast(0L))
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            Log.i(TAG, "connect from ${controller.packageName} items=${player.mediaItemCount}")
            // Let controllers (notification, lock screen) send the repeat button's command.
            val commands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    .add(REPEAT_COMMAND)
                    .add(FAVORITE_COMMAND)
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(customLayout())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                REPEAT_ACTION -> cycleRepeat()
                FAVORITE_ACTION -> toggleFavorite()
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            Log.i(TAG, "libraryRoot recent=${params?.isRecent} from ${browser.packageName}")
            return afterRestore {
                LibraryResult.ofItem(androidAutoBrowser.libraryRoot(params?.isRecent == true), params)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            afterRestore {
                // Recent root is the System UI resume card; everything else is the Auto browse tree.
                val items = queueItems()
                val children =
                    if (parentId == RECENT_ROOT) {
                        listOfNotNull(items.getOrNull(player.currentMediaItemIndex) ?: items.firstOrNull())
                            .map(::asPlayable)
                    } else {
                        androidAutoBrowser.children(parentId, items, player.currentMediaItemIndex)
                    }
                LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
            }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            afterRestore {
                queueItems().firstOrNull { it.mediaId == mediaId }?.let { LibraryResult.ofItem(asPlayable(it), null) }
                    ?: androidAutoBrowser.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                    ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }

        /** Resume card tapped (or a media button after the app was closed): restore the saved queue. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Log.i(TAG, "playbackResumption from ${controller.packageName}")
            return afterRestore {
                currentQueue().also { Log.i(TAG, "resuming ${it.mediaItems.size} items at ${it.startIndex}") }
            }
        }

        /**
         * A controller asked to play a browsed item by id (items from the browser have no URI).
         * Answer with the whole restored queue starting at that item, so the app's queue stays in sync.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (mediaItems.all { it.localConfiguration != null }) {
                return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }
            val requestedIds = mediaItems.map { it.mediaId }
            val index = startIndex.coerceIn(0, requestedIds.lastIndex.coerceAtLeast(0))
            return afterRestore {
                val inQueue = queueItems().associateBy { it.mediaId }
                if (requestedIds.all(inQueue::containsKey)) {
                    currentQueue(requestedIds.getOrNull(index))
                } else {
                    // A song from Recently Played, Playlists or Library: hand it to the normal queue
                    // pipeline, and answer immediately with playable items so the head unit can start.
                    val tracks = androidAutoBrowser.resolveTracks(requestedIds)
                    if (tracks.isEmpty()) return@afterRestore currentQueue(requestedIds.getOrNull(index))
                    playbackRepository.playFrom(tracks, index, "Android Auto")
                    MediaSession.MediaItemsWithStartPosition(
                        tracks.map { PlayerMediaBridge.buildMediaItem(it, downloadedPath = null) },
                        index.coerceIn(tracks.indices),
                        startPositionMs.coerceAtLeast(0L),
                    )
                }
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            afterRestore {
                val byId = queueItems().associateBy { it.mediaId }
                val missing = mediaItems.filter { it.localConfiguration == null && it.mediaId !in byId }
                val browsed =
                    androidAutoBrowser.resolveTracks(missing.map { it.mediaId })
                        .associateBy { it.id }
                        .mapValues { (_, track) -> PlayerMediaBridge.buildMediaItem(track, downloadedPath = null) }
                mediaItems.mapNotNull { item ->
                    item.takeIf { it.localConfiguration != null } ?: byId[item.mediaId] ?: browsed[item.mediaId]
                }.toMutableList()
            }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = Futures.immediateFuture(LibraryResult.ofVoid())

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            afterRestore {
                LibraryResult.ofItemList(
                    ImmutableList.copyOf(androidAutoBrowser.search(query, pageSize.coerceAtLeast(1))),
                    params,
                )
            }
    }

    private fun asPlayable(item: MediaItem): MediaItem =
        item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setIsPlayable(true).setIsBrowsable(false).build())
            .build()

    private companion object {
        const val TAG = "CLIBeatsSession"
        const val SHARP_ARTWORK = "hq"
        const val RECENT_ROOT = AndroidAutoBrowser.RECENT_ROOT
        const val REPEAT_ACTION = "clibeats.REPEAT"
        val REPEAT_COMMAND = SessionCommand(REPEAT_ACTION, Bundle.EMPTY)
        const val FAVORITE_ACTION = "clibeats.FAVORITE"
        val FAVORITE_COMMAND = SessionCommand(FAVORITE_ACTION, Bundle.EMPTY)
    }
}

private val NAV_COMMANDS =
    listOf(
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    )
