package com.clibeats.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EqualizerStateTest {
    @Test
    fun presets_coverEveryBandWithinRange() {
        EqualizerPreset.entries.forEach { preset ->
            assertThat(preset.gains()).hasSize(EQUALIZER_BANDS_HZ.size)
            preset.gains().forEach { gain ->
                assertThat(gain).isAtLeast(EQUALIZER_MIN_GAIN_DB)
                assertThat(gain).isAtMost(EQUALIZER_MAX_GAIN_DB)
            }
        }
    }

    @Test
    fun changingABand_clampsAndMarksTheCurveCustom() {
        val state = EqualizerState(preset = EqualizerPreset.ROCK, gainsDb = EqualizerPreset.ROCK.gains())
        val changed = state.withBandGain(0, 99f)

        assertThat(changed.preset).isEqualTo(EqualizerPreset.CUSTOM)
        assertThat(changed.gainsDb[0]).isEqualTo(EQUALIZER_MAX_GAIN_DB)
        assertThat(changed.gainsDb[1]).isEqualTo(EqualizerPreset.ROCK.gains()[1])
    }

    @Test
    fun invalidBandIndex_isIgnored() {
        val state = EqualizerState()
        assertThat(state.withBandGain(-1, 6f)).isEqualTo(state)
        assertThat(state.withBandGain(EQUALIZER_BANDS_HZ.size, 6f)).isEqualTo(state)
    }

    @Test
    fun choosingAPreset_replacesTheWholeCurve() {
        val state = EqualizerState(preset = EqualizerPreset.CUSTOM, gainsDb = List(10) { 5f })
        val bass = state.withPreset(EqualizerPreset.BASS_BOOST)

        assertThat(bass.preset).isEqualTo(EqualizerPreset.BASS_BOOST)
        assertThat(bass.gainsDb).isEqualTo(EqualizerPreset.BASS_BOOST.gains())
    }

    @Test
    fun preamp_isClamped() {
        assertThat(EqualizerState().withPreamp(50f).preampDb).isEqualTo(EQUALIZER_MAX_PREAMP_DB)
        assertThat(EqualizerState().withPreamp(-50f).preampDb).isEqualTo(EQUALIZER_MIN_PREAMP_DB)
    }

    @Test
    fun gains_roundTripThroughStorage() {
        val gains = listOf(1f, -2f, 3.5f, 0f, 0f, 0f, 0f, 0f, 0f, -6f)
        assertThat(decodeEqualizerGains(encodeEqualizerGains(gains), EqualizerPreset.FLAT)).isEqualTo(gains)
    }

    @Test
    fun corruptOrShortGains_fallBackToThePreset() {
        assertThat(decodeEqualizerGains("1,2,3", EqualizerPreset.VOCAL))
            .isEqualTo(EqualizerPreset.VOCAL.gains())
        assertThat(decodeEqualizerGains("nope", EqualizerPreset.FLAT))
            .isEqualTo(EqualizerPreset.FLAT.gains())
        assertThat(decodeEqualizerGains(null, EqualizerPreset.ROCK))
            .isEqualTo(EqualizerPreset.ROCK.gains())
    }
}
