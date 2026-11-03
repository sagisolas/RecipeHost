package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/** A row of equal-width tabs; the selected one uses the panel-coloured tab background. */
@Composable
internal fun McTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = McTheme.skin
    Row(modifier.fillMaxWidth().height(McDimens.TabHeight)) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            McPressable(
                onClick = { onSelect(index) },
                painters = if (selected) McStatePainters.all(skin.tabSelected) else skin.tab,
                modifier = Modifier.weight(1f).fillMaxHeight().semantics { this.selected = selected },
                role = Role.Tab,
            ) {
                McText(title, maxLines = 1)
            }
        }
    }
}
