@file:Suppress("ktlint:standard:function-naming")

package com.clibeats.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.domain.model.Track
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

/**
 * The menu that opens when you hold a song: play next, add to queue, add to a playlist, add to favorites.
 * [onPicked] runs for every action taken (Search uses it to remember the song).
 */
@Suppress("FunctionNaming", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    track: Track,
    onDismiss: () -> Unit,
    onPicked: (Track) -> Unit = {},
    actions: TrackActionsViewModel = hiltViewModel(),
) {
    var pickPlaylist by remember { mutableStateOf(false) }
    if (pickPlaylist) {
        AddToPlaylistDialog(track = track, onDismiss = onDismiss)
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CliBeatsSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(artworkUrl = track.artworkUrl, size = 48)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CliBeatsTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = CliBeatsTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = CliBeatsDivider)
            SheetAction(Icons.AutoMirrored.Outlined.PlaylistPlay, "Play next") {
                actions.playNext(track)
                onPicked(track)
                onDismiss()
            }
            SheetAction(Icons.AutoMirrored.Outlined.QueueMusic, "Add to queue") {
                actions.addToQueue(track)
                onPicked(track)
                onDismiss()
            }
            SheetAction(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to playlist") {
                onPicked(track)
                pickPlaylist = true
            }
            SheetAction(Icons.Outlined.FavoriteBorder, "Add to favorites") {
                actions.favorite(track)
                onPicked(track)
                onDismiss()
            }
            val downloadedIds by actions.downloadedTrackIds.collectAsState()
            val isDownloaded = track.id in downloadedIds
            if (isDownloaded) {
                SheetAction(Icons.Outlined.Delete, "Remove download") {
                    actions.removeDownload(track)
                    onPicked(track)
                    onDismiss()
                }
            } else {
                SheetAction(Icons.Outlined.FileDownload, "Download for offline") {
                    actions.download(track)
                    onPicked(track)
                    onDismiss()
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = CliBeatsAccent, modifier = Modifier.size(22.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = CliBeatsTextPrimary)
    }
}
