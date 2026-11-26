package com.clibeats.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/** The four music-first tabs in the bottom bar. Queue and Settings live in the top bar. */
enum class MainTab(
    val route: AppRoute.Tab,
    val label: String,
    val icon: ImageVector,
) {
    HOME(AppRoute.Home, "HOME", Icons.Outlined.Home),
    SEARCH(AppRoute.Search, "SEARCH", Icons.Outlined.Search),
    LIBRARY(AppRoute.Library, "LIBRARY", Icons.Outlined.LibraryMusic),
    PLAYLISTS(AppRoute.Playlists, "PLAYLISTS", Icons.AutoMirrored.Outlined.PlaylistPlay),
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

    data object Playlists : Tab {
        override val breadcrumb: String = "~/playlists"
    }

    data class PlaylistDetail(
        val playlistId: String,
        val title: String,
    ) : AppRoute {
        override val breadcrumb: String = "~/playlists/${title.slug()}"
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

    data object Settings : AppRoute {
        override val breadcrumb: String = "~/settings"
    }
}

private fun String.slug(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "untitled" }
