@file:Suppress("ktlint:standard:function-naming", "ForbiddenImport")

package com.clibeats

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.data.link.IncomingLinks
import com.clibeats.data.link.MusicLink
import com.clibeats.presentation.detail.AlbumDetailScreen
import com.clibeats.presentation.detail.ArtistDetailScreen
import com.clibeats.presentation.home.HomeScreen
import com.clibeats.presentation.importer.ImportScreen
import com.clibeats.presentation.layout.LocalTabSwipeLock
import com.clibeats.presentation.layout.MainLayout
import com.clibeats.presentation.library.LibraryScreen
import com.clibeats.presentation.link.LinkViewModel
import com.clibeats.presentation.navigation.AppRoute
import com.clibeats.presentation.navigation.AppRouter
import com.clibeats.presentation.navigation.LocalAppNavigator
import com.clibeats.presentation.navigation.MainTab
import com.clibeats.presentation.onboarding.OnboardingScreen
import com.clibeats.presentation.queue.QueueScreen
import com.clibeats.presentation.search.SearchScreen
import com.clibeats.presentation.settings.SettingsScreen
import com.clibeats.presentation.theme.CliBeatsTheme
import com.clibeats.presentation.theme.CliBeatsThemeMode
import com.clibeats.presentation.theme.ThemeViewModel
import com.clibeats.presentation.update.UpdatePrompt
import com.clibeats.presentation.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var incomingLinks: IncomingLinks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleLink(intent)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeState by themeViewModel.themeState.collectAsStateWithLifecycle()
            val onboardingDone by themeViewModel.onboardingDone.collectAsStateWithLifecycle()
            val isLight = themeState.mode == CliBeatsThemeMode.LIGHT

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

            CliBeatsTheme(accent = themeState.accent.color, mode = themeState.mode) {
                when (onboardingDone) {
                    null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    false -> OnboardingScreen(currentTheme = themeState.mode)
                    true -> AppContent()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        val text =
            when (intent?.action) {
                Intent.ACTION_VIEW -> intent.dataString
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                else -> null
            } ?: return
        val link = if (intent?.action == Intent.ACTION_VIEW) MusicLink.parse(text) else MusicLink.findIn(text)
        if (link != null) {
            incomingLinks.offer(link)
        } else {
            Toast.makeText(this, "CLIBeats opens songs, playlists and albums.", Toast.LENGTH_LONG).show()
        }
    }
}

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
private fun AppContent() {
    val router = remember { AppRouter() }
    val tabs = MainTab.entries
    val pagerState = rememberPagerState(initialPage = router.activeTab.ordinal) { tabs.size }
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()

    LaunchedEffect(router.activeTab) {
        val target = router.activeTab.ordinal
        if (pagerState.currentPage != target) {
            if (abs(target - pagerState.currentPage) == 1) {
                pagerState.animateScrollToPage(target)
            } else {
                pagerState.scrollToPage(target)
            }
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        val settledTab = tabs[pagerState.settledPage]
        if (settledTab != router.activeTab && router.currentRoute != AppRoute.Queue) {
            router.selectTab(settledTab)
        }
    }

    var focusSearch by remember { mutableStateOf(false) }
    var toggleSearchSource by remember { mutableStateOf(false) }
    var lastSearchTapAt by remember { mutableLongStateOf(0L) }
    var pendingSearchToggle by remember { mutableStateOf<Job?>(null) }
    var toggleHomeSource by remember { mutableStateOf(false) }
    var toggleLibraryMode by remember { mutableStateOf(false) }

    val onTabSelected: (MainTab) -> Unit = { tab ->
        val wasAtRoot = router.activeTab == tab && router.isAtTabRoot(tab)
        val reTapAtRoot = router.selectTab(tab)
        if (tab == MainTab.SEARCH) {
            val now = SystemClock.uptimeMillis()
            val doubleTap = now - lastSearchTapAt < DOUBLE_TAP_MS
            lastSearchTapAt = now
            pendingSearchToggle?.cancel()
            pendingSearchToggle = null
            when {
                doubleTap -> focusSearch = true
                wasAtRoot ->
                    pendingSearchToggle =
                        scope.launch {
                            delay(DOUBLE_TAP_MS)
                            toggleSearchSource = true
                        }
            }
        } else if (reTapAtRoot) {
            when (tab) {
                MainTab.HOME -> toggleHomeSource = true
                MainTab.LIBRARY -> toggleLibraryMode = true
                MainTab.SEARCH, MainTab.SETTINGS -> Unit
            }
        }
    }

    val linkViewModel: LinkViewModel = hiltViewModel()
    val showLinkImport by linkViewModel.showImport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        linkViewModel.messages.collect { message ->
            val length = if (message.length > SHORT_MESSAGE) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(context, message, length).show()
        }
    }

    val updateViewModel: UpdateViewModel = hiltViewModel()
    LaunchedEffect(Unit) { updateViewModel.onAppStart() }
    UpdatePrompt(viewModel = updateViewModel)

    BackHandler(enabled = !showLinkImport && router.canGoBack) { router.popBack() }
    BackHandler(enabled = showLinkImport) { linkViewModel.closeImport() }

    var swipeLockedPages by remember { mutableStateOf(emptySet<Int>()) }

    fun lockSwipe(
        page: Int,
        locked: Boolean,
    ) {
        swipeLockedPages = if (locked) swipeLockedPages + page else swipeLockedPages - page
    }

    CompositionLocalProvider(LocalAppNavigator provides { next -> router.navigate(next) }) {
        MainLayout(
            activeTab = router.activeTab,
            onTabSelected = onTabSelected,
            onQueueClick = {
                if (router.currentRoute == AppRoute.Queue) router.popBack() else router.navigate(AppRoute.Queue)
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    key = { tabs[it].route.key },
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = pagerState.currentPage !in swipeLockedPages,
                ) { page ->
                    val tab = tabs[page]
                    val tabRoute = router.topRoute(tab)
                    val setLocked: (Boolean) -> Unit = remember(page) { { locked -> lockSwipe(page, locked) } }
                    CompositionLocalProvider(LocalTabSwipeLock provides setLocked) {
                        stateHolder.SaveableStateProvider(tabRoute.key) {
                            when (tabRoute) {
                                AppRoute.Home ->
                                    HomeScreen(
                                        toggleSourceRequested = toggleHomeSource,
                                        onToggleSourceHandled = { toggleHomeSource = false },
                                    )
                                AppRoute.Search ->
                                    SearchScreen(
                                        focusRequested = focusSearch,
                                        onFocusHandled = { focusSearch = false },
                                        toggleSourceRequested = toggleSearchSource,
                                        onToggleSourceHandled = { toggleSearchSource = false },
                                    )
                                AppRoute.Library ->
                                    LibraryScreen(
                                        toggleModeRequested = toggleLibraryMode,
                                        onToggleModeHandled = { toggleLibraryMode = false },
                                    )
                                AppRoute.Settings -> SettingsScreen()
                                is AppRoute.ArtistDetail ->
                                    ArtistDetailScreen(route = tabRoute, onBack = { router.popBack() })
                                is AppRoute.AlbumDetail ->
                                    AlbumDetailScreen(route = tabRoute, onBack = { router.popBack() })
                                AppRoute.Queue -> Unit
                            }
                        }
                    }
                }
                if (router.currentRoute == AppRoute.Queue) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        QueueScreen(onClose = { router.popBack() })
                    }
                }
                if (showLinkImport) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ImportScreen(onClose = linkViewModel::closeImport)
                    }
                }
            }
        }
    }
}

private const val DOUBLE_TAP_MS = 300L
private const val SHORT_MESSAGE = 40
