@file:Suppress("ktlint:standard:function-naming")

package com.clibeats.presentation.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clibeats.domain.provider.MusicProvider
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsTextSecondary

/** One-tap switch between local library and online providers (Home + Search). */
@Suppress("FunctionNaming")
@Composable
fun ProviderSwitcher(
    providers: List<MusicProvider>,
    activeProviderId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        providers.forEach { provider ->
            val selected = provider.providerId == activeProviderId
            val color = if (selected) CliBeatsAccent else CliBeatsTextSecondary
            Text(
                text = if (selected) "[● ${provider.displayName}]" else "[ ${provider.displayName} ]",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier =
                    Modifier
                        .semantics { this.selected = selected }
                        .border(1.dp, if (selected) CliBeatsAccent else CliBeatsBorderInactive)
                        .clickable(role = Role.Tab) { onSelect(provider.providerId) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
