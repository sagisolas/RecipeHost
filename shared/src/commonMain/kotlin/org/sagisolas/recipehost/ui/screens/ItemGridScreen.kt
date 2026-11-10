package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.domain.catalog.CatalogItem
import org.sagisolas.recipehost.presentation.ItemGridViewModel
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.grid_mods
import org.sagisolas.recipehost.shared.generated.resources.grid_scan
import org.sagisolas.recipehost.shared.generated.resources.grid_search_hint
import org.sagisolas.recipehost.ui.designsystem.McButton
import org.sagisolas.recipehost.ui.designsystem.McDimens
import org.sagisolas.recipehost.ui.designsystem.McPanel
import org.sagisolas.recipehost.ui.designsystem.McProgressBar
import org.sagisolas.recipehost.ui.designsystem.McSlotGrid
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTextField
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.designsystem.mcTiledBackground
import org.sagisolas.recipehost.ui.item.IconAtlasCache
import org.sagisolas.recipehost.ui.item.ItemIcon
import org.sagisolas.recipehost.ui.navigation.Navigator
import org.sagisolas.recipehost.ui.navigation.Screen
import org.sagisolas.recipehost.ui.tabScreenInsetsPadding

/**
 * The Items tab's first screen: Mods, search and Scan on top, a thin bar while Icons arrive, and
 * the Items as bare Icons, as many slot-sized cells per row as fit beside a scrollbar. All of it
 * sits on the tiled dirt list background.
 */
@Composable
internal fun ItemGridScreen(
    viewModel: ItemGridViewModel,
    navigator: Navigator,
    atlases: IconAtlasCache,
    modifier: Modifier = Modifier,
) {
    // Main.immediate: the field's value must follow each edit within the same frame, or a fast
    // typist's next keystroke edits a stale value.
    val state by viewModel.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    // The dirt covers the whole screen, behind the top bar and the status bar too.
    Column(modifier.fillMaxSize().mcTiledBackground(McTheme.skin.listBackground).tabScreenInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            McButton(stringResource(Res.string.grid_mods), onClick = { navigator.push(Screen.ModList) })
            McTextField(
                state.searchText,
                viewModel::onSearchTextChange,
                Modifier.weight(1f),
                hint = stringResource(Res.string.grid_search_hint),
            )
            McButton(stringResource(Res.string.grid_scan), onClick = viewModel::scan)
        }
        state.iconProgress?.let { McProgressBar(it, Modifier.padding(horizontal = 8.dp)) }
        McSlotGrid(Modifier.weight(1f).fillMaxWidth()) {
            items(state.items, key = { it.key }) { item ->
                ItemCell(item, atlases, onClick = { navigator.push(Screen.Item(item.itemId)) })
            }
        }
    }
}

/** One Item: tap opens it, a long press shows its id until dismissed. */
@Composable
private fun ItemCell(
    item: CatalogItem,
    atlases: IconAtlasCache,
    onClick: () -> Unit,
) {
    var showId by remember { mutableStateOf(false) }
    Box(
        Modifier.size(McDimens.SlotSize).combinedClickable(onLongClick = { showId = true }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ItemIcon(item.icon, atlases)
        if (showId) {
            val below = with(LocalDensity.current) { McDimens.SlotSize.roundToPx() }
            Popup(offset = IntOffset(0, below), onDismissRequest = { showId = false }) {
                McPanel { McText(item.itemId, maxLines = 1) }
            }
        }
    }
}

/** The same item id can be in several releases, each with its own Icon. */
private val CatalogItem.key get() = "$releaseId/$itemId"
