@file:Suppress("ForbiddenImport")

package com.clibeats.playback.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.playback.ArtworkBitmapLoader
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject lateinit var player: ExoPlayer

    @Inject lateinit var playbackRepository: PlaybackRepository

    @Inject lateinit var imageLoader: ImageLoader

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
                    ArtworkBitmapLoader(this, imageLoader) {
                        scope.launch { librarySession?.let { onUpdateNotification(it) } }
                    },
                ).apply { sessionActivity()?.let(::setSessionActivity) }
                .build()
        librarySession = session
        session.setCustomLayout(repeatLayout())
        player.addListener(repeatListener)
        // The app drives ExoPlayer directly (no MediaController), so register the session explicitly;
        // otherwise Media3 never posts the media notification.
        addSession(session)
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
                librarySession?.setCustomLayout(repeatLayout())
            }
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
    private fun <T> afterRestore(block: () -> T): ListenableFuture<T> {
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
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(repeatLayout())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != REPEAT_ACTION) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            cycleRepeat()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootId = if (params?.isRecent == true) RECENT_ROOT else ROOT
            Log.i(TAG, "libraryRoot recent=${params?.isRecent} from ${browser.packageName}")
            val root =
                MediaItem.Builder()
                    .setMediaId(rootId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("CLIBeats")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .build(),
                    ).build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
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
                // Recent root: the last played song (shown on the resume card). Otherwise: the queue.
                val items = queueItems()
                val children =
                    if (parentId == RECENT_ROOT) {
                        listOfNotNull(items.getOrNull(player.currentMediaItemIndex) ?: items.firstOrNull())
                    } else {
                        items
                    }
                LibraryResult.ofItemList(ImmutableList.copyOf(children.map(::asPlayable)), params)
            }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            afterRestore {
                queueItems().firstOrNull { it.mediaId == mediaId }?.let { LibraryResult.ofItem(asPlayable(it), null) }
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
            val requestedId = mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId
            return afterRestore { currentQueue(requestedId) }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            afterRestore {
                val byId = queueItems().associateBy { it.mediaId }
                mediaItems.mapNotNull { it.takeIf { item -> item.localConfiguration != null } ?: byId[it.mediaId] }
                    .toMutableList()
            }
    }

    private fun asPlayable(item: MediaItem): MediaItem =
        item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setIsPlayable(true).setIsBrowsable(false).build())
            .build()

    private companion object {
        const val TAG = "CLIBeatsSession"
        const val ROOT = "clibeats_root"
        const val RECENT_ROOT = "clibeats_recent"
        const val REPEAT_ACTION = "clibeats.REPEAT"
        val REPEAT_COMMAND = SessionCommand(REPEAT_ACTION, Bundle.EMPTY)
    }
}

private val NAV_COMMANDS =
    listOf(
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    )
