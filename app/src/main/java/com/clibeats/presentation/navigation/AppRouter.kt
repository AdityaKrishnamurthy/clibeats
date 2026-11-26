package com.clibeats.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Per-tab back stacks plus one global utility stack for Queue and Settings.
 *
 * Pure Kotlin so navigation history, tab isolation, double-tap-to-root, and system-back
 * order are unit-testable without a UI host.
 */
class AppRouter(
    startTab: MainTab = MainTab.HOME,
) {
    private val stacks: Map<MainTab, MutableList<AppRoute>> =
        MainTab.entries.associateWith { mutableStateListOf<AppRoute>(it.route) }

    private val utilityStack = mutableStateListOf<AppRoute>()

    var activeTab: MainTab by mutableStateOf(startTab)
        private set

    val currentRoute: AppRoute
        get() = utilityStack.lastOrNull() ?: stack(activeTab).last()

    val canGoBack: Boolean
        get() = utilityStack.isNotEmpty() || stack(activeTab).size > 1 || activeTab != MainTab.HOME

    fun isAtTabRoot(tab: MainTab = activeTab): Boolean = utilityStack.isEmpty() && stack(tab).size == 1

    /**
     * Switches tabs. Tapping an active tab pops back to its root; returns true only when the
     * caller should run the tab's re-tap action (toggle Online/Local, focus search, etc.).
     */
    fun selectTab(tab: MainTab): Boolean {
        val tabStack = stack(tab)
        return when {
            tab != activeTab -> {
                utilityStack.clear()
                activeTab = tab
                false
            }
            utilityStack.isNotEmpty() -> {
                utilityStack.clear()
                false
            }
            tabStack.size > 1 -> {
                tabStack.subList(1, tabStack.size).clear()
                false
            }
            else -> true
        }
    }

    fun navigate(route: AppRoute) {
        when (route) {
            is AppRoute.Tab -> {
                utilityStack.clear()
                activeTab = MainTab.entries.first { it.route == route }
            }

            AppRoute.Queue, AppRoute.Settings -> {
                if (utilityStack.lastOrNull() != route) utilityStack.add(route)
            }

            else -> {
                utilityStack.clear()
                val tabStack = stack(activeTab)
                if (tabStack.lastOrNull() != route) tabStack.add(route)
            }
        }
    }

    /** Pops the utility stack, then the active tab's stack, then switches back to Home. */
    fun popBack(): Boolean =
        when {
            utilityStack.isNotEmpty() -> {
                utilityStack.removeAt(utilityStack.lastIndex)
                true
            }

            stack(activeTab).size > 1 -> {
                val tabStack = stack(activeTab)
                tabStack.removeAt(tabStack.lastIndex)
                true
            }

            activeTab != MainTab.HOME -> {
                activeTab = MainTab.HOME
                true
            }

            else -> false
        }

    private fun stack(tab: MainTab): MutableList<AppRoute> = stacks.getValue(tab)
}
