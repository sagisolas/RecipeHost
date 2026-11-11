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
import org.sagisolas.recipehost.domain.recipes.BookmarkedRecipe
import org.sagisolas.recipehost.domain.recipes.RecipeVariant

/** What the Bookmarks tab shows: its [bookmarks], newest first; `null` until they first arrive. */
internal data class BookmarksUiState(
    val bookmarks: List<BookmarkUi>? = null,
)

/**
 * One bookmarked recipe, in its [selectedVariant]. It is headed by its first [result] (shown without
 * its count), titled with that result's name and subtitled with the recipe type; a recipe with no
 * results has no [result] and no [subtitle], and is titled with its type. [variants] is as in
 * [RecipeSectionUi].
 */
internal data class BookmarkUi(
    val recipeId: String,
    val result: StackUi?,
    val title: String,
    val subtitle: String?,
    val variants: List<List<String>>,
    val selectedVariant: Int,
    val layout: RecipeLayoutUi,
)

/** The Bookmarks tab: every bookmarked recipe, each with its own variant choice. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BookmarksViewModel(
    bookmarkedRecipes: Flow<List<BookmarkedRecipe>>,
    private val resolveStacks: (List<String>) -> Flow<Map<String, StackResolution>>,
    private val removeBookmark: suspend (String) -> Unit,
) : ViewModel() {
    /** The variant chosen per recipe id; a missing one shows its newest variant. */
    private val chosenVariants = MutableStateFlow(emptyMap<String, Int>())

    private val placed: Flow<List<PlacedBookmark>> =
        combine(bookmarkedRecipes, chosenVariants) { recipes, chosen ->
            recipes.map { placedBookmark(it, chosen[it.recipeId] ?: 0) }
        }

    /** The placed bookmarks with their stacks' Items, which change together, as on the Item screen. */
    private val resolved: Flow<Pair<List<PlacedBookmark>, Map<String, CatalogItem?>>> =
        placed.flatMapLatest { bookmarks ->
            val ids = bookmarks.flatMap { it.variant.display.itemIds + it.resultIds }.distinct()
            resolveItems(ids, resolveStacks).map { bookmarks to it }
        }

    val state: StateFlow<BookmarksUiState> =
        combine(resolved, alternativeTicks()) { (bookmarks, items), tick ->
            BookmarksUiState(bookmarks.map { bookmarkOf(it, items, tick) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BookmarksUiState())

    /** Shows variant [index] of bookmarked recipe [recipeId]. */
    fun selectVariant(
        recipeId: String,
        index: Int,
    ) {
        chosenVariants.update { it + (recipeId to index) }
    }

    fun remove(recipeId: String) {
        viewModelScope.launch { removeBookmark(recipeId) }
    }
}

/** A bookmarked recipe at its chosen variant, before its stacks resolve. */
private class PlacedBookmark(
    val recipe: BookmarkedRecipe,
    val variantIndex: Int,
) {
    val variant: RecipeVariant get() = recipe.variants[variantIndex]

    /** The header's result: the variant's first. */
    val resultIds: List<String> get() =
        listOfNotNull(
            variant.display.results
                .firstOrNull()
                ?.itemId,
        )
}

/** [recipe] at variant [index], kept inside the recipe if it lost variants since. */
private fun placedBookmark(
    recipe: BookmarkedRecipe,
    index: Int,
) = PlacedBookmark(recipe, index.coerceIn(0, recipe.variants.lastIndex))

private fun bookmarkOf(
    placed: PlacedBookmark,
    items: Map<String, CatalogItem?>,
    tick: Int,
): BookmarkUi {
    val type = placed.recipe.typeName ?: placed.recipe.typeId
    val result =
        placed.variant.display.results
            .firstOrNull()
            ?.let { StackUi(it.itemId, count = 1, item = items[it.itemId]) }
    return BookmarkUi(
        recipeId = placed.recipe.recipeId,
        result = result,
        title = result?.let { it.item?.displayName ?: it.itemId } ?: type,
        subtitle = type.takeIf { result != null },
        variants = variantChoicesOf(placed.recipe.variants),
        selectedVariant = placed.variantIndex,
        layout = layoutOf(placed.variant.display, items, tick),
    )
}
