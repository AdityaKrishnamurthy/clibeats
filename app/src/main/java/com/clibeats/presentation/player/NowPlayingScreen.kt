@file:Suppress("ktlint:standard:function-naming", "MagicNumber")

package com.clibeats.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

/** Full-screen player opened from the bottom PlayerBar. Collects state itself (ticks stay local). */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val state by viewModel.playbackState.collectAsState()
    val sleepTimer by viewModel.sleepTimer.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val track = state.currentTrack
    var showSleepDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CliBeatsBackground)
                .clickable(enabled = false) {} // swallow taps so the screen below doesn't receive them
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Header ──
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Close player", tint = CliBeatsTextPrimary)
            }
            Text(
                text = "> now_playing",
                style = MaterialTheme.typography.labelMedium,
                color = CliBeatsAccent,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(12.dp))

        // ── Artwork ──
        val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        AsyncImage(
            model = track?.artworkUrl,
            contentDescription = track?.let { "Artwork for ${it.title}" },
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CliBeatsBorderInactive, RoundedCornerShape(8.dp)),
        )

        Spacer(Modifier.height(20.dp))

        // ── Title / artist / quality ──
        Text(
            text = track?.title ?: "Nothing playing",
            style = MaterialTheme.typography.headlineSmall,
            color = CliBeatsTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (!track?.artist.isNullOrBlank()) {
            Text(
                text = track!!.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = CliBeatsTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = qualityLine(state),
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsAccent,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        // ── Seek bar: drag locally, seek once on release ──
        SeekBar(state = state, onSeek = viewModel::seekTo)

        Spacer(Modifier.height(8.dp))

        // ── Transport controls (no shuffle by design) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::onRepeatClick) {
                Icon(
                    imageVector =
                        if (state.repeatMode == RepeatMode.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                    contentDescription = "Repeat: ${state.repeatMode.name.lowercase()}",
                    tint = if (state.repeatMode == RepeatMode.OFF) CliBeatsTextSecondary else CliBeatsAccent,
                )
            }
            IconButton(onClick = viewModel::onSkipPreviousClick, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Outlined.SkipPrevious,
                    contentDescription = "Previous",
                    tint = CliBeatsTextPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .border(2.dp, CliBeatsAccent, RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::onPlayPauseClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = CliBeatsAccent,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = viewModel::onSkipNextClick, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.SkipNext, "Next", tint = CliBeatsTextPrimary, modifier = Modifier.size(32.dp))
            }
            // Balance the row so play/pause stays centred.
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        // ── Bottom actions: sleep timer · queue · add to playlist ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sleepLabel =
                when (val t = sleepTimer) {
                    SleepTimer.Off -> null
                    is SleepTimer.Countdown -> "${t.remainingMin}m"
                    SleepTimer.EndOfTrack -> "end"
                }
            Row(
                modifier = Modifier.clickable { showSleepDialog = true }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Bedtime,
                    contentDescription = "Sleep timer" + (sleepLabel?.let { ": $it" } ?: ""),
                    tint = if (sleepLabel != null) CliBeatsAccent else CliBeatsTextSecondary,
                )
                if (sleepLabel != null) {
                    Text(" $sleepLabel", style = MaterialTheme.typography.labelSmall, color = CliBeatsAccent)
                }
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic, "Open queue", tint = CliBeatsTextSecondary)
            }
            IconButton(onClick = { showPlaylistDialog = true }, enabled = track != null) {
                Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to playlist", tint = CliBeatsTextSecondary)
            }
        }
    }

    if (showSleepDialog) {
        val options = SLEEP_OPTIONS
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("> sleep --timer") },
            text = {
                Column {
                    options.forEach { (label, minutes) ->
                        Text(
                            text = "[ $label ]",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSleepTimer(minutes)
                                        showSleepDialog = false
                                    }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Cancel") } },
        )
    }

    if (showPlaylistDialog && track != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("> add --to-playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists yet. Create one in the Playlists tab.", color = CliBeatsTextSecondary)
                } else {
                    LazyColumn {
                        items(playlists, key = { it.id }) { playlist ->
                            Text(
                                text = "[ ${playlist.name} ]",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addToPlaylist(track, playlist.id)
                                            showPlaylistDialog = false
                                        }.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlaylistDialog = false }) { Text("Cancel") } },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SeekBar(
    state: PlaybackState,
    onSeek: (Long) -> Unit,
) {
    val total = state.durationMs.coerceAtLeast(0L)
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val liveFraction = if (total > 0) (state.positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
    val shown = if (dragFraction >= 0f) dragFraction else liveFraction
    val accent = CliBeatsAccent
    Slider(
        value = shown,
        onValueChange = { dragFraction = it },
        onValueChangeFinished = {
            if (total > 0 && dragFraction >= 0f) onSeek((dragFraction * total).toLong())
            dragFraction = -1f
        },
        enabled = total > 0,
        colors =
            SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = CliBeatsBorderInactive,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            formatTime((shown * total).toLong()),
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsTextSecondary,
        )
        Text(formatTime(total), style = MaterialTheme.typography.labelSmall, color = CliBeatsTextSecondary)
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/** e.g. `[LOCAL] FLAC · 1076 kbps · 44.1 kHz · 24-bit` or `[ONLINE · YouTube Music] OPUS · 160 kbps · 48 kHz`. */
internal fun qualityLine(state: PlaybackState): String {
    val providerId = state.currentTrack?.providerId ?: return ""
    val source = if (providerId == "local") "[LOCAL]" else "[ONLINE · ${providerLabel(providerId)}]"
    val parts =
        listOfNotNull(
            state.codec,
            state.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" },
            state.sampleRateHz?.let {
                    hz ->
                if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(java.util.Locale.US, "%.1f kHz", hz / 1000f)
            },
            state.bitDepth?.let { "$it-bit" },
        )
    return if (parts.isEmpty()) source else "$source ${parts.joinToString(" · ")}"
}

private fun providerLabel(id: String): String =
    when (id) {
        "youtube_music" -> "YouTube Music"
        "jamendo" -> "Jamendo"
        "audius" -> "Audius"
        "internet_archive" -> "Internet Archive"
        else -> id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

private val SLEEP_OPTIONS =
    listOf("Off" to 0, "15 min" to 15, "30 min" to 30, "45 min" to 45, "60 min" to 60, "End of track" to null)
