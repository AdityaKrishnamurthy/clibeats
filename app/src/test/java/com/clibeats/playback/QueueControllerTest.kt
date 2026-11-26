package com.clibeats.playback

import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class QueueControllerTest {
    private fun track(id: String) =
        Track(
            id = id,
            title = "Track $id",
            artist = "Artist",
            album = "Album",
            durationMs = 120_000L,
            artworkUrl = null,
            streamUrl = null,
            providerId = "youtube_music",
        )

    @Test
    fun `addToQueue inserts after user tracks and strictly before remaining radio tracks`() {
        val controller = QueueController()
        controller.replaceQueue(listOf(track("user1")), startIndex = 0, source = "Home")
        controller.addAutoplayTracks(listOf(track("r1"), track("r2"), track("r3")))

        // User skips into radio track r1 (index 1) and manually queues u2 then u3.
        val insert1 = controller.addToQueue(currentIndex = 1, track("u2"))
        val insert2 = controller.addToQueue(currentIndex = 1, track("u3"))

        assertThat(insert1).isEqualTo(2)
        assertThat(insert2).isEqualTo(3)
        assertThat(controller.snapshot().map { it.id })
            .containsExactly("user1", "r1", "u2", "u3", "r2", "r3")
            .inOrder()
    }

    @Test
    fun `restoreQueue with persisted radioIds preserves autoplay barrier`() {
        val controller = QueueController()
        controller.restoreQueue(
            tracks = listOf(track("u1"), track("r1"), track("r2")),
            radioIds = setOf("r1", "r2"),
            shuffleEnabled = false,
        )

        val insertIndex = controller.addToQueue(currentIndex = 1, track("u2"))
        assertThat(insertIndex).isEqualTo(2)
        assertThat(controller.snapshot().map { it.id }).containsExactly("u1", "r1", "u2", "r2").inOrder()
    }

    @Test
    fun `concurrent queue mutations preserve consistent state without exceptions`() {
        val controller = QueueController()
        controller.replaceQueue((1..20).map { track("init_$it") }, startIndex = 0, source = "Test")

        val pool = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(4)
        repeat(4) { worker ->
            pool.submit {
                try {
                    repeat(50) { step ->
                        when (step % 4) {
                            0 -> controller.addToQueue(0, track("w${worker}_$step"))
                            1 -> controller.playNext(0, track("n${worker}_$step"))
                            2 -> controller.moveTrack(1, 2)
                            else -> controller.snapshot()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
        pool.shutdownNow()
        assertThat(controller.size()).isGreaterThan(20)
        assertThat(controller.queueFlow.value).isEqualTo(controller.snapshot())
    }
}
