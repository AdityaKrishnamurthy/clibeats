@file:Suppress("ForbiddenImport")

package com.clibeats.playback.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.clibeats.R
import com.clibeats.domain.repository.PlaybackRepository
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
 * Media3 owns the notification: it shows the current track's artwork (from MediaItem.artworkUri),
 * keeps the service in the foreground while playing, and taps open the app via the session activity.
 * Playback resumption: after the app is closed, Android 13+ can show a "resume" card; tapping it
 * restores the last saved queue through [MediaSession.Callback.onPlaybackResumption].
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var player: ExoPlayer

    @Inject lateinit var playbackRepository: PlaybackRepository

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_stat_music_note)
            },
        )
        val session =
            MediaSession.Builder(this, player)
                .setCallback(ResumptionCallback())
                .apply { sessionActivity()?.let(::setSessionActivity) }
                .build()
        mediaSession = session
        // The app drives ExoPlayer directly (no MediaController), so register the session explicitly;
        // otherwise Media3 never posts the media notification.
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Swiped away from recents: keep playing if music is on; otherwise stop (resume card remains). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        // The ExoPlayer is a DI singleton owned by the app (PlayerAdapter), not by this service:
        // releasing it here would kill playback permanently.
        mediaSession?.run {
            release()
            mediaSession = null
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

    private inner class ResumptionCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                runCatching {
                    // Restores the saved queue into the shared player (keeps the app UI in sync).
                    playbackRepository.awaitRestored()
                    val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
                    MediaSession.MediaItemsWithStartPosition(
                        items,
                        player.currentMediaItemIndex.coerceAtLeast(0),
                        player.currentPosition.coerceAtLeast(0L),
                    )
                }.fold(result::set, result::setException)
            }
            return result
        }
    }
}
