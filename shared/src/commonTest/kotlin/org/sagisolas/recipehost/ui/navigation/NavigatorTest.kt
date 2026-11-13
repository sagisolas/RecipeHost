/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NavigatorTest {
    /** Built the way `rememberNavigator` builds it, minus the saving, which Navigation 3 owns. */
    private fun navigator(start: Route = Route.Root) =
        Navigator(
            route = mutableStateOf(start),
            selectedTab = mutableStateOf(Tab.Items),
            stacks = Tab.entries.associateWith { NavBackStack<NavKey>(StackEntry(it.root)) },
        )

    /** What `RootScaffold` gives `NavDisplay`: the selected tab's stack alone. */
    private val Navigator.displayed get() = stacks.getValue(selectedTab).map { (it as StackEntry).screen }

    @Test
    fun pushAndPopWithinTheCurrentTab() {
        val navigator = navigator()
        navigator.push(Screen.ModList)
        navigator.push(Screen.Item("minecraft:stone"))
        assertEquals(Screen.Item("minecraft:stone"), navigator.currentScreen)

        navigator.pop()
        assertEquals(Screen.ModList, navigator.currentScreen)
        navigator.pop()
        assertEquals(Screen.Grid, navigator.currentScreen)
    }

    @Test
    fun eachTabKeepsItsOwnStack() {
        val navigator = navigator()
        navigator.push(Screen.Item("minecraft:stone"))

        navigator.selectTab(Tab.Settings)
        assertEquals(Screen.Settings, navigator.currentScreen)
        navigator.push(Screen.Item("create:cogwheel"))

        navigator.selectTab(Tab.Items)
        assertEquals(Screen.Item("minecraft:stone"), navigator.currentScreen)
        navigator.selectTab(Tab.Settings)
        assertEquals(Screen.Item("create:cogwheel"), navigator.currentScreen)
    }

    @Test
    fun backPopsTheCurrentTabThenExitsFromItsRoot() {
        val navigator = navigator()
        navigator.push(Screen.ModList)
        navigator.selectTab(Tab.Settings)
        navigator.push(Screen.Item("minecraft:stone"))
        assertEquals(listOf(Screen.Settings, Screen.Item("minecraft:stone")), navigator.displayed)

        navigator.pop()
        assertEquals(
            listOf(Screen.Settings),
            navigator.displayed,
            "one entry left, so NavDisplay leaves back to the system and the app exits",
        )
        assertEquals(Tab.Settings, navigator.selectedTab, "back never switches tabs")
    }

    @Test
    fun reselectingTheActiveTabPopsItToItsRoot() {
        val navigator = navigator()
        navigator.push(Screen.ModList)
        navigator.push(Screen.Item("minecraft:stone"))

        navigator.selectTab(Tab.Items)
        assertEquals(listOf(Screen.Grid), navigator.stacks.getValue(Tab.Items).map { (it as StackEntry).screen })
    }

    @Test
    fun showRootLeavesAFullScreenRouteForTheGivenTab() {
        val navigator = navigator(start = Route.Welcome)
        navigator.showScanProgress()
        assertEquals(Route.ScanProgress, navigator.route)

        navigator.showRoot(Tab.Items)
        assertEquals(Route.Root, navigator.route)
        assertEquals(Tab.Items, navigator.selectedTab)
        assertEquals(Screen.Grid, navigator.currentScreen)
    }

    @Test
    fun showingWelcomeDiscardsEveryTabsStack() {
        val navigator = navigator()
        navigator.push(Screen.Item("minecraft:stone"))
        navigator.selectTab(Tab.Settings)
        navigator.push(Screen.Item("minecraft:stick"))
        val oldEntries = navigator.stacks.values.flatten()

        navigator.showWelcome()

        assertEquals(Route.Welcome, navigator.route)
        assertEquals(Tab.Items, navigator.selectedTab)
        assertEquals(listOf(Screen.Grid), navigator.displayed)
        assertEquals(
            listOf(Screen.Settings),
            navigator.stacks.getValue(Tab.Settings).map { (it as StackEntry).screen },
        )
        val newEntries = navigator.stacks.values.flatten()
        assertTrue(newEntries.none { it in oldEntries }, "the roots are fresh entries, with no saved state")
    }

    @Test
    fun theSameScreenPushedTwiceIsTwoEntries() {
        val navigator = navigator()
        navigator.push(Screen.Item("minecraft:stone"))
        navigator.push(Screen.Item("minecraft:stick"))
        navigator.push(Screen.Item("minecraft:stone"))
        navigator.selectTab(Tab.Settings)
        navigator.push(Screen.Item("minecraft:stone"))

        val ids =
            navigator.stacks.values
                .flatten()
                .map { (it as StackEntry).id }
        assertEquals(ids.size, ids.toSet().size, "every entry, in every tab, has its own id")
        val stones =
            navigator.stacks.getValue(Tab.Items).filter {
                (it as StackEntry).screen ==
                    Screen.Item("minecraft:stone")
            }
        assertNotEquals(stones[0], stones[1])
    }
}
