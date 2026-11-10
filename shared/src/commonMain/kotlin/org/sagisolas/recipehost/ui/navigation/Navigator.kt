package org.sagisolas.recipehost.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What fills the window: a full-screen route, or the bottom-navigation [Root]. */
@Serializable
internal sealed interface Route {
    @Serializable data object Welcome : Route

    @Serializable data object ScanProgress : Route

    /** The tabs, each showing the top of its own stack above the bottom bar. */
    @Serializable data object Root : Route
}

/** A screen inside a bottom-navigation tab. */
@Serializable
internal sealed interface Screen {
    @Serializable data object Grid : Screen

    @Serializable data object ModList : Screen

    @Serializable data object Settings : Screen

    @Serializable data class Item(
        val itemId: String,
    ) : Screen
}

/**
 * One [screen] on a tab's back stack: the Navigation 3 key. Its random [id] is part of its
 * content key, so the same screen pushed twice is two entries, each with its own saved state
 * and ViewModels.
 */
@Serializable
internal data class StackEntry(
    val screen: Screen,
    val id: String,
) : NavKey {
    @OptIn(ExperimentalUuidApi::class)
    constructor(screen: Screen) : this(screen, Uuid.random().toString())
}

/**
 * The bottom-navigation tabs, in bar order. A new tab is a new entry with its first screen, plus
 * its bar item in `RootScaffold`.
 */
@Serializable
internal enum class Tab(
    val root: Screen,
) {
    Items(Screen.Grid),
    Settings(Screen.Settings),
}

/** Answers whether the `Catalog` holds nothing yet, which makes Welcome the start route. */
internal fun interface CatalogEmptiness {
    fun isEmpty(): Boolean
}

/** Lets `rememberNavBackStack` save [StackEntry] keys on every platform, iOS included. */
private val navKeys =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) { subclass(StackEntry::class, StackEntry.serializer()) }
            }
    }

/**
 * The [Navigator] for the app's UI. Navigation 3 and Compose save and restore all of it (back
 * stacks, selected tab, full-screen route) across activity recreation and process death;
 * [startRoute] is only asked for on a fresh start.
 */
@Composable
internal fun rememberNavigator(startRoute: () -> Route): Navigator {
    val route =
        rememberSerializable(stateSerializer = Route.serializer()) {
            mutableStateOf(startRoute())
        }
    val selectedTab =
        rememberSerializable(stateSerializer = Tab.serializer()) {
            mutableStateOf(Tab.Items)
        }
    val stacks = Tab.entries.associateWith { rememberNavBackStack(navKeys, StackEntry(it.root)) }
    return remember(route, selectedTab, stacks) { Navigator(route, selectedTab, stacks) }
}

/**
 * Navigation over state that Navigation 3 owns: one stack per [Tab], of which only the selected
 * tab's is displayed. Back pops within that tab, and at its root leaves the app; the bottom bar
 * is never part of back. `NavDisplay` handles back itself and calls [pop].
 */
internal class Navigator(
    route: MutableState<Route>,
    selectedTab: MutableState<Tab>,
    /** One back stack per tab, root first; never empty. */
    val stacks: Map<Tab, NavBackStack<NavKey>>,
) {
    var route: Route by route
        private set

    var selectedTab: Tab by selectedTab
        private set

    val currentScreen: Screen get() = (stacks.getValue(selectedTab).last() as StackEntry).screen

    /** Pushes [screen] onto the current tab's stack, as a new entry even if it's already there. */
    fun push(screen: Screen) {
        stacks.getValue(selectedTab).add(StackEntry(screen))
    }

    /**
     * Back, from `NavDisplay`: pops the current tab. `NavDisplay` never calls it at a tab's root,
     * where back is the system's and leaves the app.
     */
    fun pop() {
        val stack = stacks.getValue(selectedTab)
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Switches to [tab], keeping its stack; reselecting the active tab pops it to its root. */
    fun selectTab(tab: Tab) {
        if (tab == selectedTab) {
            val stack = stacks.getValue(tab)
            stack.subList(1, stack.size).clear()
        } else {
            selectedTab = tab
        }
    }

    fun showScanProgress() {
        route = Route.ScanProgress
    }

    /**
     * Leaves the bottom-navigation root for Welcome, discarding every tab's stack: each tab starts
     * again from a fresh root entry, with no saved state, when the root comes back.
     */
    fun showWelcome() {
        route = Route.Welcome
        selectedTab = Tab.Items
        stacks.forEach { (tab, stack) ->
            // Adding first keeps the stack from ever being empty.
            stack.add(StackEntry(tab.root))
            stack.subList(0, stack.lastIndex).clear()
        }
    }

    /** Leaves a full-screen route for the bottom-navigation root, on [tab]. */
    fun showRoot(tab: Tab) {
        route = Route.Root
        selectedTab = tab
    }
}
