package org.sagisolas.recipehost.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What fills the window: a full-screen route, or the bottom-navigation [Root]. */
internal sealed interface Route {
    data object Welcome : Route
    data object ScanProgress : Route

    /** The tabs, each showing the top of its own stack above the bottom bar. */
    data object Root : Route
}

/** A screen inside a bottom-navigation tab. */
internal sealed interface Screen {
    data object Grid : Screen
    data object ModList : Screen
    data object Settings : Screen
    data class Item(val itemId: String) : Screen
}

/**
 * The bottom-navigation tabs, in bar order. A new tab is a new entry with its first screen, plus
 * its bar item in `RootScaffold`.
 */
internal enum class Tab(val root: Screen) {
    Items(Screen.Grid),
    Settings(Screen.Settings),
}

/** Answers whether the `Catalog` holds nothing yet, which makes Welcome the start route. */
internal fun interface CatalogEmptiness {
    fun isEmpty(): Boolean
}

internal data class NavState(
    val route: Route,
    val selectedTab: Tab,
    /** One back stack per tab, root first; never empty. */
    val stacks: Map<Tab, List<Screen>>,
) {
    val currentScreen: Screen get() = stacks.getValue(selectedTab).last()

    /**
     * Whether [Navigator.back] handles back. It doesn't at the Items root, where back leaves the
     * app, nor on a full-screen route: Welcome is the app's only screen then, and a running
     * Scan keeps going in the app scope.
     */
    val canGoBack: Boolean
        get() = route == Route.Root && (selectedTab != Tab.Items || stacks.getValue(selectedTab).size > 1)
}

/**
 * The app's navigation state: a full-screen route or the bottom-navigation root, with a
 * hand-written back stack per [Tab]. Call it from the main thread.
 */
internal class Navigator(catalog: CatalogEmptiness) {
    private val _state = MutableStateFlow(
        NavState(
            route = if (catalog.isEmpty()) Route.Welcome else Route.Root,
            selectedTab = Tab.Items,
            stacks = Tab.entries.associateWith { listOf(it.root) },
        ),
    )
    val state: StateFlow<NavState> = _state.asStateFlow()

    /** Pushes [screen] onto the current tab's stack. */
    fun push(screen: Screen) = updateStack { it + screen }

    /** Pops the current tab's stack; does nothing at its root. */
    fun pop() = updateStack { if (it.size > 1) it.dropLast(1) else it }

    /**
     * Pops the current tab; at a tab's root, switches to Items. Returns false, and does nothing,
     * when [NavState.canGoBack] is false.
     */
    fun back(): Boolean {
        val state = _state.value
        when {
            !state.canGoBack -> return false
            state.stacks.getValue(state.selectedTab).size > 1 -> pop()
            else -> selectTab(Tab.Items)
        }
        return true
    }

    /** Switches to [tab], keeping its stack; reselecting the active tab pops it to its root. */
    fun selectTab(tab: Tab) {
        val state = _state.value
        _state.value = if (tab == state.selectedTab) {
            state.copy(stacks = state.stacks + (tab to listOf(tab.root)))
        } else {
            state.copy(selectedTab = tab)
        }
    }

    fun showScanProgress() {
        _state.value = _state.value.copy(route = Route.ScanProgress)
    }

    /** Leaves a full-screen route for the bottom-navigation root, on [tab]. */
    fun showRoot(tab: Tab) {
        _state.value = _state.value.copy(route = Route.Root, selectedTab = tab)
    }

    private fun updateStack(change: (List<Screen>) -> List<Screen>) {
        val state = _state.value
        val tab = state.selectedTab
        _state.value = state.copy(stacks = state.stacks + (tab to change(state.stacks.getValue(tab))))
    }
}
