package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

@Immutable
internal data class McBottomBarItem(
    val label: String,
    val icon: Painter,
)

/**
 * The root tab bar: one item per tab, icon over label. Its background also fills [windowInsets]
 * (the system navigation bar by default), which the items stay clear of.
 */
@Composable
internal fun McBottomBar(
    items: List<McBottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars,
) {
    val skin = McTheme.skin
    Row(
        modifier
            .fillMaxWidth()
            .mcBackground(skin.bottomBar)
            .windowInsetsPadding(windowInsets)
            .height(McDimens.BottomBarHeight),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            McPressable(
                onClick = { onSelect(index) },
                textures = if (selected) McStateTextures.all(skin.bottomBarItemSelected) else skin.bottomBarItem,
                modifier = Modifier.weight(1f).fillMaxHeight().semantics { this.selected = selected },
                role = Role.Tab,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(item.icon, contentDescription = null, modifier = Modifier.size(McDimens.BottomBarIconSize))
                    McText(
                        text = item.label,
                        color = if (selected) McTheme.colors.textOnDark else McTheme.colors.textDisabled,
                        style = McTheme.typography.small,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
