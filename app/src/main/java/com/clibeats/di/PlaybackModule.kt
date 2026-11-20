@file:Suppress("ForbiddenImport")

package com.clibeats.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.clibeats.data.cache.CacheManager
import com.clibeats.data.cache.MediaCaches
import com.clibeats.playback.LocalOrRemoteMediaSourceFactory
import com.clibeats.playback.StreamResolver
import com.clibeats.playback.TrackUriResolver
import com.clibeats.playback.waveform.LoudnessTap
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    private const val MIN_BUFFER_MS = 15_000
    private const val MAX_BUFFER_MS = 50_000
    private const val BUFFER_FOR_PLAYBACK_MS = 500
    private const val BUFFER_AFTER_REBUFFER_MS = 1_500

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

    @Suppress("LongParameterList") // Hilt passes in what the player is built from.
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
        streamResolver: dagger.Lazy<StreamResolver>,
        cacheManager: dagger.Lazy<CacheManager>,
        mediaCaches: MediaCaches,
        loudnessTap: LoudnessTap,
    ): ExoPlayer {
        // Start playback after 0.5s of audio instead of Media3's 2.5s default.
        val loadControl =
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_AFTER_REBUFFER_MS,
                )
                .build()
        val resolvingFactory =
            ResolvingDataSource.Factory(
                DefaultDataSource.Factory(context),
                TrackUriResolver(streamResolver, cacheManager),
            )
        // Remote audio goes through the song cache. The cache key is the stable placeholder/track URI,
        // so a cached song replays without re-resolving (or any network). Local files skip the cache.
        val cachedFactory =
            CacheDataSource.Factory()
                .setCache(mediaCaches.songCache)
                .setUpstreamDataSourceFactory(resolvingFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val mediaSourceFactory =
            LocalOrRemoteMediaSourceFactory(
                local = DefaultMediaSourceFactory(DefaultDataSource.Factory(context)),
                remote = DefaultMediaSourceFactory(cachedFactory),
            )
        // Media3's default audio output plus a pass-through step that measures loudness for the
        // Now Playing waveform of streamed songs.
        val renderersFactory =
            object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink =
                    DefaultAudioSink
                        .Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(arrayOf(loudnessTap))
                        .build()
            }
        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }
}
