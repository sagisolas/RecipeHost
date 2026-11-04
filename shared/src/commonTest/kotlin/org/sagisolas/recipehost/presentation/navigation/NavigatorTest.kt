package org.sagisolas.recipehost.presentation.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NavigatorTest {

    /** Built the way `rememberNavigator` builds it, minus the saving, which Navigation 3 owns. */
    private fun navigator(start: Route = Route.Root) = Navigator(
        route = mutableStateOf(start),
        selectedTab = mutableStateOf(Tab.Items),
        stacks = Tab.entries.associateWith { NavBackStack<NavKey>(StackEntry(it.root)) },
    )

    /** What `NavDisplay` is given: Items' stack, then the selected tab's. */
    private val Navigator.displayed get() = tabsInUse.flatMap { tab -> stacks.getValue(tab).map { (it as StackEntry).screen } }

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
    fun backPopsTheCurrentTabThenGoesToItemsThenExits() {
        val navigator = navigator()
        navigator.push(Screen.ModList)
        navigator.selectTab(Tab.Settings)
        navigator.push(Screen.Item("minecraft:stone"))
        assertEquals(
            listOf(Screen.Grid, Screen.ModList, Screen.Settings, Screen.Item("minecraft:stone")),
            navigator.displayed,
        )

        navigator.pop()
        assertEquals(Screen.Settings, navigator.currentScreen)

        navigator.pop()
        assertEquals(Tab.Items, navigator.selectedTab)
        assertEquals(Screen.ModList, navigator.currentScreen)

        navigator.pop()
        assertEquals(
            listOf(Screen.Grid),
            navigator.displayed,
            "one entry left, so NavDisplay leaves back to the system and the app exits",
        )
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
    fun theSameScreenPushedTwiceIsTwoEntries() {
        val navigator = navigator()
        navigator.push(Screen.Item("minecraft:stone"))
        navigator.push(Screen.Item("minecraft:stick"))
        navigator.push(Screen.Item("minecraft:stone"))
        navigator.selectTab(Tab.Settings)
        navigator.push(Screen.Item("minecraft:stone"))

        val ids = navigator.stacks.values.flatten().map { (it as StackEntry).id }
        assertEquals(ids.size, ids.toSet().size, "every entry, in every tab, has its own id")
        val stones = navigator.stacks.getValue(Tab.Items).filter { (it as StackEntry).screen == Screen.Item("minecraft:stone") }
        assertNotEquals(stones[0], stones[1])
    }
}
