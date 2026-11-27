package com.clibeats.domain.model

/** What swiping a song does (Settings → Gestures). */
enum class SwipeAction(
    val label: String,
) {
    QUEUE("Add to queue"),
    PLAY_NEXT("Play next"),
    FAVORITE("Add to ♥ Favorites"),
    OFF("Nothing"),
}

/** The app's swipe gestures (Settings → Gestures). The defaults are how CLIBeats has always behaved. */
data class GestureSettings(
    /** Swiping a song to the right. */
    val songSwipeRight: SwipeAction = SwipeAction.QUEUE,
    /** Swiping a song to the left. */
    val songSwipeLeft: SwipeAction = SwipeAction.FAVORITE,
    /** Swipe left / right to move between the bottom tabs. */
    val tabSwipe: Boolean = true,
    /** Swipe down on Now Playing to close it. */
    val nowPlayingSwipeDown: Boolean = true,
    /** Swipe up on Now Playing's cover to switch between cover and lyrics. */
    val nowPlayingSwipeUp: Boolean = true,
)
