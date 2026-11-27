@file:Suppress("ForbiddenImport")

package com.clibeats.data.backup

import android.content.Context
import com.clibeats.data.playlist.CliBeatsFile
import com.clibeats.data.playlist.CliBeatsPlaylist
import com.clibeats.data.playlist.PlaylistExchangeManager
import com.clibeats.data.preferences.AppPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class LibrarySnapshotManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private val context: Context = mock()
    private val exchange: PlaylistExchangeManager = mock()
    private val appPreferences: AppPreferences = mock()
    private lateinit var manager: LibrarySnapshotManager

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(context.cacheDir).thenReturn(tempFolder.newFolder("cache"))
        whenever(appPreferences.mirrorBackupsToDownloads).thenReturn(flowOf(false))
        manager = LibrarySnapshotManager(context, exchange, appPreferences)
    }

    @Test
    fun `snapshotIfDue returns null and avoids overwriting when library is empty`() =
        runTest {
            whenever(exchange.buildFile()).thenReturn(CliBeatsFile())

            val result = manager.snapshotIfDue()

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isNull()
            assertThat(manager.hasSnapshot()).isFalse()
            verify(exchange, never()).exportToFile(any())
        }

    @Test
    fun `snapshotIfDue writes snapshot rotates previous copy and skips when still fresh`() =
        runTest {
            val nonEmpty = CliBeatsFile(playlists = listOf(CliBeatsPlaylist(name = "Mix")))
            whenever(exchange.buildFile()).thenReturn(nonEmpty)
            whenever(exchange.exportToFile(any())).doAnswer { inv ->
                val target = inv.getArgument<File>(0)
                target.parentFile?.mkdirs()
                target.writeText("""{"version":2}""")
                Result.success(1)
            }

            val first = manager.snapshotIfDue()
            assertThat(first.getOrNull()).isEqualTo(manager.latestFile)
            assertThat(manager.hasSnapshot()).isTrue()

            // Within 24h, non-forced run skips export.
            val skipped = manager.snapshotIfDue(force = false)
            assertThat(skipped.getOrNull()).isNull()

            // A forced run replaces the backup in place: still one file, no spare copy.
            val forced = manager.snapshotIfDue(force = true)
            assertThat(forced.getOrNull()).isEqualTo(manager.latestFile)
            assertThat(File(manager.latestFile.parentFile, "clibeats-backup.prev.json").isFile).isFalse()
            assertThat(manager.latestFile.parentFile?.listFiles()?.size).isEqualTo(1)
        }

    @Test
    fun `clearBackups deletes saved snapshot folder`() =
        runTest {
            manager.latestFile.parentFile?.mkdirs()
            manager.latestFile.writeText("{}")
            assertThat(manager.hasSnapshot()).isTrue()

            manager.clearBackups()

            assertThat(manager.hasSnapshot()).isFalse()
        }
}
