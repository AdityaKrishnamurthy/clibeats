package com.clibeats.domain.model

/** What swiping a song does (Settings → Gestures), with the hint shown behind the row while swiping. */
enum class SwipeAction(
    val label: String,
    val hint: String,
) {
    QUEUE("Add to queue", "+ QUEUE"),
    PLAY_NEXT("Play next", "▶ PLAY NEXT"),
    FAVORITE("Add to ♥ Favorites", "♥ FAVORITE"),
    ADD_TO_PLAYLIST("Add to playlist", "+ PLAYLIST"),
    REMOVE("Remove from queue", "✕ REMOVE"),
    OFF("Nothing", ""),
    ;

    companion object {
        /** Choices for songs in lists. */
        val SONG_CHOICES = listOf(QUEUE, PLAY_NEXT, FAVORITE, ADD_TO_PLAYLIST, OFF)

        /** Choices for songs in the Queue. */
        val QUEUE_CHOICES = listOf(REMOVE, PLAY_NEXT, FAVORITE, ADD_TO_PLAYLIST, OFF)
    }
}

/** The app's swipe gestures (Settings → Gestures). The defaults are how CLIBeats has always behaved. */
data class GestureSettings(
    /** Swiping a song to the right. */
    val songSwipeRight: SwipeAction = SwipeAction.QUEUE,
    /** Swiping a song to the left. */
    val songSwipeLeft: SwipeAction = SwipeAction.FAVORITE,
    /** Swiping a song in the Queue to the right. */
    val queueSwipeRight: SwipeAction = SwipeAction.REMOVE,
    /** Swipe left / right to move between the bottom tabs. */
    val tabSwipe: Boolean = true,
    /** Swipe down on Now Playing to close it. */
    val nowPlayingSwipeDown: Boolean = true,
    /** Swipe up on Now Playing's cover to switch between cover and lyrics. */
    val nowPlayingSwipeUp: Boolean = true,
)
