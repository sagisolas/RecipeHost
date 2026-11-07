package org.sagisolas.recipehost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.sagisolas.recipehost.presentation.ItemTab
import org.sagisolas.recipehost.presentation.ItemViewModel
import org.sagisolas.recipehost.presentation.RecipeSectionUi
import org.sagisolas.recipehost.presentation.StackUi
import org.sagisolas.recipehost.shared.generated.resources.Res
import org.sagisolas.recipehost.shared.generated.resources.item_from_instances
import org.sagisolas.recipehost.shared.generated.resources.item_no_recipes
import org.sagisolas.recipehost.shared.generated.resources.item_tab_recipes
import org.sagisolas.recipehost.shared.generated.resources.item_tab_uses
import org.sagisolas.recipehost.ui.designsystem.McDropdown
import org.sagisolas.recipehost.ui.designsystem.McPager
import org.sagisolas.recipehost.ui.designsystem.McSlot
import org.sagisolas.recipehost.ui.designsystem.McTabs
import org.sagisolas.recipehost.ui.designsystem.McText
import org.sagisolas.recipehost.ui.designsystem.McTheme
import org.sagisolas.recipehost.ui.item.IconAtlasCache
import org.sagisolas.recipehost.ui.item.LayoutImageCache
import org.sagisolas.recipehost.ui.item.StackIcon
import org.sagisolas.recipehost.ui.navigation.Navigator
import org.sagisolas.recipehost.ui.navigation.Screen
import org.sagisolas.recipehost.ui.recipe.RecipeLayout
import org.sagisolas.recipehost.ui.tabScreenInsetsPadding

/**
 * An Item, pushed onto the current tab's stack: its Icon, name and id on top, then its Recipes
 * and Uses tabs, each a list of one section per recipe type. Tapping a stack opens its Item.
 */
@Composable
internal fun ItemScreen(
    viewModel: ItemViewModel,
    navigator: Navigator,
    atlases: IconAtlasCache,
    layoutImages: LayoutImageCache,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open = { itemId: String -> navigator.push(Screen.Item(itemId)) }
    Column(modifier.fillMaxSize().tabScreenInsetsPadding()) {
        // Until the first state arrives the header's Item isn't resolved yet; it would flash as unknown.
        Header(state.header, atlases)
        McTabs(
            tabs = listOf(stringResource(Res.string.item_tab_recipes), stringResource(Res.string.item_tab_uses)),
            selectedIndex = state.tab.ordinal,
            onSelect = { viewModel.selectTab(ItemTab.entries[it]) },
        )
        when {
            state.header == null -> Unit
            state.sections.isEmpty() -> McText(
                stringResource(Res.string.item_no_recipes),
                Modifier.fillMaxWidth().padding(16.dp),
                color = McTheme.colors.textOnDark,
            )
            else -> LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.sections, key = { it.typeId }) { section ->
                    RecipeSection(
                        section,
                        atlases,
                        layoutImages,
                        onPrevious = { viewModel.previous(section.typeId) },
                        onNext = { viewModel.next(section.typeId) },
                        onSelectVariant = { viewModel.selectVariant(section.typeId, it) },
                        onOpen = open,
                    )
                }
            }
        }
    }
}

/** The Item's Icon, then its display name over its id. An unresolved Item shows only its id. */
@Composable
private fun Header(item: StackUi?, atlases: IconAtlasCache) {
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item != null) {
            McSlot { StackIcon(item, atlases) }
        }
        Column {
            McText(item?.item?.displayName ?: "", color = McTheme.colors.textOnDark, style = McTheme.typography.title, maxLines = 2)
            McText(item?.item?.itemId ?: "", color = McTheme.colors.textOnDark, style = McTheme.typography.small, maxLines = 1)
        }
    }
}

/**
 * One recipe type: its type id with the pager at the end, the "From <instance>" choice when the
 * recipe has several bodies, then the recipe.
 */
@Composable
private fun RecipeSection(
    section: RecipeSectionUi,
    atlases: IconAtlasCache,
    layoutImages: LayoutImageCache,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectVariant: (Int) -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            McText(
                section.typeId,
                Modifier.weight(1f),
                color = McTheme.colors.textOnDark,
                style = McTheme.typography.small,
                maxLines = 2,
            )
            McPager(section.index, section.count, onPrevious, onNext)
        }
        if (section.variants.isNotEmpty()) {
            // McDropdown's label isn't composable, so the labels are resolved here.
            val labels = section.variants.map { stringResource(Res.string.item_from_instances, it.joinToString(", ")) }
            McDropdown(
                options = section.variants.indices.toList(),
                selected = section.selectedVariant,
                onSelect = onSelectVariant,
                label = { labels[it] },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RecipeLayout(section.layout, atlases, layoutImages, onOpen)
    }
}
