@file:Suppress("ktlint:standard:function-naming")

package com.clibeats.presentation.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clibeats.domain.provider.MusicProvider
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsTextSecondary

const val LOCAL_PROVIDER_ID = "local"

/**
 * Two-way source switch: [ ONLINE: <provider> ▾ ] | [ LOCAL ].
 * The online segment opens a dropdown of online providers (YouTube Music, Internet Archive, ...).
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun ProviderSwitcher(
    providers: List<MusicProvider>,
    activeProviderId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val online = providers.filter { it.providerId != LOCAL_PROVIDER_ID }
    val isLocal = activeProviderId == LOCAL_PROVIDER_ID
    val onlineCurrent =
        online.firstOrNull { it.providerId == activeProviderId }
            ?: online.firstOrNull { it.providerId == ProviderRegistry.DEFAULT_PROVIDER_ID }
            ?: online.firstOrNull()
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Segment(
                label = "ONLINE: ${onlineCurrent?.displayName ?: "—"} ▾",
                selected = !isLocal,
                onClick = {
                    // First tap switches to online; tapping while online opens the provider list.
                    if (isLocal && onlineCurrent != null) onSelect(onlineCurrent.providerId) else menuOpen = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                online.forEach { provider ->
                    val chosen = provider.providerId == activeProviderId
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (chosen) "● ${provider.displayName}" else "  ${provider.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (chosen) CliBeatsAccent else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onSelect(provider.providerId)
                        },
                    )
                }
            }
        }
        if (providers.any { it.providerId == LOCAL_PROVIDER_ID }) {
            Segment(
                label = "LOCAL",
                selected = isLocal,
                onClick = { onSelect(LOCAL_PROVIDER_ID) },
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color: Color = if (selected) CliBeatsAccent else CliBeatsTextSecondary
    Text(
        text = if (selected) "[● $label]" else "[ $label ]",
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        modifier =
            modifier
                .semantics { this.selected = selected }
                .border(1.dp, if (selected) CliBeatsAccent else CliBeatsBorderInactive)
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}
