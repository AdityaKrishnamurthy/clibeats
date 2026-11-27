@file:Suppress("ForbiddenImport")

package com.clibeats.di

import android.content.Context
import coil.ImageLoader
import coil.memory.MemoryCache
import com.clibeats.data.cache.ResizableDiskCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

private const val MEMORY_CACHE_PERCENT = 0.25
private const val BYTES_IN_MB = 1_048_576L
private const val DEFAULT_ARTWORK_CACHE_MB = 512L

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    /** Artwork disk cache; its limit (Settings → Storage) can change at runtime. */
    @Provides
    @Singleton
    fun provideArtworkDiskCache(
        @ApplicationContext context: Context,
    ): ResizableDiskCache =
        ResizableDiskCache(
            dir = context.cacheDir.resolve("image_cache"),
            maxBytes = DEFAULT_ARTWORK_CACHE_MB * BYTES_IN_MB,
        )

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        artworkDiskCache: ResizableDiskCache,
        downloadCovers: com.clibeats.data.download.DownloadCovers,
    ): ImageLoader =
        ImageLoader.Builder(context)
            // Downloaded songs' covers come from their kept files (offline, and safe from cache clears).
            .components { add(downloadCovers.interceptor) }
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache { artworkDiskCache }
            .crossfade(false)
            // Covers never change for a URL: always serve from disk without revalidating over the network.
            .respectCacheHeaders(false)
            .build()
}
