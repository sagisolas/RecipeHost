package org.sagisolas.recipehost.domain.recipes

/**
 * How one recipe variant is drawn, decoded once from its stored body and layout: [Captured] when it
 * has a stored `Recipe layout` (ADR 0015), [Generic] otherwise. Every list of stacks is a Slot's
 * alternatives; an empty one is an empty slot.
 */
internal sealed interface RecipeDisplay {
    /** Every item id the display shows, each alternative included, in display order. */
    val itemIds: List<String>

    /** The recipe's results, whether or not a `Recipe slot` shows them; empty when it has none. */
    val results: List<DisplayStack>

    /** The recipe's input Slots and its results, as the generic layout lays them out. */
    data class Generic(
        val inputs: List<List<DisplayStack>>,
        override val results: List<DisplayStack>,
    ) : RecipeDisplay {
        override val itemIds get() = (inputs.flatten() + results).map { it.itemId }
    }

    /** The layout's image, loaded by [key], [width] by [height] GUI px, with each `Recipe slot` filled. */
    data class Captured(
        val key: String,
        val width: Int,
        val height: Int,
        val slots: List<DisplaySlot>,
        override val results: List<DisplayStack>,
    ) : RecipeDisplay {
        override val itemIds get() = slots.flatMap { slot -> slot.stacks.map { it.itemId } }
    }
}

/** A `Recipe slot`'s rectangle in GUI px, with the alternatives it shows. */
internal data class DisplaySlot(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val stacks: List<DisplayStack>,
)

/** An `ItemStack` to show; its [count] is at least 1. */
internal data class DisplayStack(
    val itemId: String,
    val count: Int,
)
