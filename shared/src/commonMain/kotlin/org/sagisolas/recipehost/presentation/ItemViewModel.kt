/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sagisolas.recipehost.domain.catalog.CatalogItem
import org.sagisolas.recipehost.domain.catalog.StackResolution
import org.sagisolas.recipehost.domain.recipes.RecipeDirection
import org.sagisolas.recipehost.domain.recipes.RecipeGroup
import org.sagisolas.recipehost.domain.recipes.RecipeVariant

/** What the Item screen shows: the Item in its [header], and the [tab]'s sections. */
internal data class ItemUiState(
    /** `null` until the tab's recipes first arrive **/
    val header: StackUi?,
    val tab: ItemTab = ItemTab.Recipes,
    val sections: List<RecipeSectionUi> = emptyList(),
)

/** The Item screen's tabs, in tab order. */
internal enum class ItemTab { Recipes, Uses }

/**
 * One recipe type's section, headed by [title]: the type's name, or its [typeId] when the `Catalog`
 * has no name for it. It shows the recipe at [index] of its [count], in its [selectedVariant];
 * [bookmarked] says whether that recipe has a `Bookmark`.
 * [variants] lists each variant's Instance names, newest first, and is empty when the recipe has
 * only one body, so there is nothing to choose.
 */
internal data class RecipeSectionUi(
    val typeId: String,
    val title: String,
    val index: Int,
    val count: Int,
    val recipeId: String,
    val variants: List<List<String>>,
    val selectedVariant: Int,
    val layout: RecipeLayoutUi,
    val bookmarked: Boolean = false,
)

/** An Item's screen: its recipes and uses. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ItemViewModel(
    itemId: String,
    itemRecipes: (String, RecipeDirection) -> Flow<List<RecipeGroup>>,
    private val resolveStacks: (List<String>) -> Flow<Map<String, StackResolution>>,
    bookmarkedIds: Flow<Set<String>>,
    private val toggleRecipeBookmark: suspend (String) -> Unit,
) : ViewModel() {
    private val tab = MutableStateFlow(ItemTab.Recipes)

    /**
     * Where each section is, by tab and type id; a missing one is at its first recipe's newest
     * variant. Each tab keeps its own, so switching tabs and back finds them where they were.
     */
    private val positions = MutableStateFlow(emptyMap<SectionKey, Position>())

    /** The selected tab with its groups, so a tab switch never pairs one tab with the other's groups. */
    private val groups: Flow<Pair<ItemTab, List<RecipeGroup>>> =
        tab.flatMapLatest { tab ->
            val direction =
                when (tab) {
                    ItemTab.Recipes -> RecipeDirection.Recipes
                    ItemTab.Uses -> RecipeDirection.Uses
                }
            itemRecipes(itemId, direction).map { tab to it }
        }

    private val placed: Flow<Pair<ItemTab, List<PlacedSection>>> =
        combine(groups, positions) { (tab, groups), positions ->
            tab to groups.map { placedSection(it, positions[SectionKey(tab, it.typeId)] ?: Position()) }
        }

    /**
     * The placed sections with their stacks' Items. Both change together: the screen keeps the
     * previous recipe until the new one's stacks resolve, so they never flash as unresolved.
     */
    private val resolved: Flow<Resolved> =
        placed.flatMapLatest { (tab, sections) ->
            resolveItems(sections.flatMap { it.variant.display.itemIds }.distinct(), resolveStacks).map {
                Resolved(tab, sections, it)
            }
        }

    /** The screen's one clock; it runs only while the screen collects [state]. */
    private val ticks: Flow<Int> = alternativeTicks()

    private val header: Flow<StackUi> =
        resolveStacks(listOf(itemId)).map {
            StackUi(itemId, count = 1, item = it[itemId]?.let(::itemOf))
        }

    val state: StateFlow<ItemUiState> =
        combine(header, resolved, bookmarkedIds, ticks) { header, resolved, bookmarked, tick ->
            ItemUiState(
                header,
                resolved.tab,
                resolved.sections.map { sectionOf(it, resolved.items, tick, it.recipe.recipeId in bookmarked) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ItemUiState(null))

    fun selectTab(tab: ItemTab) {
        this.tab.value = tab
    }

    /** Pages [typeId]'s section forward, stopping at its last recipe; it shows its newest variant. */
    fun next(typeId: String) = page(typeId, +1)

    /** Pages [typeId]'s section back, stopping at its first recipe; it shows its newest variant. */
    fun previous(typeId: String) = page(typeId, -1)

    /** Shows variant [index] of the recipe [typeId]'s section is at. */
    fun selectVariant(
        typeId: String,
        index: Int,
    ) {
        val key = SectionKey(tab.value, typeId)
        positions.update { it + (key to (it[key] ?: Position()).copy(variant = index)) }
    }

    /** Bookmarks the recipe [typeId]'s section shows, or removes its bookmark if it has one. */
    fun toggleBookmark(typeId: String) {
        val section = state.value.sections.firstOrNull { it.typeId == typeId } ?: return
        viewModelScope.launch { toggleRecipeBookmark(section.recipeId) }
    }

    private fun page(
        typeId: String,
        by: Int,
    ) {
        val section = state.value.sections.firstOrNull { it.typeId == typeId } ?: return
        val key = SectionKey(tab.value, typeId)
        positions.update { it + (key to Position(page = (section.index + by).coerceIn(0, section.count - 1))) }
    }
}

/** A section of one tab. */
private data class SectionKey(
    val tab: ItemTab,
    val typeId: String,
)

/** A section's pager [page] and the [variant] shown of the recipe there. */
private data class Position(
    val page: Int = 0,
    val variant: Int = 0,
)

/** The selected tab's sections at their positions, with the Items of their stacks. */
private class Resolved(
    val tab: ItemTab,
    val sections: List<PlacedSection>,
    val items: Map<String, CatalogItem?>,
)

/** A group at its pager's recipe and that recipe's chosen variant, before its stacks resolve. */
private class PlacedSection(
    val group: RecipeGroup,
    val index: Int,
    val variantIndex: Int,
) {
    val recipe get() = group.recipes[index]
    val variant: RecipeVariant get() = recipe.variants[variantIndex]
}

/** [group] at [position], kept inside the group and the recipe if they shrank since. */
private fun placedSection(
    group: RecipeGroup,
    position: Position,
): PlacedSection {
    val index = position.page.coerceIn(0, group.recipes.lastIndex)
    return PlacedSection(group, index, position.variant.coerceIn(0, group.recipes[index].variants.lastIndex))
}

private fun sectionOf(
    placed: PlacedSection,
    items: Map<String, CatalogItem?>,
    tick: Int,
    bookmarked: Boolean,
) = RecipeSectionUi(
    typeId = placed.group.typeId,
    title = placed.group.name ?: placed.group.typeId,
    index = placed.index,
    count = placed.group.recipes.size,
    recipeId = placed.recipe.recipeId,
    variants = variantChoicesOf(placed.recipe.variants),
    selectedVariant = placed.variantIndex,
    layout = layoutOf(placed.variant.display, items, tick),
    bookmarked = bookmarked,
)
