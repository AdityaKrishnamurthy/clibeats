@file:Suppress("ktlint:standard:function-naming", "MagicNumber")

package com.clibeats.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clibeats.domain.model.LyricLine
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.JetBrainsMonoFamily
import java.util.Locale

private val LYRIC_FONT_SIZE = 15.sp
private val TIME_FONT_SIZE = 12.sp
private val ROW_VERTICAL_PADDING = 8.dp
private const val INACTIVE_TIME_ALPHA = 0.6f
private const val ACTIVE_HIGHLIGHT_ALPHA = 0.14f

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun LyricsPanel(
    lyrics: List<LyricLine>,
    positionMsProvider: () -> Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "[ NO SYNCED LYRICS AVAILABLE ]",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = CliBeatsTextSecondary,
            )
        }
        return
    }

    val activeIndex by remember(lyrics) {
        derivedStateOf {
            val pos = positionMsProvider()
            val idx = lyrics.indexOfLast { it.timeMs <= pos }
            if (idx >= 0) idx else 0
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty() && activeIndex in lyrics.indices) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 32.dp, horizontal = 8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isActive = index == activeIndex
            val rowModifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (isActive) {
                            Modifier.background(CliBeatsAccent.copy(alpha = ACTIVE_HIGHLIGHT_ALPHA))
                        } else {
                            Modifier
                        },
                    ).clickable { onSeekTo(line.timeMs) }
                    .padding(horizontal = 8.dp, vertical = ROW_VERTICAL_PADDING)

            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val timeStr = formatTimestamp(line.timeMs)
                Text(
                    text = if (isActive) "> [$timeStr]" else "  [$timeStr]",
                    fontSize = TIME_FONT_SIZE,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) CliBeatsAccent else CliBeatsTextSecondary.copy(alpha = INACTIVE_TIME_ALPHA),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = line.text.ifBlank { "..." },
                    fontSize = LYRIC_FONT_SIZE,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) CliBeatsTextPrimary else CliBeatsTextSecondary,
                )
            }
        }
    }
}

private fun formatTimestamp(timeMs: Long): String {
    val totalSec = timeMs.coerceAtLeast(0L) / 1000L
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
