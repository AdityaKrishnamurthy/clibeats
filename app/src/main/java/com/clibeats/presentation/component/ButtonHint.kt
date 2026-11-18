@file:Suppress("ktlint:standard:function-naming")

package com.clibeats.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsSurface

/**
 * Wraps a control and briefly shows a tiny label ([hint], e.g. "ONE" / "ALL" / "OFF") just above it.
 * The label floats outside the button's bounds, so it doesn't shift the layout.
 */
@Suppress("FunctionNaming")
@Composable
fun HintedControl(
    hint: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Keep the last text while fading out so the label doesn't empty mid-animation.
    val last = remember { arrayOf("") }
    hint?.let { last[0] = it }
    Box(modifier = modifier.zIndex(1f), contentAlignment = Alignment.Center) {
        content()
        AnimatedVisibility(
            visible = hint != null,
            enter = fadeIn(tween(HINT_FADE_MS)),
            exit = fadeOut(tween(HINT_FADE_MS)),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-HINT_LIFT_DP).dp)
                    .wrapContentSize(unbounded = true),
        ) {
            Text(
                text = last[0],
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsAccent,
                maxLines = 1,
                modifier =
                    Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .background(CliBeatsSurface)
                        .border(1.dp, CliBeatsAccent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private const val HINT_FADE_MS = 120
private const val HINT_LIFT_DP = 18
