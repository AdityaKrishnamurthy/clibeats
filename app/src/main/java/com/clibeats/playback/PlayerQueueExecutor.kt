package com.clibeats.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track

/**
 * Coordinates atomic mutations between [QueueController] and [ExoPlayer]'s media item list.
 */
@Suppress("TooManyFunctions")
internal class PlayerQueueExecutor(
    private val player: ExoPlayer,
    private val queueController: QueueController,
    private val startPlaybackService: () -> Unit,
    private val updateState: () -> Unit,
    private val toMediaItem: (Track) -> MediaItem,
) {
    fun playTrack(track: Track) =
        synchronized(queueController) {
            startPlaybackService()
            val (index, replacedAll) = queueController.planPlayTrack(player.currentMediaItemIndex, track)
            val mediaItem = toMediaItem(track)
            if (!replacedAll) {
                player.addMediaItem(index, mediaItem)
                player.seekTo(index, 0L)
            } else {
                player.setMediaItem(mediaItem)
            }
            player.prepare()
            player.play()
            updateState()
        }

    fun setQueue(
        tracks: List<Track>,
        startIndex: Int,
        source: String?,
    ) = synchronized(queueController) {
        startPlaybackService()
        val (ordered, start) = queueController.replaceQueue(tracks, startIndex, source)
        player.setMediaItems(ordered.map(toMediaItem), start, 0L)
        player.prepare()
        player.play()
        updateState()
    }

    fun addToQueue(track: Track) =
        synchronized(queueController) {
            startPlaybackService()
            val index = queueController.addToQueue(player.currentMediaItemIndex, track)
            player.addMediaItem(index, toMediaItem(track))
            if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 1) {
                player.prepare()
            }
            updateState()
        }

    fun addAutoplayTracks(tracks: List<Track>) =
        synchronized(queueController) {
            if (tracks.isEmpty()) return@synchronized
            val endedAt = player.mediaItemCount.takeIf { player.playbackState == Player.STATE_ENDED }
            startPlaybackService()
            queueController.addAutoplayTracks(tracks)
            player.addMediaItems(tracks.map(toMediaItem))
            if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == tracks.size) {
                player.prepare()
            }
            if (endedAt != null && player.playWhenReady) {
                player.seekTo(endedAt, 0L)
                player.prepare()
                player.play()
            }
            updateState()
        }

    fun removeUpcomingRadio() =
        synchronized(queueController) {
            val removed = queueController.removeUpcomingRadio(player.currentMediaItemIndex)
            removed.forEach { idx ->
                if (idx < player.mediaItemCount) player.removeMediaItem(idx)
            }
            updateState()
        }

    fun addAllToQueue(tracks: List<Track>) =
        synchronized(queueController) {
            if (!queueController.addAll(tracks)) return@synchronized
            startPlaybackService()
            player.addMediaItems(tracks.map(toMediaItem))
            if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == tracks.size) {
                player.prepare()
            }
            updateState()
        }

    fun playNext(track: Track) =
        synchronized(queueController) {
            startPlaybackService()
            val targetIndex = queueController.playNext(player.currentMediaItemIndex, track)
            player.addMediaItem(targetIndex, toMediaItem(track))
            if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 1) {
                player.prepare()
            }
            updateState()
        }

    @Suppress("LongParameterList")
    fun restoreQueue(
        tracks: List<Track>,
        startIndex: Int,
        positionMs: Long,
        repeatMode: RepeatMode,
        shuffleEnabled: Boolean,
        radioIds: Set<String>,
    ) = synchronized(queueController) {
        if (tracks.isEmpty()) return@synchronized
        queueController.restoreQueue(tracks, radioIds, shuffleEnabled)
        val validIndex = startIndex.coerceIn(tracks.indices)
        player.setMediaItems(tracks.map(toMediaItem), validIndex, positionMs.coerceAtLeast(0L))
        player.repeatMode =
            when (repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            }
        player.shuffleModeEnabled = false
        updateState()
    }

    fun skipToIndex(index: Int) {
        if (index in 0 until queueController.size()) {
            player.seekTo(index, 0L)
            player.play()
        }
    }

    fun playForward(track: Track) =
        synchronized(queueController) {
            if (queueController.sourceOf(track.id) == null) queueController.setSource(track, HISTORY_SOURCE)
            val current = player.currentMediaItemIndex
            val existing = queueController.findNextIndexOf(track.id, current)
            if (existing != null) {
                player.seekTo(existing, 0L)
            } else {
                startPlaybackService()
                val index = queueController.insertAt((current + 1).coerceIn(0, queueController.size()), track)
                player.addMediaItem(index, toMediaItem(track))
                player.seekTo(index, 0L)
                player.prepare()
            }
            player.play()
            updateState()
        }

    fun playBeforeCurrent(track: Track) =
        synchronized(queueController) {
            if (queueController.sourceOf(track.id) == null) queueController.setSource(track, HISTORY_SOURCE)
            startPlaybackService()
            val targetIndex = player.currentMediaItemIndex.coerceIn(0, queueController.size())
            val index = queueController.insertAt(targetIndex, track)
            player.addMediaItem(index, toMediaItem(track))
            player.seekTo(index, 0L)
            player.prepare()
            player.play()
            updateState()
        }

    fun toggleShuffle() =
        synchronized(queueController) {
            val previousSize = queueController.size()
            val plan = queueController.toggleShuffle(player.currentMediaItemIndex)
            if (plan != null) {
                if (plan.previousCurrentIndex + 1 < previousSize) {
                    player.removeMediaItems(plan.previousCurrentIndex + 1, previousSize)
                }
                if (plan.previousCurrentIndex > 0) {
                    player.removeMediaItems(0, plan.previousCurrentIndex)
                }
                if (plan.before.isNotEmpty()) player.addMediaItems(0, plan.before.map(toMediaItem))
                if (plan.after.isNotEmpty()) player.addMediaItems(plan.after.map(toMediaItem))
            }
            updateState()
        }

    fun moveTrack(
        fromIndex: Int,
        toIndex: Int,
    ) = synchronized(queueController) {
        if (queueController.moveTrack(fromIndex, toIndex)) {
            player.moveMediaItem(fromIndex, toIndex)
            updateState()
        }
    }

    fun removeFromQueue(index: Int) =
        synchronized(queueController) {
            if (queueController.removeAt(index) != null) {
                player.removeMediaItem(index)
                updateState()
            }
        }

    fun refreshTracks(fresh: Map<String, Track>) =
        synchronized(queueController) {
            if (queueController.refreshTracks(fresh)) updateState()
        }

    fun insertAt(
        index: Int,
        track: Track,
    ) = synchronized(queueController) {
        val at = queueController.insertAt(index, track)
        player.addMediaItem(at, toMediaItem(track))
        updateState()
    }

    fun clearQueue() =
        synchronized(queueController) {
            val current = player.currentMediaItemIndex
            val sizeBefore = queueController.size()
            if (!queueController.clearExcept(current)) {
                player.clearMediaItems()
            } else {
                if (current + 1 < sizeBefore) player.removeMediaItems(current + 1, sizeBefore)
                if (current > 0) player.removeMediaItems(0, current)
            }
            updateState()
        }
}
