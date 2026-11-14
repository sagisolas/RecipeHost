/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.domain.recipes

import app.cash.sqldelight.Query
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.catalog.RecipeStore
import org.sagisolas.recipehost.protocol.ItemStack
import org.sagisolas.recipehost.protocol.LayoutPatch
import org.sagisolas.recipehost.protocol.Recipe
import org.sagisolas.recipehost.protocol.RecipeLayout
import org.sagisolas.recipehost.protocol.RecipeSlot

/**
 * A stored `Recipe layout` without its image: [width] and [height] in GUI px, and the `Recipe slot`s
 * the recipe's `layout_stacks` fill by index. The image is loaded by [key], with [patch] drawn over it.
 */
internal data class StoredLayout(
    val key: String,
    val width: Int,
    val height: Int,
    val slots: List<RecipeSlot>,
    val patch: DisplayPatch?,
)

/** A recipe as one `Instance` has it: the row shape the recipe and bookmark queries map to. */
internal class InstanceRecipe(
    val releaseId: Long,
    val recipeId: String,
    val typeId: String,
    val body: ByteArray,
    val instanceName: String,
    layoutKey: String?,
    layoutWidth: Long?,
    layoutHeight: Long?,
    layoutSlots: ByteArray?,
    patchKey: String?,
    patchTiles: ByteArray?,
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
                    patchKey?.let { key ->
                        DisplayPatch(key, patchTiles?.let { LayoutPatch.ADAPTER.decode(it).tiles }.orEmpty())
                    },
                )
            }
        } else {
            null
        }
}

/** A recipe query's rows, with the wire Recipe of each distinct body among them. */
internal class RecipeRows(
    val rows: List<InstanceRecipe>,
    val recipes: Map<ByteString, Recipe>,
)

/**
 * Runs the query and reads the Slots and item ids its bodies name in one transaction, so a Scan
 * that ends in between can't delete them as unreferenced (ADR 0018).
 */
internal fun Query<InstanceRecipe>.readWithRecipes(
    catalog: CatalogDatabase,
    recipeStore: RecipeStore,
): RecipeRows =
    catalog.transactionWithResult {
        val rows = executeAsList()
        val bodies = rows.map { it.body.toByteString() }.distinct()
        RecipeRows(rows, bodies.zip(recipeStore.recipesOf(bodies.map { it.toByteArray() })).toMap())
    }

/**
 * Rows of one recipe, newest Instance first, collapsed by body: equal bodies are equal recipes
 * (ADR 0018). groupBy keeps first-seen order. [recipes] holds each body's wire Recipe.
 */
internal fun List<InstanceRecipe>.toVariants(recipes: Map<ByteString, Recipe>): List<RecipeVariant> =
    groupBy { it.body.toByteString() }.map { (body, sameBody) ->
        RecipeVariant(instanceNames = sameBody.map { it.instanceName }) {
            recipeDisplayOf(recipes.getValue(body), sameBody.firstNotNullOfOrNull { it.layout }?.invoke())
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
            results = recipe.results.map(::displayStackOf),
            patch = layout.patch,
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
