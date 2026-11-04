package org.sagisolas.recipehost.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    private fun navigator(catalogEmpty: Boolean = false) = Navigator(CatalogEmptiness { catalogEmpty })

    private val Navigator.screen get() = state.value.currentScreen

    @Test
    fun pushAndPopWithinTheCurrentTab() {
        val navigator = navigator()
        navigator.push(Screen.ModList)
        navigator.push(Screen.Item("minecraft:stone"))
        assertEquals(Screen.Item("minecraft:stone"), navigator.screen)

        navigator.pop()
        assertEquals(Screen.ModList, navigator.screen)
        navigator.pop()
        assertEquals(Screen.Grid, navigator.screen)
    }

    @Test
    fun eachTabKeepsItsOwnStack() {
        val navigator = navigator()
        navigator.push(Screen.Item("minecraft:stone"))

        navigator.selectTab(Tab.Settings)
        assertEquals(Screen.Settings, navigator.screen)
        navigator.push(Screen.Item("create:cogwheel"))

        navigator.selectTab(Tab.Items)
        assertEquals(Screen.Item("minecraft:stone"), navigator.screen)
        navigator.selectTab(Tab.Settings)
        assertEquals(Screen.Item("create:cogwheel"), navigator.screen)
    }

    @Test
    fun backPopsTheCurrentTabThenGoesToItemsThenExits() {
        val navigator = navigator()
        navigator.push(Screen.ModList)
        navigator.selectTab(Tab.Settings)
        navigator.push(Screen.Item("minecraft:stone"))

        assertTrue(navigator.state.value.canGoBack)
        assertTrue(navigator.back())
        assertEquals(Screen.Settings, navigator.screen)

        assertTrue(navigator.back())
        assertEquals(Tab.Items, navigator.state.value.selectedTab)
        assertEquals(Screen.ModList, navigator.screen)

        assertTrue(navigator.back())
        assertEquals(Screen.Grid, navigator.screen)

        assertFalse(navigator.state.value.canGoBack)
        assertFalse(navigator.back(), "back at the Items root leaves the app")
        assertEquals(Screen.Grid, navigator.screen)
    }

    @Test
    fun reselectingTheActiveTabPopsItToItsRoot() {
        val navigator = navigator()
        navigator.push(Screen.ModList)
        navigator.push(Screen.Item("minecraft:stone"))

        navigator.selectTab(Tab.Items)
        assertEquals(Screen.Grid, navigator.screen)
        assertEquals(listOf(Screen.Grid), navigator.state.value.stacks.getValue(Tab.Items))
    }

    @Test
    fun startsAtWelcomeWhenTheCatalogIsEmpty() {
        assertEquals(Route.Welcome, navigator(catalogEmpty = true).state.value.route)
    }

    @Test
    fun startsAtTheItemsTabWhenTheCatalogHasItems() {
        val state = navigator(catalogEmpty = false).state.value
        assertEquals(Route.Root, state.route)
        assertEquals(Tab.Items, state.selectedTab)
    }

    @Test
    fun showRootLeavesAFullScreenRouteForTheGivenTab() {
        val navigator = navigator(catalogEmpty = true)
        navigator.showScanProgress()
        assertEquals(Route.ScanProgress, navigator.state.value.route)
        assertFalse(navigator.state.value.canGoBack, "back on a full-screen route is left to the system")

        navigator.showRoot(Tab.Items)
        assertEquals(Route.Root, navigator.state.value.route)
        assertEquals(Tab.Items, navigator.state.value.selectedTab)
        assertEquals(Screen.Grid, navigator.screen)
    }
}
