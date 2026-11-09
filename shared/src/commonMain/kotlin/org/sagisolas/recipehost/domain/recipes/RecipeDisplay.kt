package org.sagisolas.recipehost.domain.recipes

import org.sagisolas.recipehost.protocol.ItemStack
import org.sagisolas.recipehost.protocol.Recipe
import org.sagisolas.recipehost.protocol.RecipeSlot

/**
 * How one recipe variant is drawn, decoded once from its stored body and layout: [Captured] when it
 * has a stored `Recipe layout` (ADR 0015), [Generic] otherwise. Every list of stacks is a Slot's
 * alternatives; an empty one is an empty slot.
 */
internal sealed interface RecipeDisplay {
    /** Every item id the display shows, each alternative included, in display order. */
    val itemIds: List<String>

    /** The recipe's input Slots and its results, as the generic layout lays them out. */
    data class Generic(val inputs: List<List<DisplayStack>>, val results: List<DisplayStack>) : RecipeDisplay {
        override val itemIds get() = (inputs.flatten() + results).map { it.itemId }
    }

    /** The layout's image, loaded by [key], [width] by [height] GUI px, with each `Recipe slot` filled. */
    data class Captured(val key: String, val width: Int, val height: Int, val slots: List<DisplaySlot>) : RecipeDisplay {
        override val itemIds get() = slots.flatMap { slot -> slot.stacks.map { it.itemId } }
    }
}

/** A `Recipe slot`'s rectangle in GUI px, with the alternatives it shows. */
internal data class DisplaySlot(val x: Int, val y: Int, val width: Int, val height: Int, val stacks: List<DisplayStack>)

/** An `ItemStack` to show; its [count] is at least 1. */
internal data class DisplayStack(val itemId: String, val count: Int)

/**
 * [recipe] as drawn: in [layout] when it has one, whose slots the recipe's `layout_stacks` fill by
 * index (a slot past them stays empty), and generically otherwise.
 */
internal fun recipeDisplayOf(recipe: Recipe, layout: StoredLayout?): RecipeDisplay =
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
            slots = layout.slots.mapIndexed { index, slot ->
                displaySlotOf(slot, recipe.layout_stacks.getOrNull(index)?.stacks.orEmpty())
            },
        )
    }

private fun displaySlotOf(slot: RecipeSlot, stacks: List<ItemStack>) =
    DisplaySlot(slot.x, slot.y, slot.width, slot.height, stacks.map(::displayStackOf))

/** A wire count of 0 means 1. */
private fun displayStackOf(stack: ItemStack) = DisplayStack(stack.item_id, stack.count.coerceAtLeast(1))
