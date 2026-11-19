package com.clibeats.data.update

import kotlinx.serialization.Serializable

/** The parts of a GitHub release the updater needs. Cached in preferences as JSON. */
@Serializable
data class ReleaseInfo(
    /** Version without the leading "v", e.g. "2.1.0". */
    val version: String,
    val apkName: String,
    val apkUrl: String,
    /** URL of `SHA256SUMS.txt`; null when the release has none (download is then refused). */
    val checksumsUrl: String?,
    /** The release page on GitHub (release notes). */
    val pageUrl: String,
)

/** How often the app checks GitHub on launch. */
enum class UpdateFrequency(
    val label: String,
    val intervalMs: Long,
) {
    DAILY("DAILY", DAY_MS),
    WEEKLY("WEEKLY", DAYS_PER_WEEK * DAY_MS),
    OFF("OFF", Long.MAX_VALUE),
}

/** State of the in-app updater, shared by the launch pop-up and Settings → Updates. */
sealed interface UpdateState {
    /** Nothing to show (no check yet, or an automatic check found nothing). */
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(
        val release: ReleaseInfo,
    ) : UpdateState

    /** [progress] is 0–100, or -1 while the size is still unknown. */
    data class Downloading(
        val release: ReleaseInfo,
        val progress: Int,
    ) : UpdateState

    data class Verifying(
        val release: ReleaseInfo,
    ) : UpdateState

    /** Downloaded to Downloads and the SHA-256 matched the release's checksum. */
    data class ReadyToInstall(
        val release: ReleaseInfo,
    ) : UpdateState

    data class Failed(
        val message: String,
        val release: ReleaseInfo? = null,
    ) : UpdateState
}

const val DAY_MS = 24L * 60 * 60 * 1000
private const val DAYS_PER_WEEK = 7
