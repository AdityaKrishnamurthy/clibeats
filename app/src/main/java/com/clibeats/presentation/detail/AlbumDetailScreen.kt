@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.clibeats.presentation.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.presentation.navigation.AppRoute

/** Album tracks screen using the same TUI detail view as Library's OpenedGroupView. */
@Composable
fun AlbumDetailScreen(
    route: AppRoute.AlbumDetail,
    onBack: () -> Unit = {},
    viewModel: EntityDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(route.albumName, route.artistName, route.browseId, route.providerId) {
        viewModel.loadAlbum(route.albumName, route.artistName, route.browseId, route.providerId)
    }
    EntityDetailContent(
        fallbackTitle = route.albumName,
        fallbackSubtitle = { "Album · ${it.size} tracks" },
        emptyText = "No tracks found for ${route.albumName}.",
        onBack = onBack,
        viewModel = viewModel,
    )
}
