// ForbiddenImport: data-layer test legitimately imports sibling data packages; Phase 0 pattern is over-broad.
@file:Suppress("ForbiddenImport", "MaxLineLength")

package com.clibeats.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class AppPreferencesTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var appPreferences: AppPreferences
    private var tempFile: File? = null

    @Before
    fun setup() {
        dataStore = createTestDataStore()
        appPreferences = AppPreferences(dataStore)
    }

    @After
    fun tearDown() {
        tempFile?.delete()
    }

    private fun createTestDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher() + Job())) {
            File.createTempFile("clibeats_test", ".preferences_pb").also { tempFile = it }
        }

    @Test
    fun cacheMaxMb_defaultsTo512() =
        runTest {
            assertEquals(512, appPreferences.cacheMaxMb.first())
        }

    @Test
    fun setCacheMaxMb_roundTripsThroughDataStore() =
        runTest {
            appPreferences.setCacheMaxMb(2560)

            assertEquals(2560, appPreferences.cacheMaxMb.first())
        }

    @Test
    fun cacheLimits_belowTheSmallestOptionAreRaised() =
        runTest {
            // 256 MB (songs) and 64 MB (artwork) were options once.
            appPreferences.setCacheMaxMb(256)
            appPreferences.setArtworkCacheMb(64)

            assertEquals(512, appPreferences.cacheMaxMb.first())
            assertEquals(128, appPreferences.artworkCacheMb.first())
        }

    @Test
    fun cacheLimits_noLongerOfferedMoveUpToTheNextSize() =
        runTest {
            // 1.5 GB was briefly a song option.
            appPreferences.setCacheMaxMb(1536)

            assertEquals(2048, appPreferences.cacheMaxMb.first())
            assertEquals(2048, AppPreferences.snapToOption(1536, AppPreferences.SONG_CACHE_OPTIONS_MB))
            assertEquals(3072, AppPreferences.snapToOption(9999, AppPreferences.SONG_CACHE_OPTIONS_MB))
        }
}
