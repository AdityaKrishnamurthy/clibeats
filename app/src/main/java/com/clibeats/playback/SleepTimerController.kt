package com.clibeats.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates both "stop after current track" and countdown minute sleep timers for [PlayerAdapter].
 */
class SleepTimerController(
    private val scope: CoroutineScope,
    private val setPauseAtEndOfMediaItems: (Boolean) -> Unit,
    private val onCountdownExpired: () -> Unit,
) {
    private val _pauseAtEndOfTrack = MutableStateFlow(false)
    val pauseAtEndOfTrack: StateFlow<Boolean> = _pauseAtEndOfTrack.asStateFlow()

    private val _sleepTimerCountdown = MutableStateFlow<Int?>(null)
    val sleepTimerCountdown: StateFlow<Int?> = _sleepTimerCountdown.asStateFlow()

    private var sleepCountdownJob: Job? = null

    fun setPauseAtEndOfTrack(enabled: Boolean) {
        setPauseAtEndOfMediaItems(enabled)
        _pauseAtEndOfTrack.value = enabled
    }

    fun startSleepTimer(minutes: Int) {
        sleepCountdownJob?.cancel()
        setPauseAtEndOfTrack(false)
        if (minutes <= 0) {
            _sleepTimerCountdown.value = null
            return
        }
        sleepCountdownJob =
            scope.launch {
                for (left in minutes downTo 1) {
                    _sleepTimerCountdown.value = left
                    delay(MINUTE_MS)
                }
                onCountdownExpired()
                _sleepTimerCountdown.value = null
            }
    }

    fun cancelSleepTimer() {
        sleepCountdownJob?.cancel()
        sleepCountdownJob = null
        _sleepTimerCountdown.value = null
        setPauseAtEndOfTrack(false)
    }

    companion object {
        private const val MINUTE_MS = 60_000L
    }
}
