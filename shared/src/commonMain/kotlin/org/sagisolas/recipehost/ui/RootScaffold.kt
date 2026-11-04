package org.sagisolas.recipehost.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.presentation.navigation.NavState
import org.sagisolas.recipehost.presentation.navigation.Screen
import org.sagisolas.recipehost.presentation.navigation.Tab
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.tab_items
import org.sagisolas.recipehost.shared.generated.resources.tab_settings
import org.sagisolas.recipehost.ui.designsystem.McBottomBar
import org.sagisolas.recipehost.ui.designsystem.McBottomBarItem
import org.sagisolas.recipehost.ui.designsystem.McTheme

/** The bottom-navigation root: the selected tab's current screen above the [McBottomBar]. */
@Composable
internal fun RootScaffold(
    state: NavState,
    onSelectTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    screen: @Composable (Screen) -> Unit,
) {
    val items = Tab.entries.map { barItem(it) }
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            screen(state.currentScreen)
        }
        McBottomBar(
            items = items,
            selectedIndex = state.selectedTab.ordinal,
            onSelect = { onSelectTab(Tab.entries[it]) },
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
