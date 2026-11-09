package org.sagisolas.recipehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.sagisolas.recipehost.domain.catalog.CatalogItem
import org.sagisolas.recipehost.domain.catalog.StackResolution
import org.sagisolas.recipehost.domain.recipes.DisplayStack
import org.sagisolas.recipehost.domain.recipes.RecipeDirection
import org.sagisolas.recipehost.domain.recipes.RecipeDisplay
import org.sagisolas.recipehost.domain.recipes.RecipeGroup
import org.sagisolas.recipehost.domain.recipes.RecipeVariant
import kotlin.time.Duration.Companion.milliseconds

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
 * One recipe type's section: the recipe at [index] of its [count], in its [selectedVariant].
 * [variants] lists each variant's Instance names, newest first, and is empty when the recipe has
 * only one body, so there is nothing to choose.
 */
internal data class RecipeSectionUi(
    val typeId: String,
    val index: Int,
    val count: Int,
    val recipeId: String,
    val variants: List<List<String>>,
    val selectedVariant: Int,
    val layout: RecipeLayoutUi,
)

/**
 * How one recipe is drawn, chosen per recipe: [Captured] when it has a stored `Recipe layout`
 * (ADR 0015), [Generic] otherwise.
 */
internal sealed interface RecipeLayoutUi {
    /** The input slots in a wrapped row, an arrow, then the results. A `null` input is an empty slot. */
    data class Generic(val inputs: List<StackUi?>, val results: List<StackUi>) : RecipeLayoutUi

    /**
     * The layout's image, loaded by [key], [width] by [height] GUI px, with the recipe's stacks
     * in its [slots].
     */
    data class Captured(val key: String, val width: Int, val height: Int, val slots: List<LayoutSlotUi>) : RecipeLayoutUi
}

/** A `Recipe slot`'s rectangle in GUI px, with the stack it shows now; `null` when it's empty. */
internal data class LayoutSlotUi(val x: Int, val y: Int, val width: Int, val height: Int, val stack: StackUi?)

/** An ItemStack as shown: [item] is `null` when it doesn't resolve, so its id shows instead. */
internal data class StackUi(val itemId: String, val count: Int, val item: CatalogItem?)

/** An Item's screen: its recipes and uses. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ItemViewModel(
    itemId: String,
    itemRecipes: (String, RecipeDirection) -> Flow<List<RecipeGroup>>,
    private val resolveStacks: (List<String>) -> Flow<Map<String, StackResolution>>,
) : ViewModel() {

    private val tab = MutableStateFlow(ItemTab.Recipes)

    /**
     * Where each section is, by tab and type id; a missing one is at its first recipe's newest
     * variant. Each tab keeps its own, so switching tabs and back finds them where they were.
     */
    private val positions = MutableStateFlow(emptyMap<SectionKey, Position>())

    /** The selected tab with its groups, so a tab switch never pairs one tab with the other's groups. */
    private val groups: Flow<Pair<ItemTab, List<RecipeGroup>>> = tab.flatMapLatest { tab ->
        val direction = when (tab) {
            ItemTab.Recipes -> RecipeDirection.Recipes
            ItemTab.Uses -> RecipeDirection.Uses
        }
        itemRecipes(itemId, direction).map { tab to it }
    }

    private val placed: Flow<Pair<ItemTab, List<PlacedSection>>> = combine(groups, positions) { (tab, groups), positions ->
        tab to groups.map { placedSection(it, positions[SectionKey(tab, it.typeId)] ?: Position()) }
    }

    /**
     * The placed sections with their stacks' Items. Both change together: the screen keeps the
     * previous recipe until the new one's stacks resolve, so they never flash as unresolved.
     */
    private val resolved: Flow<Resolved> = placed.flatMapLatest { (tab, sections) ->
        resolve(sections.flatMap { it.variant.display.itemIds }.distinct()).map { Resolved(tab, sections, it) }
    }

    /**
     * The screen's one clock: every Slot with alternatives shows alternative `tick % count`, so
     * they all turn together. It runs only while the screen collects [state].
     */
    private val ticks: Flow<Int> = flow {
        var tick = 0
        while (true) {
            emit(tick++)
            delay(ALTERNATIVE_SHOW_TIME)
        }
    }

    private val header: Flow<StackUi> = resolveStacks(listOf(itemId)).map { StackUi(itemId, count = 1, item = it[itemId]?.let(::itemOf)) }

    val state: StateFlow<ItemUiState> = combine(header, resolved, ticks) { header, resolved, tick ->
        ItemUiState(header, resolved.tab, resolved.sections.map { sectionOf(it, resolved.items, tick) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ItemUiState(null))

    fun selectTab(tab: ItemTab) {
        this.tab.value = tab
    }

    /** Pages [typeId]'s section forward, stopping at its last recipe; it shows its newest variant. */
    fun next(typeId: String) = page(typeId, +1)

    /** Pages [typeId]'s section back, stopping at its first recipe; it shows its newest variant. */
    fun previous(typeId: String) = page(typeId, -1)

    /** Shows variant [index] of the recipe [typeId]'s section is at. */
    fun selectVariant(typeId: String, index: Int) {
        val key = SectionKey(tab.value, typeId)
        positions.update { it + (key to (it[key] ?: Position()).copy(variant = index)) }
    }

    private fun page(typeId: String, by: Int) {
        val section = state.value.sections.firstOrNull { it.typeId == typeId } ?: return
        val key = SectionKey(tab.value, typeId)
        positions.update { it + (key to Position(page = (section.index + by).coerceIn(0, section.count - 1))) }
    }

    /** Each of [ids] with its Item, or `null` when it doesn't resolve. */
    private fun resolve(ids: List<String>): Flow<Map<String, CatalogItem?>> =
        if (ids.isEmpty()) {
            flowOf(emptyMap())
        } else {
            resolveStacks(ids).map { resolutions -> ids.associateWith { id -> resolutions[id]?.let(::itemOf) } }
        }
}

/** A section of one tab. */
private data class SectionKey(val tab: ItemTab, val typeId: String)

/** A section's pager [page] and the [variant] shown of the recipe there. */
private data class Position(val page: Int = 0, val variant: Int = 0)

/** The selected tab's sections at their positions, with the Items of their stacks. */
private class Resolved(val tab: ItemTab, val sections: List<PlacedSection>, val items: Map<String, CatalogItem?>)

/** A group at its pager's recipe and that recipe's chosen variant, before its stacks resolve. */
private class PlacedSection(val group: RecipeGroup, val index: Int, val variantIndex: Int) {
    val recipe get() = group.recipes[index]
    val variant: RecipeVariant get() = recipe.variants[variantIndex]
}

/** [group] at [position], kept inside the group and the recipe if they shrank since. */
private fun placedSection(group: RecipeGroup, position: Position): PlacedSection {
    val index = position.page.coerceIn(0, group.recipes.lastIndex)
    return PlacedSection(group, index, position.variant.coerceIn(0, group.recipes[index].variants.lastIndex))
}

private fun sectionOf(placed: PlacedSection, items: Map<String, CatalogItem?>, tick: Int) = RecipeSectionUi(
    typeId = placed.group.typeId,
    index = placed.index,
    count = placed.group.recipes.size,
    recipeId = placed.recipe.recipeId,
    variants = placed.recipe.variants.takeIf { it.size > 1 }?.map { it.instanceNames } ?: emptyList(),
    selectedVariant = placed.variantIndex,
    layout = layoutOf(placed.variant.display, items, tick),
)

/** [display] at [tick]: each slot shows its alternative `tick % count`. */
private fun layoutOf(display: RecipeDisplay, items: Map<String, CatalogItem?>, tick: Int): RecipeLayoutUi = when (display) {
    is RecipeDisplay.Generic -> RecipeLayoutUi.Generic(
        inputs = display.inputs.map { stacks -> stacks.alternativeAt(tick)?.let { stackOf(it, items) } },
        results = display.results.map { stackOf(it, items) },
    )
    is RecipeDisplay.Captured -> RecipeLayoutUi.Captured(
        key = display.key,
        width = display.width,
        height = display.height,
        slots = display.slots.map { slot ->
            LayoutSlotUi(slot.x, slot.y, slot.width, slot.height, slot.stacks.alternativeAt(tick)?.let { stackOf(it, items) })
        },
    )
}

private fun stackOf(stack: DisplayStack, items: Map<String, CatalogItem?>) =
    StackUi(stack.itemId, count = stack.count, item = items[stack.itemId])

/** The alternative shown at [tick], or `null` for an empty slot. */
private fun List<DisplayStack>.alternativeAt(tick: Int): DisplayStack? = if (isEmpty()) null else this[tick % size]

private fun itemOf(resolution: StackResolution): CatalogItem? = (resolution as? StackResolution.Resolved)?.item

/** How long each alternative of a Slot shows. */
private val ALTERNATIVE_SHOW_TIME = 1_000.milliseconds

/** Keeps the state shared across a configuration change, which briefly stops the collection. */
private const val STOP_TIMEOUT_MILLIS = 5_000L
