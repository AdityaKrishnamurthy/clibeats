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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val tabs = NavDestination.mainTabs
    // Saveable page: tab survives rotation/process death.
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedPage) { tabs.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { savedPage = pagerState.currentPage }

    val navigate: (NavDestination) -> Unit = { destination ->
        val target = tabs.indexOf(destination).coerceAtLeast(0)
        scope.launch {
            // Neighbours slide; far jumps snap so we don't compose every page in between.
            if (abs(target - pagerState.currentPage) == 1) {
                pagerState.animateScrollToPage(target)
            } else {
                pagerState.scrollToPage(target)
            }
        }
    }
    BackHandler(enabled = pagerState.currentPage != 0) { navigate(NavDestination.Home) }

    MainLayout(
        selectedDestination = tabs[pagerState.currentPage],
        onDestinationSelected = navigate,
    ) {
        // Swipe left/right between tabs in bottom-bar order.
        HorizontalPager(
            state = pagerState,
            key = { tabs[it].route },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (tabs[page]) {
                NavDestination.Home -> HomeScreen(onNavigate = navigate)
                NavDestination.Search -> SearchScreen()
                NavDestination.Queue -> QueueScreen()
                NavDestination.Library -> LibraryScreen()
                NavDestination.Playlists -> PlaylistScreen()
                NavDestination.Settings -> SettingsScreen()
            }
        }
    }
}
