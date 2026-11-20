@file:Suppress("ForbiddenImport")

package com.clibeats.data.local.dao

import com.clibeats.data.local.entity.SongEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LikedSongReorderTest {
    /** Just enough of the DAO to check the order logic of [LikedSongDao.reorder]. */
    private class FakeLikedSongDao : LikedSongDao {
        val likedAt = linkedMapOf<String, Long>()

        /** Song ids as Favorites lists them: newest liked first. */
        fun listed(): List<String> = likedAt.entries.sortedByDescending { it.value }.map { it.key }

        override suspend fun likeSong(
            songId: String,
            likedAt: Long,
        ) {
            this.likedAt[songId] = likedAt
        }

        override suspend fun latestLikedAt(): Long? = likedAt.values.maxOrNull()

        override suspend fun setLikedAt(
            songId: String,
            likedAt: Long,
        ) {
            if (songId in this.likedAt) this.likedAt[songId] = likedAt
        }

        override suspend fun deleteAll() = likedAt.clear()

        override suspend fun unlikeSong(songId: String) {
            likedAt.remove(songId)
        }

        override fun isLikedFlow(songId: String): Flow<Boolean> = throw UnsupportedOperationException()

        override suspend fun isLiked(songId: String): Boolean = songId in likedAt

        override fun getLikedSongsAsFlow(): Flow<List<SongEntity>> = throw UnsupportedOperationException()

        override fun getLikedSongIdsAsFlow(): Flow<List<String>> = throw UnsupportedOperationException()
    }

    @Test
    fun `reorder stores the dragged order`() =
        runTest {
            val dao = FakeLikedSongDao()
            dao.likeSong("a", 100)
            dao.likeSong("b", 200)
            dao.likeSong("c", 300)
            assertThat(dao.listed()).containsExactly("c", "b", "a").inOrder()

            dao.reorder(listOf("a", "c", "b"))

            assertThat(dao.listed()).containsExactly("a", "c", "b").inOrder()
        }

    @Test
    fun `a song liked after reordering still goes on top`() =
        runTest {
            val dao = FakeLikedSongDao()
            dao.likeSong("a", 100)
            dao.likeSong("b", 200)
            dao.reorder(listOf("a", "b"))

            dao.likeSong("new", 201)

            assertThat(dao.listed()).containsExactly("new", "a", "b").inOrder()
        }

    @Test
    fun `reorder on empty favorites does nothing`() =
        runTest {
            val dao = FakeLikedSongDao()
            dao.reorder(listOf("x"))
            assertThat(dao.listed()).isEmpty()
        }
}
