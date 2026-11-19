package com.clibeats.data.update

/**
 * Represents the current state of the in-app update flow.
 */
sealed class UpdateState {
    /** No update check has been started yet. */
    data object Idle : UpdateState()

    /** A check is in progress. */
    data object Checking : UpdateState()

    /** The app is already on the latest release. */
    data object UpToDate : UpdateState()

    /**
     * A newer release is available.
     *
     * @param version  The tag name from GitHub (e.g. "v2.1.0").
     * @param apkUrl   Direct browser-download URL for the release APK asset.
     * @param changelog Human-readable release body (truncated to 2 000 chars).
     */
    data class UpdateAvailable(
        val version: String,
        val apkUrl: String,
        val changelog: String,
    ) : UpdateState()

    /** An APK download is in progress; [progress] is 0–100. */
    data class Downloading(val progress: Int) : UpdateState()

    /** The APK has been downloaded and the system installer has been launched. */
    data object ReadyToInstall : UpdateState()

    /** Something went wrong; [message] is a human-readable reason. */
    data class Error(val message: String) : UpdateState()
}
