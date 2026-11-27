package com.clibeats.playback

import com.clibeats.domain.model.EQUALIZER_BANDS_HZ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each slider's band covers the range around its own frequency. */
class EqualizerBandsTest {
    @Test
    fun bandEdgesSitBetweenNeighbouringSliders() {
        EQUALIZER_BANDS_HZ.indices.forEach { i ->
            val edge = bandUpperEdgeHz(i)
            assertTrue("band $i edge above its centre", edge > EQUALIZER_BANDS_HZ[i])
            EQUALIZER_BANDS_HZ.getOrNull(i + 1)?.let { next -> assertTrue("band $i edge below the next", edge < next) }
        }
        assertEquals(44.0f, bandUpperEdgeHz(0), 0.5f)
        assertEquals(20_000f, bandUpperEdgeHz(EQUALIZER_BANDS_HZ.lastIndex), 0.01f)
    }
}
