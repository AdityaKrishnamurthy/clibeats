@file:Suppress("ForbiddenImport")

package com.clibeats.di

import com.clibeats.data.provider.LocalMusicProvider
import com.clibeats.data.provider.MusicProviderRegistry
import com.clibeats.data.provider.YouTubeMusicProvider
import com.clibeats.domain.provider.ProviderRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the [ProviderRegistry] — the single provider-selection mechanism.
 * Providers are registered in display-priority order; the active one is chosen
 * via AppPreferences and looked up by id.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {
    @Provides
    @Singleton
    fun provideProviderRegistry(
        youtube: YouTubeMusicProvider,
        local: LocalMusicProvider,
    ): ProviderRegistry = MusicProviderRegistry(listOf(youtube, local))
}
