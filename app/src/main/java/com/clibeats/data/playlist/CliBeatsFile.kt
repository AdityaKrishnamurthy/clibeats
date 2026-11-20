package com.clibeats.data.playlist

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Portable playlist exchange format (`clibeats.json`).
 *
 * The format is provider-agnostic: tracks carry `providerId` + `sourceId` so a
 * new device can attempt provider resolution on import. No server required.
 *
 * Version 2 adds optional `prefs`, `albums` and `artists` so a folder snapshot can
 * restore the whole library. Every new field has a default, so v1 files still read.
 */
@Serializable
data class CliBeatsFile(
    val version: Int = CURRENT_VERSION,
    val playlists: List<CliBeatsPlaylist> = emptyList(),
    /** Non-secret settings only (theme, provider, cache limits, onboarding flag). */
    val prefs: CliBeatsPrefs? = null,
    val albums: List<CliBeatsAlbum> = emptyList(),
    val artists: List<CliBeatsArtist> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class CliBeatsPlaylist(
    val name: String,
    val tracks: List<CliBeatsTrack> = emptyList(),
)

@Serializable
data class CliBeatsTrack(
    val providerId: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String? = null,
    val sourceUrl: String? = null,
)

/** Allow-listed preferences. Never add tokens, history, search queries or anything identifying. */
@Serializable
data class CliBeatsPrefs(
    val themeMode: String? = null,
    val accentColorName: String? = null,
    val activeProviderId: String? = null,
    val cacheMaxMb: Int? = null,
    val artworkCacheMb: Int? = null,
    val highQualityStreaming: Boolean? = null,
    val autoQueueRelated: Boolean? = null,
    val onboardingDone: Boolean? = null,
)

@Serializable
data class CliBeatsAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val providerId: String,
)

@Serializable
data class CliBeatsArtist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val providerId: String,
)

/** Pure encode/decode — kept free of Android dependencies so it is unit-testable on the JVM. */
object CliBeatsFileCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    fun encode(file: CliBeatsFile): String = json.encodeToString(file)

    fun decode(raw: String): CliBeatsFile = json.decodeFromString(raw)
}
