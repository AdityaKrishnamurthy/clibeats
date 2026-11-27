@file:Suppress("MagicNumber")

package com.clibeats.domain.model

/** The ten bands CLIBeats exposes, in Hz. The same centers are used by DynamicsProcessing and Equalizer. */
val EQUALIZER_BANDS_HZ: List<Int> = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

const val EQUALIZER_MIN_GAIN_DB = -12f
const val EQUALIZER_MAX_GAIN_DB = 12f
const val EQUALIZER_MIN_PREAMP_DB = -12f
const val EQUALIZER_MAX_PREAMP_DB = 12f

enum class EqualizerPreset(
    val storedName: String,
    val label: String,
    val gainsDb: List<Float>,
) {
    FLAT("FLAT", "FLAT", List(EQUALIZER_BANDS_HZ.size) { 0f }),
    BASS_BOOST("BASS_BOOST", "BASS BOOST", listOf(7f, 6f, 5f, 3f, 0f, 0f, 0f, 0f, 0f, 1f)),
    VOCAL("VOCAL", "VOCAL", listOf(-3f, -2f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, 0f)),
    ROCK("ROCK", "ROCK", listOf(4f, 3f, 2f, 0f, -1f, 0f, 2f, 3f, 3f, 2f)),
    ELECTRONIC("ELECTRONIC", "ELECTRONIC", listOf(5f, 4f, 2f, 0f, -2f, 1f, 2f, 4f, 4f, 3f)),
    LATE_NIGHT("LATE_NIGHT", "LATE NIGHT", listOf(2f, 1f, 0f, -1f, -2f, -2f, -1f, 0f, 1f, 1f)),
    CUSTOM("CUSTOM", "CUSTOM", List(EQUALIZER_BANDS_HZ.size) { 0f }),
    ;

    fun gains(): List<Float> = gainsDb.map(::clampGainDb)

    companion object {
        val selectable: List<EqualizerPreset> = entries.filterNot { it == CUSTOM }

        fun fromStoredName(name: String?): EqualizerPreset = entries.firstOrNull { it.storedName == name } ?: FLAT
    }
}

data class EqualizerState(
    val enabled: Boolean = false,
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val gainsDb: List<Float> = EqualizerPreset.FLAT.gains(),
    val preampDb: Float = 0f,
) {
    /** A drag/increment never leaves the bands or presets out of range. */
    fun withBandGain(
        index: Int,
        gainDb: Float,
    ): EqualizerState {
        if (index !in gainsDb.indices) return this
        val gains = gainsDb.toMutableList().apply { this[index] = clampGainDb(gainDb) }
        return copy(preset = EqualizerPreset.CUSTOM, gainsDb = gains)
    }

    fun withPreamp(gainDb: Float): EqualizerState =
        copy(
            preampDb = gainDb.coerceIn(EQUALIZER_MIN_PREAMP_DB, EQUALIZER_MAX_PREAMP_DB),
        )

    fun withPreset(preset: EqualizerPreset): EqualizerState =
        if (preset == EqualizerPreset.CUSTOM) this else copy(preset = preset, gainsDb = preset.gains())
}

fun clampGainDb(value: Float): Float = value.coerceIn(EQUALIZER_MIN_GAIN_DB, EQUALIZER_MAX_GAIN_DB)

/** The saved [gains] if they match the band count, else the preset's curve. */
fun decodeEqualizerGains(
    stored: String?,
    preset: EqualizerPreset,
): List<Float> {
    val parsed =
        stored?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf {
            it.size == EQUALIZER_BANDS_HZ.size
        }
    return (parsed ?: preset.gains()).map(::clampGainDb)
}

fun encodeEqualizerGains(gains: List<Float>): String = gains.joinToString(",") { it.toString() }
