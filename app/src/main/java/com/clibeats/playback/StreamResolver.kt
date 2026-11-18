@file:Suppress("ForbiddenImport")

package com.clibeats.playback

import com.clibeats.data.provider.YouTubeMusicProvider
import com.clibeats.domain.model.PlaybackException
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.util.DiagnosticLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamResolver
    @Inject
    constructor(
        private val providerRegistry: ProviderRegistry,
    ) {
        suspend fun resolve(
            track: Track,
            traceId: String = DiagnosticLogger.generateTraceId(),
            forceRefresh: Boolean = false,
        ): Track {
            val existing = track.streamUrl
            if (!forceRefresh && !existing.isNullOrBlank() && !isStreamExpired(existing)) {
                return track
            }

            val provider =
                providerRegistry.getProvider(track.providerId)
                    ?: providerRegistry.defaultProvider()
            if (forceRefresh) (provider as? YouTubeMusicProvider)?.invalidateCache(track.id)

            return when (val result = provider.stream(track.id)) {
                is ProviderResult.Success -> track.copy(streamUrl = result.data)
                is ProviderResult.Error -> {
                    DiagnosticLogger.logError(traceId, "STREAM_RESOLUTION_FAILED", result.message)
                    throw PlaybackException.StreamResolutionFailed(result.message, result.cause)
                }
                else -> {
                    val msg = "Provider result is loading or unhandled"
                    DiagnosticLogger.logError(traceId, "STREAM_RESOLUTION_FAILED", msg)
                    throw PlaybackException.StreamResolutionFailed(msg)
                }
            }
        }

        /** Drops any cached URL so the next resolve hits the provider (used after HTTP 403). */
        fun invalidate(
            providerId: String,
            trackId: String,
        ) {
            (providerRegistry.getProvider(providerId) as? YouTubeMusicProvider)?.invalidateCache(trackId)
        }

        companion object {
            private const val EXPIRY_SAFETY_MS = 60_000L
            private val EXPIRE_PARAM = Regex("""[?&/]expire[=/](\d+)""")

            /** True when a signed URL's `expire=` epoch-seconds is past (or within 60s). */
            fun isStreamExpired(
                url: String,
                nowMs: Long = System.currentTimeMillis(),
            ): Boolean {
                val expireSec = EXPIRE_PARAM.find(url)?.groupValues?.get(1)?.toLongOrNull() ?: return false
                return expireSec * 1000L <= nowMs + EXPIRY_SAFETY_MS
            }
        }
    }
