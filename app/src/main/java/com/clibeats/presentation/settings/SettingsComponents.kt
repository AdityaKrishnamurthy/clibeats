@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionNaming",
    "MagicNumber",
    "ForbiddenImport",
    "MatchingDeclarationName",
)

package com.clibeats.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.theme.LocalAccentColor

/** The pages Settings opens from its main list. */
enum class SettingsSection(
    val title: String,
) {
    APPEARANCE("Appearance"),
    LOCAL_MUSIC("Local music"),
    GESTURES("Gestures"),
    STORAGE("Storage & cache"),
    BACKUP("Backup"),
    UPDATES("Updates"),
}

internal val SettingsShape = RoundedCornerShape(16.dp)

/**
 * A panel of settings: the theme's surface colour (so Berry, Ocean, Grove... tint it) with a faint outline.
 * The page header names the page, so panels need no title; [label] names a part of a page when it has several.
 */
@Composable
internal fun SettingsPanel(
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        label?.let { SettingsGroupLabel(it) }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(SettingsShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), SettingsShape)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Small caps label above a group of rows. */
@Composable
internal fun SettingsGroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp, top = 4.dp).semantics { heading() },
    )
}

/** A list of rows in one rounded group (rows are separated by thin lines). */
@Composable
internal fun SettingsGroup(
    label: String,
    rows: List<@Composable () -> Unit>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsGroupLabel(label)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(SettingsShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), SettingsShape),
        ) {
            rows.forEachIndexed { i, row ->
                if (i > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                row()
            }
        }
    }
}

/** The icon in a soft accent circle at the start of a settings row. */
@Composable
private fun RowIcon(icon: ImageVector) {
    val accent = LocalAccentColor.current
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
    }
}

/** A row that opens a page: icon, title, what's set now, and a chevron. */
@Composable
internal fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A row with an on/off switch; the whole row toggles it. */
@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector?,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { onCheckedChange(!checked) }
                .padding(horizontal = if (icon != null) 14.dp else 0.dp, vertical = if (icon != null) 12.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RowIcon(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A switch in the accent colour. */
@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val accent = LocalAccentColor.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors =
            SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedThumbColor = readableOn(accent),
                checkedBorderColor = accent,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
    )
}

/** Black or white, whichever reads on [color]. */
internal fun readableOn(color: Color): Color =
    if (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue > 0.6f) Color.Black else Color.White

/** A settings page's top bar: back, and the page's name. */
@Composable
internal fun SettingsPageHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back to settings",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** A thin bar showing how full something is (0..1). */
@Composable
internal fun UsageBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccentColor.current
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(shape)
                    .background(accent),
        )
    }
}
