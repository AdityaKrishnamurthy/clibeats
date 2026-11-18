package com.clibeats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.presentation.home.HomeScreen
import com.clibeats.presentation.layout.MainLayout
import com.clibeats.presentation.layout.NavDestination
import com.clibeats.presentation.library.LibraryScreen
import com.clibeats.presentation.more.MoreScreen
import com.clibeats.presentation.player.PlayerViewModel
import com.clibeats.presentation.playlist.PlaylistScreen
import com.clibeats.presentation.queue.QueueScreen
import com.clibeats.presentation.search.SearchScreen
import com.clibeats.presentation.settings.SettingsScreen
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.CliBeatsTheme
import com.clibeats.presentation.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeState by themeViewModel.themeState.collectAsState()

            CliBeatsTheme(accent = themeState.accent, mode = themeState.mode) {
                // Saveable route: tab survives rotation/process death. Back returns to Home before exiting.
                var route by rememberSaveable { mutableStateOf(NavDestination.Home.route) }
                val selectedDestination =
                    NavDestination.all.firstOrNull { it.route == route } ?: NavDestination.Home
                val navigate: (NavDestination) -> Unit = { route = it.route }
                BackHandler(enabled = selectedDestination != NavDestination.Home) { navigate(NavDestination.Home) }
                val playerViewModel: PlayerViewModel = hiltViewModel()

                MainLayout(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = navigate,
                ) {
                    when (selectedDestination) {
                        NavDestination.Home ->
                            HomeScreen(
                                onNavigate = navigate,
                                playerViewModel = playerViewModel,
                            )
                        NavDestination.Search -> SearchScreen()
                        NavDestination.Queue -> QueueScreen()
                        NavDestination.Library -> LibraryScreen()
                        NavDestination.More ->
                            MoreScreen(
                                onNavigate = navigate,
                            )
                        NavDestination.Playlists -> PlaylistScreen()
                        NavDestination.Settings -> SettingsScreen()
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = selectedDestination.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = CliBeatsTextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
