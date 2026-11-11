package org.sagisolas.recipehost

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.sagisolas.recipehost.ui.AppTheme
import org.sagisolas.recipehost.ui.RootScaffold
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.mcBackground
import org.sagisolas.recipehost.ui.navigation.Navigator
import org.sagisolas.recipehost.ui.navigation.Route
import org.sagisolas.recipehost.ui.navigation.Screen
import org.sagisolas.recipehost.ui.navigation.Tab
import org.sagisolas.recipehost.ui.navigation.noTransition
import org.sagisolas.recipehost.ui.navigation.rememberNavigator
import org.sagisolas.recipehost.ui.screens.BookmarksScreen
import org.sagisolas.recipehost.ui.screens.ItemGridScreen
import org.sagisolas.recipehost.ui.screens.ItemScreen
import org.sagisolas.recipehost.ui.screens.ModListScreen
import org.sagisolas.recipehost.ui.screens.ScanProgressScreen
import org.sagisolas.recipehost.ui.screens.SettingsScreen
import org.sagisolas.recipehost.ui.screens.WelcomeScreen

/** The whole app UI, over the instances [graph] holds. */
@Composable
fun App(graph: AppGraph) {
    AppTheme {
        val navigator =
            rememberNavigator(
                startRoute = { if (graph.catalogEmptiness.isEmpty()) Route.Welcome else Route.Root },
                startTab = { if (graph.bookmarksEmptiness.isEmpty()) Tab.Items else Tab.Bookmarks },
            )
        LaunchedEffect(navigator) {
            graph.pairing.progressRequests.collect { navigator.showScanProgress() }
        }
        // Edge to edge: the background runs behind the system bars, and each screen keeps its own
        // content clear of them.
        Box(Modifier.fillMaxSize().mcBackground(McTheme.skin.screen)) {
            // One entry: full-screen routes replace each other, and back on them is the system's.
            // Each route gets its own ViewModels, cleared when another route replaces it.
            NavDisplay(
                backStack = listOf(navigator.route),
                onBack = {},
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                transitionSpec = { noTransition() },
                popTransitionSpec = { noTransition() },
                predictivePopTransitionSpec = { noTransition() },
                entryProvider = { route ->
                    NavEntry(route) {
                        when (it) {
                            Route.Welcome -> {
                                WelcomeScreen(viewModel { graph.welcomeViewModel() })
                            }

                            Route.ScanProgress -> {
                                ScanProgressScreen(
                                    viewModel { graph.scanProgressViewModel() },
                                    onCommitted = { navigator.showRoot(Tab.Items) },
                                )
                            }

                            Route.Root -> {
                                RootScaffold(navigator) { screen -> TabScreen(screen, graph, navigator) }
                            }
                        }
                    }
                },
            )
        }
    }
}

/** A screen inside a tab; each entry's `viewModel { }` is scoped to that entry. */
@Composable
private fun TabScreen(
    screen: Screen,
    graph: AppGraph,
    navigator: Navigator,
) {
    when (screen) {
        Screen.Grid -> {
            ItemGridScreen(viewModel { graph.itemGridViewModel() }, navigator, graph.iconAtlases)
        }

        Screen.ModList -> {
            ModListScreen(viewModel { graph.modListViewModel() }, navigator)
        }

        is Screen.Item -> {
            ItemScreen(
                viewModel { graph.itemViewModel(screen.itemId) },
                navigator,
                graph.iconAtlases,
                graph.layoutImages,
            )
        }

        Screen.Bookmarks -> {
            BookmarksScreen(
                viewModel { graph.bookmarksViewModel() },
                navigator,
                graph.iconAtlases,
                graph.layoutImages,
            )
        }

        Screen.Settings -> {
            SettingsScreen(viewModel { graph.settingsViewModel() }, onCleared = navigator::showWelcome)
        }
    }
}
