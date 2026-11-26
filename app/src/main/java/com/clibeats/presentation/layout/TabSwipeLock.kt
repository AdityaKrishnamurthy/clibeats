@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.clibeats.presentation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf

/**
 * Lets a screen inside the tab pager stop left/right swipes between tabs while it needs horizontal
 * gestures for itself (for example the playlist selection bar, whose actions scroll sideways).
 */
val LocalTabSwipeLock = compositionLocalOf<(locked: Boolean) -> Unit> { {} }

/** Tabs don't swipe while [locked] is true, nor after this leaves the screen with the lock on. */
@Composable
fun LockTabSwipe(locked: Boolean) {
    val setLocked = LocalTabSwipeLock.current
    DisposableEffect(locked, setLocked) {
        setLocked(locked)
        onDispose { if (locked) setLocked(false) }
    }
}
