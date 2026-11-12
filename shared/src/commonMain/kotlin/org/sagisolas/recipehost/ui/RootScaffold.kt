package org.sagisolas.recipehost.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.ic_bookmark
import org.sagisolas.recipehost.shared.generated.resources.ic_build
import org.sagisolas.recipehost.shared.generated.resources.ic_grid_view
import org.sagisolas.recipehost.shared.generated.resources.tab_bookmarks
import org.sagisolas.recipehost.shared.generated.resources.tab_items
import org.sagisolas.recipehost.shared.generated.resources.tab_settings
import org.sagisolas.recipehost.ui.designsystem.McBottomBar
import org.sagisolas.recipehost.ui.designsystem.McBottomBarItem
import org.sagisolas.recipehost.ui.navigation.Navigator
import org.sagisolas.recipehost.ui.navigation.Screen
import org.sagisolas.recipehost.ui.navigation.StackEntry
import org.sagisolas.recipehost.ui.navigation.Tab
import org.sagisolas.recipehost.ui.navigation.noTransition
import org.sagisolas.recipehost.ui.navigation.popTransition
import org.sagisolas.recipehost.ui.navigation.predictivePopTransition
import org.sagisolas.recipehost.ui.navigation.pushTransition

/**
 * The bottom-navigation root: a `NavDisplay` of the selected tab's stack above the [McBottomBar],
 * which stays visible on pushed screens. Back is handled by `NavDisplay`, which calls [Navigator.pop].
 */
@Composable
internal fun RootScaffold(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    screen: @Composable (Screen) -> Unit,
) {
    // Every tab keeps its decorated entries, so a hidden tab keeps its saved state and ViewModels.
    val entries =
        Tab.entries.associateWith { tab ->
            rememberDecoratedNavEntries(
                backStack = navigator.stacks.getValue(tab),
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                // The default content key is the key's toString(), which includes StackEntry's unique id.
                entryProvider = { key ->
                    val entry = key as StackEntry
                    NavEntry(key) { screen(entry.screen) }
                },
            )
        }
    val items = Tab.entries.map { barItem(it) }
    Column(modifier.fillMaxSize()) {
        NavDisplay(
            entries = entries.getValue(navigator.selectedTab),
            onBack = navigator::pop,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // A push slides the new screen in from the right; a pop slides the screen being left
            // off to the right, quickly for the back button and with the finger for the back
            // gesture. The push checks the stack depth: NavDisplay also consults transitionSpec
            // while it finishes a released back gesture, and must not run the push slide then.
            transitionSpec = {
                if (targetState.previousEntries.size > initialState.previousEntries.size) {
                    pushTransition()
                } else {
                    noTransition()
                }
            },
            popTransitionSpec = { popTransition() },
            predictivePopTransitionSpec = { predictivePopTransition() },
        )
        McBottomBar(
            items = items,
            selectedIndex = navigator.selectedTab.ordinal,
            onSelect = { navigator.selectTab(Tab.entries[it]) },
        )
    }
}

/**
 * Keeps a tab screen's content below the status bar and clear of display cutouts. Apply it after
 * the screen's background, so the background runs behind the status bar; the bottom bar takes the
 * navigation bar.
 */
@Composable
internal fun Modifier.tabScreenInsetsPadding(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))

/** A tab's entry in the bar. */
@Composable
private fun barItem(tab: Tab): McBottomBarItem =
    when (tab) {
        Tab.Items -> {
            McBottomBarItem(stringResource(Res.string.tab_items), painterResource(Res.drawable.ic_grid_view))
        }

        Tab.Bookmarks -> {
            McBottomBarItem(stringResource(Res.string.tab_bookmarks), painterResource(Res.drawable.ic_bookmark))
        }

        Tab.Settings -> {
            McBottomBarItem(stringResource(Res.string.tab_settings), painterResource(Res.drawable.ic_build))
        }
    }
