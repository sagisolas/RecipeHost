package org.sagisolas.recipehost.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.presentation.navigation.Navigator
import org.sagisolas.recipehost.presentation.navigation.Screen
import org.sagisolas.recipehost.presentation.navigation.StackEntry
import org.sagisolas.recipehost.presentation.navigation.Tab
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.tab_items
import org.sagisolas.recipehost.shared.generated.resources.tab_settings
import org.sagisolas.recipehost.ui.designsystem.McBottomBar
import org.sagisolas.recipehost.ui.designsystem.McBottomBarItem
import org.sagisolas.recipehost.ui.designsystem.McTheme

/**
 * The bottom-navigation root: a `NavDisplay` of the tabs in use above the [McBottomBar], which
 * stays visible on pushed screens. Back is handled by `NavDisplay`, which calls [Navigator.pop].
 */
@Composable
internal fun RootScaffold(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    screen: @Composable (Screen) -> Unit,
) {
    // Every tab keeps its decorated entries, so a hidden tab keeps its saved state and ViewModels.
    val entries = Tab.entries.associateWith { tab ->
        rememberDecoratedNavEntries(
            backStack = navigator.stacks.getValue(tab),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            // The default content key is the key's toString(), which includes StackEntry's unique id.
            entryProvider = { key -> NavEntry(key) { screen((it as StackEntry).screen) } },
        )
    }
    val items = Tab.entries.map { barItem(it) }
    Column(modifier.fillMaxSize()) {
        NavDisplay(
            entries = navigator.tabsInUse.flatMap { entries.getValue(it) },
            onBack = navigator::pop,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        McBottomBar(
            items = items,
            selectedIndex = navigator.selectedTab.ordinal,
            onSelect = { navigator.selectTab(Tab.entries[it]) },
        )
    }
}

/** A tab's entry in the bar. */
@Composable
private fun barItem(tab: Tab): McBottomBarItem {
    // TODO(issue 11): real tab icons; the missing texture stands in until then.
    val missingTexture = McTheme.skin.missingTexture
    val icon = remember(missingTexture) { BitmapPainter(missingTexture, filterQuality = FilterQuality.None) }
    val label = when (tab) {
        Tab.Items -> stringResource(Res.string.tab_items)
        Tab.Settings -> stringResource(Res.string.tab_settings)
    }
    return McBottomBarItem(label, icon)
}
