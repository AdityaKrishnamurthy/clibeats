package com.clibeats.playback.waveform

/**
 * Files what [LoudnessTap] heard between two position ticks under the bar that was playing. A tick after a
 * seek, a pause or a gap is skipped: that audio can't be placed reliably, and the next ticks fill it in.
 */
class LiveLoudnessTracker {
    private var lastPositionMs = -1L
    val bars = WaveformAccumulator()

    /** Returns true when a bar got new loudness. */
    fun onTick(
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        sumSquares: Double,
        count: Long,
    ): Boolean {
        val previous = lastPositionMs
        lastPositionMs = if (isPlaying) positionMs else -1L
        val continuous = previous >= 0 && positionMs - previous in 0..MAX_TICK_GAP_MS
        val measurable = count > 0 && durationMs > 0
        if (!isPlaying || !continuous || !measurable) return false
        val middleMs = (previous + positionMs) / 2
        bars.add(Loudness.barFor(middleMs * US_PER_MS, durationMs * US_PER_MS), sumSquares, count)
        return true
    }

    private companion object {
        /** Ticks come every 500 ms while playing; a bigger jump is a seek or a stall. */
        const val MAX_TICK_GAP_MS = 2_000L
        const val US_PER_MS = 1000L
    }
}
