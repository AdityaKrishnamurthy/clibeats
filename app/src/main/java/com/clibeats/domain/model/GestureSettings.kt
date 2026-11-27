package com.clibeats.domain.model

/** Which swipe gestures are on (Settings → Gestures). All on by default. */
data class GestureSettings(
    /** Swipe a song right to add it to the queue. */
    val songSwipeRight: Boolean = true,
    /** Swipe a song left to add it to ♥ Favorites. */
    val songSwipeLeft: Boolean = true,
    /** Swipe left / right to move between the bottom tabs. */
    val tabSwipe: Boolean = true,
    /** Swipe down on Now Playing to close it. */
    val nowPlayingSwipeDown: Boolean = true,
    /** Swipe up on Now Playing's cover to switch between cover and lyrics. */
    val nowPlayingSwipeUp: Boolean = true,
)
