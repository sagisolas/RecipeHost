package org.sagisolas.recipehost

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.presentation.navigation.Navigator
import org.sagisolas.recipehost.presentation.navigation.Route
import org.sagisolas.recipehost.presentation.navigation.Screen
import org.sagisolas.recipehost.presentation.navigation.Tab
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.placeholder_continue
import org.sagisolas.recipehost.shared.generated.resources.placeholder_grid
import org.sagisolas.recipehost.shared.generated.resources.placeholder_item
import org.sagisolas.recipehost.shared.generated.resources.placeholder_mod_list
import org.sagisolas.recipehost.shared.generated.resources.placeholder_open_item
import org.sagisolas.recipehost.shared.generated.resources.placeholder_open_mods
import org.sagisolas.recipehost.shared.generated.resources.placeholder_scan_progress
import org.sagisolas.recipehost.shared.generated.resources.placeholder_scan_qr
import org.sagisolas.recipehost.shared.generated.resources.placeholder_settings
import org.sagisolas.recipehost.shared.generated.resources.placeholder_welcome
import org.sagisolas.recipehost.ui.AppTheme
import org.sagisolas.recipehost.ui.PlaceholderAction
import org.sagisolas.recipehost.ui.PlaceholderScreen
import org.sagisolas.recipehost.ui.RootScaffold
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.mcBackground

/** The whole app UI, over the instances [graph] holds. */
@Composable
fun App(graph: AppGraph) {
    AppTheme {
        val navigator = graph.navigator
        val nav by navigator.state.collectAsState()
        // Disabled where Navigator doesn't handle back, so the system's back leaves the app.
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = nav.canGoBack,
            onBackCompleted = { navigator.back() },
        )

        Box(Modifier.fillMaxSize().mcBackground(McTheme.skin.screen).safeDrawingPadding()) {
            when (nav.route) {
                Route.Welcome -> WelcomePlaceholder(graph)
                Route.ScanProgress -> ScanProgressPlaceholder(graph)
                Route.Root -> RootScaffold(nav, onSelectTab = navigator::selectTab) { screen ->
                    ScreenPlaceholder(screen, navigator)
                }
            }
        }
    }
}

// TODO(issue 08): the Welcome screen.
@Composable
private fun WelcomePlaceholder(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    PlaceholderScreen(
        stringResource(Res.string.placeholder_welcome),
        actions = listOf(
            PlaceholderAction(stringResource(Res.string.placeholder_scan_qr)) {
                scope.launch {
                    try {
                        graph.pairing.scanQr()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // No scanner on this device (no Google Play services): the deep link still works.
                    }
                }
            },
        ),
    )
}

// TODO(issue 08): the Scan progress screen, which moves to the root by itself once committed.
@Composable
private fun ScanProgressPlaceholder(graph: AppGraph) {
    val progress by graph.scans.progress.collectAsState()
    val failure by graph.pairing.failure.collectAsState()
    PlaceholderScreen(
        stringResource(Res.string.placeholder_scan_progress, (failure ?: progress).toString()),
        actions = listOf(
            PlaceholderAction(stringResource(Res.string.placeholder_continue)) { graph.navigator.showRoot(Tab.Items) },
        ),
    )
}

// TODO(issues 08-10): the Items tab's grid and mod list, the Item screen and Settings.
@Composable
private fun ScreenPlaceholder(screen: Screen, navigator: Navigator) {
    val openStone = PlaceholderAction(stringResource(Res.string.placeholder_open_item, "minecraft:stone")) {
        navigator.push(Screen.Item("minecraft:stone"))
    }
    when (screen) {
        Screen.Grid -> PlaceholderScreen(
            stringResource(Res.string.placeholder_grid),
            actions = listOf(
                PlaceholderAction(stringResource(Res.string.placeholder_open_mods)) { navigator.push(Screen.ModList) },
                openStone,
            ),
        )
        Screen.ModList -> PlaceholderScreen(stringResource(Res.string.placeholder_mod_list))
        Screen.Settings -> PlaceholderScreen(stringResource(Res.string.placeholder_settings), actions = listOf(openStone))
        is Screen.Item -> PlaceholderScreen(
            stringResource(Res.string.placeholder_item, screen.itemId),
            actions = listOf(
                PlaceholderAction(stringResource(Res.string.placeholder_open_item, "minecraft:stick")) {
                    navigator.push(Screen.Item("minecraft:stick"))
                },
            ),
        )
    }
}
