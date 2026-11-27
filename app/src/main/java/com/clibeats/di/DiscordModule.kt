@file:Suppress("ForbiddenImport")

package com.clibeats.di

import com.clibeats.data.discord.DiscordPresenceGateway
import com.clibeats.data.discord.UnavailableDiscordPresenceGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiscordModule {
    @Binds
    @Singleton
    abstract fun presenceGateway(gateway: UnavailableDiscordPresenceGateway): DiscordPresenceGateway
}
