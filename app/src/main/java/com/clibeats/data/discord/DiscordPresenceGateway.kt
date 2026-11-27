package com.clibeats.data.discord

/**
 * Publishing seam for the official Discord Social SDK.
 *
 * Discord's Android Rich Presence is a C++ prefab AAR downloaded from the Developer Portal and
 * called through JNI; there is no user-token Gateway path here (self-bots violate Discord's ToS).
 * The default build has no AAR, so [UnavailableDiscordPresenceGateway] only reports
 * [DiscordSdkStatus.SDK_REQUIRED] and the Settings screen shows the preview.
 *
 * See docs/DISCORD_PRESENCE.md for adding the AAR, Application ID and native bridge.
 */
interface DiscordPresenceGateway {
    val sdkStatus: DiscordSdkStatus

    /** Publish [activity], or clear the presence when it is null. */
    fun publish(activity: DiscordActivity?)
}

/** Used when the Social SDK AAR is not part of the build. Never falls back to a user token. */
class UnavailableDiscordPresenceGateway
    @javax.inject.Inject
    constructor() : DiscordPresenceGateway {
        override val sdkStatus: DiscordSdkStatus = DiscordSdkStatus.SDK_REQUIRED

        override fun publish(activity: DiscordActivity?) = Unit
    }
