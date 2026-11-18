@file:Suppress("ForbiddenImport")

package com.clibeats.playback.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.clibeats.R
import com.clibeats.domain.repository.PlaybackRepository
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

    private var librarySession: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_stat_music_note)
            },
        )
        val session =
            MediaLibrarySession.Builder(this, player, LibraryCallback())
                .apply { sessionActivity()?.let(::setSessionActivity) }
                .build()
        librarySession = session
        // The app drives ExoPlayer directly (no MediaController), so register the session explicitly;
        // otherwise Media3 never posts the media notification.
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    /** Swiped away from recents: keep playing if music is on; otherwise stop (the resume card remains). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        // The ExoPlayer is a DI singleton owned by the app (PlayerAdapter), not by this service:
        // releasing it here would kill playback permanently.
        librarySession?.run {
            release()
            librarySession = null
        }
        super.onDestroy()
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
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootId = if (params?.isRecent == true) RECENT_ROOT else ROOT
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
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = afterRestore { currentQueue() }

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
        const val ROOT = "clibeats_root"
        const val RECENT_ROOT = "clibeats_recent"
    }
}
