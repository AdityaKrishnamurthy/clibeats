@file:Suppress("ForbiddenImport")

package com.clibeats.di

import android.content.Context
import com.clibeats.data.download.DownloadStore
import com.clibeats.data.download.DownloadStreamFetcher
import com.clibeats.data.download.TrackDownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    fun provideTrackDownloadManager(
        @ApplicationContext context: Context,
        store: DownloadStore,
        fetcher: DownloadStreamFetcher,
    ): TrackDownloadManager =
        TrackDownloadManager(
            context = context,
            store = store,
            fetcher = fetcher,
        )
}
