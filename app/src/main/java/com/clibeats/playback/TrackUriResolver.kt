@file:Suppress("ForbiddenImport")

package com.clibeats.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.clibeats.data.cache.CacheManager
import com.clibeats.domain.model.Track
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Lazily turns `clibeats://track?p=<providerId>&id=<trackId>` placeholder URIs into real
 * stream URLs when ExoPlayer opens the item. Runs on ExoPlayer's loader thread, so
 * blocking here never touches the main thread. Every queue entry becomes playable
 * without resolving the whole queue up front, and expired URLs are re-resolved.
 */
@OptIn(UnstableApi::class)
class TrackUriResolver(
    private val streamResolver: dagger.Lazy<StreamResolver>,
    private val cacheManager: dagger.Lazy<CacheManager>,
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != SCHEME) return dataSpec
        val providerId = uri.getQueryParameter("p").orEmpty()
        val trackId = uri.getQueryParameter("id") ?: throw IOException("Missing track id in $uri")

        val resolved =
            runBlocking {
                cacheManager.get().getCachedFile(trackId)?.takeIf { it.exists() }?.let { Uri.fromFile(it) }
                    ?: runCatching {
                        streamResolver.get().resolve(placeholderTrack(trackId, providerId)).streamUrl
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        throw IOException("Stream resolution failed for $trackId", error)
                    }
                        ?.let { Uri.parse(it) }
            } ?: throw IOException("No stream URL for $trackId")
        return dataSpec.withUri(resolved)
    }

    companion object {
        const val SCHEME = "clibeats"

        fun placeholderUri(track: Track): Uri =
            Uri.Builder()
                .scheme(SCHEME)
                .authority("track")
                .appendQueryParameter("p", track.providerId)
                .appendQueryParameter("id", track.id)
                .build()

        private fun placeholderTrack(
            id: String,
            providerId: String,
        ) = Track(
            id = id,
            title = "",
            artist = "",
            album = "",
            durationMs = 0L,
            artworkUrl = null,
            streamUrl = null,
            providerId = providerId,
        )
    }
}
