package com.clibeats.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom navigation tabs matching v2.6.1 layout: Home, Search, Library, Settings.
 * Playlists live inside Library; Queue opens full screen from the mini player or Now Playing.
 */
enum class MainTab(
    val route: AppRoute.Tab,
    val label: String,
    val icon: ImageVector,
) {
    HOME(AppRoute.Home, "Home", Icons.Outlined.Home),
    SEARCH(AppRoute.Search, "Search", Icons.Outlined.Search),
    LIBRARY(AppRoute.Library, "Library", Icons.Outlined.LibraryMusic),
    SETTINGS(AppRoute.Settings, "Settings", Icons.Outlined.Settings),
}

val LocalAppNavigator = compositionLocalOf<(AppRoute) -> Unit> { {} }

sealed interface AppRoute {
    val breadcrumb: String

    val key: String
        get() = breadcrumb

    sealed interface Tab : AppRoute

    data object Home : Tab {
        override val breadcrumb: String = "~/home"
    }

    data object Search : Tab {
        override val breadcrumb: String = "~/search"
    }

    data object Library : Tab {
        override val breadcrumb: String = "~/library"
    }

    data object Settings : Tab {
        override val breadcrumb: String = "~/settings"
    }

    data class ArtistDetail(
        val artistName: String,
        val browseId: String? = null,
        val providerId: String = "youtube_music",
    ) : AppRoute {
        override val breadcrumb: String = "~/artist/${artistName.slug()}"
    }

    data class AlbumDetail(
        val albumName: String,
        val artistName: String,
        val browseId: String? = null,
        val providerId: String = "youtube_music",
    ) : AppRoute {
        override val breadcrumb: String = "~/album/${albumName.slug()}"
    }

    data object Queue : AppRoute {
        override val breadcrumb: String = "~/queue"
    }
}

private fun String.slug(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "untitled" }
