@file:Suppress("ktlint:standard:function-naming")

package com.clibeats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.presentation.home.HomeScreen
import com.clibeats.presentation.layout.MainLayout
import com.clibeats.presentation.layout.NavDestination
import com.clibeats.presentation.library.LibraryScreen
import com.clibeats.presentation.onboarding.OnboardingScreen
import com.clibeats.presentation.playlist.PlaylistScreen
import com.clibeats.presentation.queue.QueueScreen
import com.clibeats.presentation.search.SearchScreen
import com.clibeats.presentation.settings.SettingsScreen
import com.clibeats.presentation.theme.CliBeatsTheme
import com.clibeats.presentation.theme.CliBeatsThemeMode
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
            val onboardingDone by themeViewModel.onboardingDone.collectAsState()
            val isLight = themeState.mode == CliBeatsThemeMode.LIGHT

            // Status/nav bar icons must contrast with the app theme, not the system theme.
            DisposableEffect(isLight) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style =
                    if (isLight) {
                        SystemBarStyle.light(transparent, transparent)
                    } else {
                        SystemBarStyle.dark(transparent)
                    }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            CliBeatsTheme(accent = themeState.accent, mode = themeState.mode) {
                when (onboardingDone) {
                    null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    false -> OnboardingScreen(currentTheme = themeState.mode)
                    true -> AppContent()
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AppContent() {
    // Saveable route: tab survives rotation/process death. Back returns to Home before exiting.
    var route by rememberSaveable { mutableStateOf(NavDestination.Home.route) }
    val selectedDestination =
        NavDestination.all.firstOrNull { it.route == route } ?: NavDestination.Home
    val navigate: (NavDestination) -> Unit = { route = it.route }
    BackHandler(enabled = selectedDestination != NavDestination.Home) { navigate(NavDestination.Home) }

    MainLayout(
        selectedDestination = selectedDestination,
        onDestinationSelected = navigate,
    ) {
        when (selectedDestination) {
            NavDestination.Home -> HomeScreen(onNavigate = navigate)
            NavDestination.Search -> SearchScreen()
            NavDestination.Queue -> QueueScreen()
            NavDestination.Library -> LibraryScreen()
            NavDestination.Playlists -> PlaylistScreen()
            NavDestination.Settings -> SettingsScreen()
        }
    }
}
