@file:Suppress("ForbiddenImport")

package com.clibeats.data.update

import android.app.DownloadManager
import android.content.Context
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.data.preferences.NO_DOWNLOAD
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UpdateManagerTest {
    private val context: Context = mock()
    private val client: GitHubReleaseClient = mock()
    private val preferences: AppPreferences = mock()
    private val downloadManager: DownloadManager = mock()
    private lateinit var manager: UpdateManager

    private val newerRelease =
        ReleaseInfo(
            version = "9.9.9",
            apkName = "CLIBeats-v9.9.9.apk",
            apkUrl = "https://github.com/Omprakash-p06/clibeats/releases/download/v9.9.9/CLIBeats-v9.9.9.apk",
            checksumsUrl = "https://github.com/Omprakash-p06/clibeats/releases/download/v9.9.9/SHA256SUMS.txt",
            pageUrl = "https://github.com/Omprakash-p06/clibeats/releases/tag/v9.9.9",
        )

    private fun record(frequency: String? = UpdateFrequency.DAILY.name) =
        AppPreferences.UpdateRecord(
            frequency = frequency,
            lastCheckAt = 0L,
            etag = null,
            cachedReleaseJson = null,
            snoozeUntil = 0L,
            downloadId = NO_DOWNLOAD,
            downloadVersion = null,
            downloadSha256 = null,
        )

    @Before
    fun setUp() {
        whenever(context.getSystemService(DownloadManager::class.java)).thenReturn(downloadManager)
        whenever(preferences.updateFrequency).thenReturn(flowOf(UpdateFrequency.DAILY.name))
        manager = UpdateManager(context, client, preferences)
    }

    @Test
    fun `checkNow transitions to Available when remote release is newer`() =
        runTest {
            whenever(preferences.updateRecord()).thenReturn(record())
            whenever(client.fetchLatest(null)).thenReturn(
                GitHubReleaseClient.Result.Found(newerRelease, etag = "\"v9\""),
            )

            manager.checkNow()

            withContext(Dispatchers.Default) {
                withTimeout(5_000L) {
                    manager.state.first { it is UpdateState.Available }
                }
            }
            assertThat(manager.state.value).isEqualTo(UpdateState.Available(newerRelease))
            verify(preferences, timeout(2_000L)).saveUpdateCheck(any(), eq("\"v9\""), any())
        }

    @Test
    fun `checkNow reports UpToDate when latest release matches or precedes installed`() =
        runTest {
            val older = newerRelease.copy(version = "1.0.0")
            whenever(preferences.updateRecord()).thenReturn(record())
            whenever(client.fetchLatest(null)).thenReturn(
                GitHubReleaseClient.Result.Found(older, etag = null),
            )

            manager.checkNow()

            withContext(Dispatchers.Default) {
                withTimeout(5_000L) {
                    manager.state.first { it is UpdateState.UpToDate }
                }
            }
            assertThat(manager.promptVisible.value).isFalse()
        }

    @Test
    fun `onAppStart skips remote check when frequency is OFF`() =
        runTest {
            whenever(preferences.updateRecord()).thenReturn(record(frequency = UpdateFrequency.OFF.name))

            manager.onAppStart()

            verify(preferences, timeout(2_000L)).updateRecord()
            withContext(Dispatchers.Default) { delay(50L) }
            verify(client, never()).fetchLatest(any())
            assertThat(manager.state.value).isEqualTo(UpdateState.Idle)
        }

    @Test
    fun `later hides prompt and snoozes updates`() =
        runTest {
            manager.later()
            assertThat(manager.promptVisible.value).isFalse()
            verify(preferences, timeout(2_000L)).setUpdateSnoozeUntil(any())
        }
}
