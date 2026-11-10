package org.sagisolas.recipehost.domain.recipes

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.ByteString.Companion.toByteString
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.catalog.MINECRAFT
import org.sagisolas.recipehost.data.catalog.namespaceOf
import org.sagisolas.recipehost.domain.CATALOG_REFRESH_PERIOD
import org.sagisolas.recipehost.domain.throttleLatest
import org.sagisolas.recipehost.protocol.ItemStack
import org.sagisolas.recipehost.protocol.Recipe
import org.sagisolas.recipehost.protocol.RecipeLayout
import org.sagisolas.recipehost.protocol.RecipeSlot
import kotlin.coroutines.CoroutineContext

/** Which recipes of an Item: those that make it, or those that use it. */
internal enum class RecipeDirection {
    /** The recipes with the Item among their results. */
    Recipes,

    /** The recipes with the Item among the alternatives of an input `Slot`. */
    Uses,
}

/**
 * An Item's recipes or uses among those owned by the `Version selection` (ADR 0011), grouped by
 * type, each with its `Instance` variants decoded into how they are drawn. The flow re-emits when
 * the `Catalog` changes, at most once per [CATALOG_REFRESH_PERIOD].
 *
 * [context] runs the queries; `AppGraph` passes an I/O dispatcher.
 */
internal class ItemRecipes(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    operator fun invoke(
        itemId: String,
        direction: RecipeDirection,
    ): Flow<List<RecipeGroup>> {
        val query =
            when (direction) {
                RecipeDirection.Recipes -> catalog.recipeQueries.selectedProducing(itemId, ::InstanceRecipe)
                RecipeDirection.Uses -> catalog.recipeQueries.selectedConsuming(itemId, ::InstanceRecipe)
            }
        return query
            .asFlow()
            .throttleLatest(CATALOG_REFRESH_PERIOD)
            .mapToList(context)
            .map { it.toGroups() }
    }
}

/**
 * A stored `Recipe layout` without its image: [width] and [height] in GUI px, and the `Recipe slot`s
 * the recipe's `layout_stacks` fill by index. The image is loaded by [key].
 */
private data class StoredLayout(
    val key: String,
    val width: Int,
    val height: Int,
    val slots: List<RecipeSlot>,
)

/** A recipe as one `Instance` has it: the row shape both recipe queries map to. */
private class InstanceRecipe(
    val releaseId: Long,
    val recipeId: String,
    val typeId: String,
    val body: ByteArray,
    val bodyHash: ByteArray,
    val instanceName: String,
    layoutKey: String?,
    layoutWidth: Long?,
    layoutHeight: Long?,
    layoutSlots: ByteArray?,
    val typeName: String?,
) {
    /**
     * Null unless the recipe's layout is stored: the query's left join gives all four or none. Its
     * slots are decoded only when the variant is shown.
     */
    val layout: (() -> StoredLayout)? =
        if (layoutKey != null && layoutWidth != null && layoutHeight != null &&
            layoutSlots != null
        ) {
            {
                StoredLayout(
                    layoutKey,
                    layoutWidth.toInt(),
                    layoutHeight.toInt(),
                    RecipeLayout.ADAPTER.decode(layoutSlots).slots,
                )
            }
        } else {
            null
        }
}

/**
 * Groups rows by type, then collapses each `(release, recipe id)` by body hash into variants.
 * `minecraft:` types come first, then the others; each part keeps the type order of the rows.
 * Expects the rows ordered by type, recipe id and release, newest Instance first within a recipe,
 * as the recipe queries return them.
 */
private fun List<InstanceRecipe>.toGroups(): List<RecipeGroup> =
    groupBy { it.typeId }
        .map { (typeId, rows) ->
            RecipeGroup(
                typeId = typeId,
                name = rows.first().typeName,
                recipes =
                    rows.groupBy { it.releaseId to it.recipeId }.map { (key, instances) ->
                        CatalogRecipe(releaseId = key.first, recipeId = key.second, variants = instances.toVariants())
                    },
            )
        }.sortedBy { namespaceOf(it.typeId) != MINECRAFT } // stable: false (the game's own) sorts first

/** Rows of one recipe, newest Instance first, collapsed by body hash; groupBy keeps first-seen order. */
private fun List<InstanceRecipe>.toVariants(): List<RecipeVariant> =
    groupBy { it.bodyHash.toByteString() }.values.map { sameBody ->
        RecipeVariant(instanceNames = sameBody.map { it.instanceName }) {
            recipeDisplayOf(
                Recipe.ADAPTER.decode(sameBody.first().body),
                sameBody.firstNotNullOfOrNull { it.layout }?.invoke(),
            )
        }
    }

/**
 * [recipe] as drawn: in [layout] when it has one, whose slots the recipe's `layout_stacks` fill by
 * index (a slot past them stays empty), and generically otherwise.
 */
private fun recipeDisplayOf(
    recipe: Recipe,
    layout: StoredLayout?,
): RecipeDisplay =
    if (layout == null) {
        RecipeDisplay.Generic(
            inputs = recipe.inputs.map { slot -> slot.stacks.map(::displayStackOf) },
            results = recipe.results.map(::displayStackOf),
        )
    } else {
        RecipeDisplay.Captured(
            key = layout.key,
            width = layout.width,
            height = layout.height,
            slots =
                layout.slots.mapIndexed { index, slot ->
                    displaySlotOf(
                        slot,
                        recipe.layout_stacks
                            .getOrNull(index)
                            ?.stacks
                            .orEmpty(),
                    )
                },
        )
    }

private fun displaySlotOf(
    slot: RecipeSlot,
    stacks: List<ItemStack>,
) = DisplaySlot(slot.x, slot.y, slot.width, slot.height, stacks.map(::displayStackOf))

/** A wire count of 0 means 1. */
private fun displayStackOf(stack: ItemStack) = DisplayStack(stack.item_id, stack.count.coerceAtLeast(1))
