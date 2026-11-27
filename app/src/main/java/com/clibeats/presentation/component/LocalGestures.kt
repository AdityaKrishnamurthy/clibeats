package com.clibeats.presentation.component

import androidx.compose.runtime.staticCompositionLocalOf
import com.clibeats.domain.model.GestureSettings

/** The gesture switches from Settings → Gestures, read by song rows, the tab pager and Now Playing. */
val LocalGestureSettings = staticCompositionLocalOf { GestureSettings() }
