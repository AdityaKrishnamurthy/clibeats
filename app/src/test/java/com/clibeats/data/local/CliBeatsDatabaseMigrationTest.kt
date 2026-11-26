package com.clibeats.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.File

class CliBeatsDatabaseMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `migration 1 to 2 creates liked_songs table`() {
        val db: SupportSQLiteDatabase = mock()
        val sqlCaptor = argumentCaptor<String>()

        CliBeatsDatabase.MIGRATION_1_2.migrate(db)

        verify(db).execSQL(sqlCaptor.capture())
        assertThat(sqlCaptor.firstValue).contains("CREATE TABLE IF NOT EXISTS `liked_songs`")
        assertThat(sqlCaptor.firstValue).contains("FOREIGN KEY(`song_id`) REFERENCES `songs`(`id`)")
    }

    @Test
    fun `migration 2 to 3 creates saved_albums and saved_artists`() {
        val db: SupportSQLiteDatabase = mock()
        val sqlCaptor = argumentCaptor<String>()

        CliBeatsDatabase.MIGRATION_2_3.migrate(db)

        verify(db, times(2)).execSQL(sqlCaptor.capture())
        assertThat(sqlCaptor.allValues[0]).contains("CREATE TABLE IF NOT EXISTS `saved_albums`")
        assertThat(sqlCaptor.allValues[1]).contains("CREATE TABLE IF NOT EXISTS `saved_artists`")
    }

    @Test
    fun `migration 3 to 4 creates downloaded_tracks and lyrics_cache and history index`() {
        val db: SupportSQLiteDatabase = mock()
        val sqlCaptor = argumentCaptor<String>()

        CliBeatsDatabase.MIGRATION_3_4.migrate(db)

        verify(db, times(3)).execSQL(sqlCaptor.capture())
        assertThat(sqlCaptor.allValues[0]).contains("CREATE TABLE IF NOT EXISTS `downloaded_tracks`")
        assertThat(sqlCaptor.allValues[1]).contains("CREATE TABLE IF NOT EXISTS `lyrics_cache`")
        assertThat(sqlCaptor.allValues[2]).contains("CREATE INDEX IF NOT EXISTS `index_history_song_id`")
    }

    @Test
    fun `migration 4 to 5 drops cache_index and adds is_radio column to queue_items`() {
        val db: SupportSQLiteDatabase = mock()
        val sqlCaptor = argumentCaptor<String>()

        CliBeatsDatabase.MIGRATION_4_5.migrate(db)

        verify(db, times(2)).execSQL(sqlCaptor.capture())
        assertThat(sqlCaptor.allValues[0]).isEqualTo("DROP TABLE IF EXISTS `cache_index`")
        assertThat(sqlCaptor.allValues[1])
            .isEqualTo("ALTER TABLE `queue_items` ADD COLUMN `is_radio` INTEGER NOT NULL DEFAULT 0")
    }

    @Test
    fun `exported v5 schema matches migration 4 to 5 contract`() {
        val v4Text = schemaFile(4).readText()
        val v5Text = schemaFile(5).readText()
        val v4Entities =
            json.parseToJsonElement(v4Text).jsonObject["database"]!!
                .jsonObject["entities"]!!.jsonArray
        val v5Entities =
            json.parseToJsonElement(v5Text).jsonObject["database"]!!
                .jsonObject["entities"]!!.jsonArray

        val v4Tables = v4Entities.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }
        val v5Tables = v5Entities.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }
        assertThat(v4Tables).contains("cache_index")
        assertThat(v5Tables).doesNotContain("cache_index")
        assertThat(v5Tables).containsAtLeast("downloaded_tracks", "lyrics_cache", "queue_items")

        val queueEntity = v5Entities.first { it.jsonObject["tableName"]!!.jsonPrimitive.content == "queue_items" }
        val fields = queueEntity.jsonObject["fields"]!!.jsonArray.map { it.jsonObject }
        val isRadioField = fields.first { it["columnName"]!!.jsonPrimitive.content == "is_radio" }
        assertThat(isRadioField["affinity"]!!.jsonPrimitive.content).isEqualTo("INTEGER")
        assertThat(isRadioField["notNull"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(isRadioField["defaultValue"]!!.jsonPrimitive.content).isEqualTo("0")
    }

    private fun schemaFile(version: Int): File {
        val relative = "schemas/com.clibeats.data.local.CliBeatsDatabase/$version.json"
        val direct = File(relative)
        return if (direct.isFile) direct else File("app/$relative")
    }
}
