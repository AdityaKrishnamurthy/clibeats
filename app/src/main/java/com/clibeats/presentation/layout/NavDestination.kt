package com.clibeats.presentation.layout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * All navigable destinations in the CLIBeats app.
 *
 * Bottom navigation: Home, Search, Library, Settings ([mainTabs]). Playlists live inside Library;
 * Queue opens full screen from the mini player or Now Playing.
 */
sealed class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
) {
    data object Home : NavDestination(
        route = "home",
        label = "Home",
        icon = Icons.Outlined.Home,
        contentDescription = "Home",
    )

    data object Search : NavDestination(
        route = "search",
        label = "Search",
        icon = Icons.Outlined.Search,
        contentDescription = "Search",
    )

    data object Library : NavDestination(
        route = "library",
        label = "Library",
        icon = Icons.Outlined.LibraryMusic,
        contentDescription = "Library",
    )

    data object Playlists : NavDestination(
        route = "playlists",
        label = "Playlists",
        icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
        contentDescription = "Playlists",
    )

    data object Queue : NavDestination(
        route = "queue",
        label = "Queue",
        icon = Icons.AutoMirrored.Outlined.QueueMusic,
        contentDescription = "Queue",
    )

    data object Settings : NavDestination(
        route = "settings",
        label = "Settings",
        icon = Icons.Outlined.Settings,
        contentDescription = "Settings",
    )

    companion object {
        /** Bottom navigation tabs. */
        val mainTabs: List<NavDestination>
            get() = listOf(Home, Search, Library, Settings)

        /** Every destination in the app. */
        val all: List<NavDestination>
            get() = mainTabs + listOf(Queue, Playlists)
    }
}
