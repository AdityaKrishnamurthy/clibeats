package com.clibeats.playback

import com.clibeats.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe queue ordering and radio-boundary controller extracted from [PlayerAdapter].
 *
 * Pure JVM (zero Android or Media3 dependencies) so queue semantics — including inserting
 * user-queued tracks strictly ahead of upcoming radio suggestions, shuffle round-tripping,
 * and restoring radio boundaries after process restart — can be tested deterministically.
 */
@Suppress("TooManyFunctions")
class QueueController {
    private val lock = Any()
    private val trackList = mutableListOf<Track>()
    private val autoplayIds = mutableSetOf<String>()

    private val _queueFlow = MutableStateFlow<List<Track>>(emptyList())
    val queueFlow: StateFlow<List<Track>> = _queueFlow.asStateFlow()

    private val _radioTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val radioTrackIds: StateFlow<Set<String>> = _radioTrackIds.asStateFlow()

    @Volatile var queueGeneration: Int = 0
        private set

    @Volatile var shuffleEnabled: Boolean = false
        private set

    private var unshuffledOrder: List<Track>? = null

    private val trackSources =
        object : LinkedHashMap<String, String?>(SOURCES_KEPT, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean {
                return size > SOURCES_KEPT
            }
        }

    fun snapshot(): List<Track> = synchronized(lock) { trackList.toList() }

    fun size(): Int = synchronized(lock) { trackList.size }

    fun isEmpty(): Boolean = synchronized(lock) { trackList.isEmpty() }

    fun getOrNull(index: Int): Track? = synchronized(lock) { trackList.getOrNull(index) }

    fun sourceOf(trackId: String): String? = synchronized(lock) { trackSources[trackId] }

    fun isRadio(trackId: String?): Boolean = trackId != null && synchronized(lock) { trackId in autoplayIds }

    fun setSource(
        track: Track,
        source: String?,
    ) {
        synchronized(lock) { trackSources[track.id] = source }
    }

    /** Plays [track] immediately after [currentIndex] (or starts a fresh queue when empty). */
    fun planPlayTrack(
        currentIndex: Int,
        track: Track,
    ): Pair<Int, Boolean> =
        synchronized(lock) {
            trackSources[track.id] = null
            if (trackList.isNotEmpty()) {
                val index = (currentIndex + 1).coerceIn(0, trackList.size)
                if (autoplayIds.remove(track.id)) publishRadioLocked()
                trackList.add(index, track)
                publishQueueLocked()
                index to false
            } else {
                unshuffledOrder = null
                trackList.clear()
                resetRadioLocked()
                trackList.add(track)
                publishQueueLocked()
                0 to true
            }
        }

    /** Replaces the active queue with [tracks], applying shuffle if currently enabled. */
    fun replaceQueue(
        tracks: List<Track>,
        startIndex: Int,
        source: String?,
    ): Pair<List<Track>, Int> =
        synchronized(lock) {
            tracks.forEach { trackSources[it.id] = source }
            val ordered: List<Track>
            val start: Int
            if (shuffleEnabled && tracks.size > 1) {
                unshuffledOrder = tracks.toList()
                ordered = shuffledAround(tracks, startIndex.coerceIn(tracks.indices))
                start = 0
            } else {
                unshuffledOrder = null
                ordered = tracks.toList()
                start = startIndex
            }
            trackList.clear()
            resetRadioLocked()
            trackList.addAll(ordered)
            publishQueueLocked()
            ordered to start
        }

    /**
     * Inserts [track] after any already-queued user tracks and strictly before remaining
     * autoplay suggestions (or mixes into that user-queued range when shuffle is active).
     */
    fun addToQueue(
        currentIndex: Int,
        track: Track,
    ): Int =
        synchronized(lock) {
            trackSources[track.id] = QUEUED_SOURCE
            if (autoplayIds.remove(track.id)) publishRadioLocked()
            val first = if (trackList.isEmpty()) 0 else (currentIndex + 1).coerceIn(0, trackList.size)
            val radioStart =
                (first until trackList.size).firstOrNull { trackList[it].id in autoplayIds }
                    ?: trackList.size
            val shuffleIn = shuffleEnabled && first in 1..radioStart
            val index = if (shuffleIn) (first..radioStart).random() else radioStart
            trackList.add(index, track)
            publishQueueLocked()
            index
        }

    fun addAutoplayTracks(tracks: List<Track>): Boolean =
        synchronized(lock) {
            if (tracks.isEmpty()) return@synchronized false
            autoplayIds += tracks.map { it.id }
            publishRadioLocked()
            trackList.addAll(tracks)
            publishQueueLocked()
            true
        }

    /** Removes upcoming radio items strictly after [currentIndex]; returns removed indices high-to-low. */
    fun removeUpcomingRadio(currentIndex: Int): List<Int> =
        synchronized(lock) {
            val removed = mutableListOf<Int>()
            for (i in trackList.indices.reversed()) {
                if (i > currentIndex && trackList[i].id in autoplayIds) {
                    trackList.removeAt(i)
                    removed += i
                }
            }
            resetRadioLocked()
            publishQueueLocked()
            removed
        }

    fun addAll(tracks: List<Track>): Boolean =
        synchronized(lock) {
            if (tracks.isEmpty()) return@synchronized false
            trackList.addAll(tracks)
            publishQueueLocked()
            true
        }

    fun playNext(
        currentIndex: Int,
        track: Track,
    ): Int =
        synchronized(lock) {
            trackSources[track.id] = QUEUED_SOURCE
            if (autoplayIds.remove(track.id)) publishRadioLocked()
            val targetIndex =
                if (trackList.isEmpty()) {
                    0
                } else {
                    (currentIndex + 1).coerceIn(0, trackList.size)
                }
            trackList.add(targetIndex, track)
            publishQueueLocked()
            targetIndex
        }

    fun restoreQueue(
        tracks: List<Track>,
        radioIds: Set<String> = emptySet(),
        shuffleEnabled: Boolean = false,
    ) {
        synchronized(lock) {
            trackList.clear()
            autoplayIds.clear()
            val queueIds = tracks.map { it.id }.toSet()
            autoplayIds.addAll(radioIds.filter { it in queueIds })
            publishRadioLocked()
            queueGeneration++
            trackList.addAll(tracks)
            this.shuffleEnabled = shuffleEnabled
            unshuffledOrder = null
            publishQueueLocked()
        }
    }

    fun toggleShuffle(currentIndex: Int): ShufflePlan? =
        synchronized(lock) {
            if (currentIndex !in trackList.indices || trackList.size < 2) {
                shuffleEnabled = !shuffleEnabled
                unshuffledOrder = null
                return@synchronized null
            }
            val playing = trackList[currentIndex]
            val order =
                if (!shuffleEnabled) {
                    unshuffledOrder = trackList.toList()
                    shuffleEnabled = true
                    shuffledAround(trackList, currentIndex)
                } else {
                    val original = unshuffledOrder
                    unshuffledOrder = null
                    shuffleEnabled = false
                    if (original == null) return@synchronized null
                    val present = trackList.toMutableList()
                    val restored =
                        original.filter { o ->
                            val idx = present.indexOfFirst { it === o }
                            if (idx >= 0) present.removeAt(idx)
                            idx >= 0
                        }
                    restored + present
                }
            val targetIndex = order.indexOfFirst { it === playing }.takeIf { it >= 0 } ?: return@synchronized null
            trackList.clear()
            trackList.addAll(order)
            publishQueueLocked()
            ShufflePlan(
                previousCurrentIndex = currentIndex,
                before = order.subList(0, targetIndex).toList(),
                after = order.subList(targetIndex + 1, order.size).toList(),
            )
        }

    fun moveTrack(
        fromIndex: Int,
        toIndex: Int,
    ): Boolean =
        synchronized(lock) {
            if (fromIndex in trackList.indices && toIndex in trackList.indices && fromIndex != toIndex) {
                val track = trackList.removeAt(fromIndex)
                trackList.add(toIndex, track)
                publishQueueLocked()
                true
            } else {
                false
            }
        }

    fun removeAt(index: Int): Track? =
        synchronized(lock) {
            if (index !in trackList.indices) return@synchronized null
            val removed = trackList.removeAt(index)
            if (trackList.none { it.id == removed.id } && autoplayIds.remove(removed.id)) {
                publishRadioLocked()
            }
            publishQueueLocked()
            removed
        }

    fun insertAt(
        index: Int,
        track: Track,
    ): Int =
        synchronized(lock) {
            val at = index.coerceIn(0, trackList.size)
            if (autoplayIds.remove(track.id)) publishRadioLocked()
            trackList.add(at, track)
            publishQueueLocked()
            at
        }

    fun clearExcept(currentIndex: Int): Boolean =
        synchronized(lock) {
            if (currentIndex !in trackList.indices) {
                trackList.clear()
                resetRadioLocked()
                publishQueueLocked()
                false
            } else {
                val keep = trackList[currentIndex]
                trackList.clear()
                resetRadioLocked()
                trackList.add(keep)
                publishQueueLocked()
                true
            }
        }

    fun refreshTracks(fresh: Map<String, Track>): Boolean =
        synchronized(lock) {
            var changed = false
            trackList.indices.forEach { i ->
                val old = trackList[i]
                val new = fresh[old.id]?.copy(streamUrl = old.streamUrl) ?: return@forEach
                if (new != old) {
                    trackList[i] = new
                    unshuffledOrder = unshuffledOrder?.map { if (it === old) new else it }
                    changed = true
                }
            }
            if (changed) publishQueueLocked()
            changed
        }

    fun replaceTrackAt(
        index: Int,
        track: Track,
    ): Boolean =
        synchronized(lock) {
            if (index !in trackList.indices) return@synchronized false
            trackList[index] = track
            publishQueueLocked()
            true
        }

    fun findNextIndexOf(
        trackId: String,
        excludingIndex: Int,
    ): Int? =
        synchronized(lock) {
            trackList.indices
                .filter { trackList[it].id == trackId && it != excludingIndex }
                .minByOrNull { if (it > excludingIndex) it - excludingIndex else it + trackList.size }
        }

    private fun shuffledAround(
        tracks: List<Track>,
        keep: Int,
    ): List<Track> = listOf(tracks[keep]) + tracks.filterIndexed { i, _ -> i != keep }.shuffled()

    private fun publishQueueLocked() {
        _queueFlow.value = trackList.toList()
    }

    private fun publishRadioLocked() {
        _radioTrackIds.value = autoplayIds.toSet()
    }

    private fun resetRadioLocked() {
        autoplayIds.clear()
        publishRadioLocked()
        queueGeneration++
    }

    data class ShufflePlan(
        val previousCurrentIndex: Int,
        val before: List<Track>,
        val after: List<Track>,
    )

    companion object {
        private const val SOURCES_KEPT = 2_000
        private const val LOAD_FACTOR = 0.75f
    }
}
