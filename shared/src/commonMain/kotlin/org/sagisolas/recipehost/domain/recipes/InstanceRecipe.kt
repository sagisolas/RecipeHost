/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.domain.recipes

import okio.ByteString.Companion.toByteString
import org.sagisolas.recipehost.protocol.ItemStack
import org.sagisolas.recipehost.protocol.Recipe
import org.sagisolas.recipehost.protocol.RecipeLayout
import org.sagisolas.recipehost.protocol.RecipeSlot

/**
 * A stored `Recipe layout` without its image: [width] and [height] in GUI px, and the `Recipe slot`s
 * the recipe's `layout_stacks` fill by index. The image is loaded by [key].
 */
internal data class StoredLayout(
    val key: String,
    val width: Int,
    val height: Int,
    val slots: List<RecipeSlot>,
)

/** A recipe as one `Instance` has it: the row shape the recipe and bookmark queries map to. */
internal class InstanceRecipe(
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

/** Rows of one recipe, newest Instance first, collapsed by body hash; groupBy keeps first-seen order. */
internal fun List<InstanceRecipe>.toVariants(): List<RecipeVariant> =
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
            results = recipe.results.map(::displayStackOf),
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
