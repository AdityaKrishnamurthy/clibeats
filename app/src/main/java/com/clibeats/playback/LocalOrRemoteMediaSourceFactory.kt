@file:Suppress("ForbiddenImport")

package com.clibeats.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/** Sends content:// and file:// items to [local] (no caching) and everything else to [remote]. */
@OptIn(UnstableApi::class)
class LocalOrRemoteMediaSourceFactory(
    private val local: MediaSource.Factory,
    private val remote: MediaSource.Factory,
) : MediaSource.Factory {
    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory =
        apply {
            local.setDrmSessionManagerProvider(provider)
            remote.setDrmSessionManagerProvider(provider)
        }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory =
        apply {
            local.setLoadErrorHandlingPolicy(policy)
            remote.setLoadErrorHandlingPolicy(policy)
        }

    override fun getSupportedTypes(): IntArray = remote.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val scheme = mediaItem.localConfiguration?.uri?.scheme
        return if (scheme == "content" || scheme == "file") {
            local.createMediaSource(mediaItem)
        } else {
            remote.createMediaSource(mediaItem)
        }
    }
}
