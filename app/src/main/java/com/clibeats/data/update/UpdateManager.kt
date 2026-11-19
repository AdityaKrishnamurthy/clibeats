@file:Suppress("ForbiddenImport", "MagicNumber", "TooManyFunctions")

package com.clibeats.data.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.clibeats.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks GitHub Releases for a newer version and, if found, downloads the APK and
 * hands it to the system package installer — all without leaving the app.
 *
 * Flow:
 *  1. [checkForUpdate] hits the GitHub Releases API (latest release endpoint).
 *  2. If the remote tag is newer than [BuildConfig.VERSION_NAME], emits [UpdateState.UpdateAvailable].
 *  3. [downloadAndInstall] streams the APK into `filesDir/updates/` while emitting [UpdateState.Downloading].
 *  4. On completion it fires an `ACTION_VIEW` intent via [FileProvider] so the system installer
 *     takes over without the user ever leaving the app context.
 *
 * The class is [Singleton] so the same state is shared between the Settings screen and any
 * notification/background trigger.
 */
@Singleton
class UpdateManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
    ) {
        private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
        val state: StateFlow<UpdateState> = _state.asStateFlow()

        // ── GitHub API ──────────────────────────────────────────────────────────

        @Serializable
        private data class GitHubRelease(
            @SerialName("tag_name") val tagName: String,
            @SerialName("body") val body: String? = null,
            @SerialName("assets") val assets: List<GitHubAsset> = emptyList(),
        )

        @Serializable
        private data class GitHubAsset(
            @SerialName("name") val name: String,
            @SerialName("browser_download_url") val downloadUrl: String,
            @SerialName("content_type") val contentType: String = "",
        )

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Fetches the latest GitHub release and updates [state].
         * Safe to call from any coroutine context; IO is dispatched internally.
         */
        suspend fun checkForUpdate() {
            if (_state.value is UpdateState.Checking) return
            _state.value = UpdateState.Checking
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        Request.Builder()
                            .url(RELEASES_API_URL)
                            .header("Accept", "application/vnd.github+json")
                            .header("X-GitHub-Api-Version", "2022-11-28")
                            .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            _state.value = UpdateState.Error("Server error ${response.code}")
                            return@withContext
                        }
                        val body = response.body?.string() ?: run {
                            _state.value = UpdateState.Error("Empty response from server")
                            return@withContext
                        }
                        val release = json.decodeFromString<GitHubRelease>(body)
                        val remoteVersion = release.tagName.trimStart('v', 'V')
                        val localVersion = BuildConfig.VERSION_NAME

                        if (isNewerVersion(remoteVersion, localVersion)) {
                            val apkAsset = release.assets.firstOrNull { asset ->
                                asset.name.endsWith(".apk", ignoreCase = true)
                            }
                            if (apkAsset == null) {
                                _state.value = UpdateState.Error("No APK in release ${release.tagName}")
                            } else {
                                _state.value = UpdateState.UpdateAvailable(
                                    version = release.tagName,
                                    apkUrl = apkAsset.downloadUrl,
                                    changelog = (release.body ?: "").take(CHANGELOG_MAX_CHARS),
                                )
                            }
                        } else {
                            _state.value = UpdateState.UpToDate
                        }
                    }
                }.onFailure { e ->
                    _state.value = UpdateState.Error(e.message ?: "Unknown error")
                }
            }
        }

        /**
         * Downloads the APK from [apkUrl] into the app's private storage and launches
         * the system installer. Progress is reflected in [state].
         *
         * On Android 8+ the user is prompted to allow installs from this source once;
         * the [REQUEST_INSTALL_PACKAGES] permission in the manifest makes this possible.
         */
        suspend fun downloadAndInstall(apkUrl: String) {
            _state.value = UpdateState.Downloading(0)
            withContext(Dispatchers.IO) {
                runCatching {
                    val updateDir = File(context.filesDir, "updates").also { it.mkdirs() }
                    val apkFile = File(updateDir, "clibeats-update.apk")

                    val request = Request.Builder().url(apkUrl).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            _state.value = UpdateState.Error("Download failed: HTTP ${response.code}")
                            return@withContext
                        }
                        val body = response.body ?: run {
                            _state.value = UpdateState.Error("Empty download response")
                            return@withContext
                        }
                        val totalBytes = body.contentLength()
                        var downloadedBytes = 0L

                        apkFile.outputStream().buffered().use { out ->
                            body.byteStream().buffered().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    out.write(buffer, 0, read)
                                    downloadedBytes += read
                                    if (totalBytes > 0) {
                                        val pct = ((downloadedBytes * 100) / totalBytes).toInt()
                                        _state.value = UpdateState.Downloading(pct)
                                    }
                                }
                            }
                        }
                    }

                    // Hand off to the system installer via a content:// URI (required for Android 7+).
                    val apkUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile,
                    )
                    val installIntent =
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(installIntent)
                    _state.value = UpdateState.ReadyToInstall
                }.onFailure { e ->
                    _state.value = UpdateState.Error(e.message ?: "Download error")
                }
            }
        }

        /** Resets to [UpdateState.Idle], e.g. when the user dismisses the dialog. */
        fun reset() {
            _state.value = UpdateState.Idle
        }

        // ── Helpers ─────────────────────────────────────────────────────────────

        /**
         * Compares semantic version strings (major.minor.patch).
         * Returns true only when [remote] is strictly newer than [local].
         */
        private fun isNewerVersion(remote: String, local: String): Boolean {
            val remoteParts = remote.split('.').mapNotNull { it.toIntOrNull() }
            val localParts = local.split('.').mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            return false
        }

        companion object {
            private const val RELEASES_API_URL =
                "https://api.github.com/repos/Omprakash-p06/clibeats/releases/latest"
            private const val CHANGELOG_MAX_CHARS = 2_000
            private const val DEFAULT_BUFFER_SIZE = 8_192
        }
    }
