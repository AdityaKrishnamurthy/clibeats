package com.clibeats.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CliBeatsDatabaseMigrationAndroidTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            CliBeatsDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate3To4To5_preservesDataAndAppliesSchemaChanges() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO songs (id, title, artist, album, duration_ms, provider_id) " +
                    "VALUES ('s1', 'Track', 'Artist', 'Album', 120000, 'youtube_music')",
            )
            execSQL(
                "INSERT INTO queue_items (position, songId, addedAt) VALUES (0, 's1', 1000)",
            )
            close()
        }

        val migratedDb =
            helper.runMigrationsAndValidate(
                TEST_DB,
                5,
                true,
                CliBeatsDatabase.MIGRATION_3_4,
                CliBeatsDatabase.MIGRATION_4_5,
            )

        migratedDb.query("SELECT songId, is_radio FROM queue_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        migratedDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='cache_index'").use { cursor ->
            assertFalse(cursor.moveToFirst())
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
