/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.sagisolas.recipehost.domain.catalog.CatalogItem
import org.sagisolas.recipehost.domain.catalog.StackResolution
import org.sagisolas.recipehost.domain.recipes.DisplayPatch
import org.sagisolas.recipehost.domain.recipes.DisplayStack
import org.sagisolas.recipehost.domain.recipes.RecipeDisplay
import org.sagisolas.recipehost.domain.recipes.RecipeVariant
import kotlin.time.Duration.Companion.milliseconds

/**
 * How one recipe is drawn, chosen per recipe: [Captured] when it has a stored `Recipe layout`
 * (ADR 0015), [Generic] otherwise.
 */
internal sealed interface RecipeLayoutUi {
    /** The input slots in a wrapped row, an arrow, then the results. A `null` input is an empty slot. */
    data class Generic(
        val inputs: List<StackUi?>,
        val results: List<StackUi>,
    ) : RecipeLayoutUi

    /**
     * The layout's image, loaded by [key], [width] by [height] GUI px, with [patch]'s tiles over it
     * when it has one, and the recipe's stacks in its [slots].
     */
    data class Captured(
        val key: String,
        val width: Int,
        val height: Int,
        val slots: List<LayoutSlotUi>,
        val patch: DisplayPatch? = null,
    ) : RecipeLayoutUi
}

/** A `Recipe slot`'s rectangle in GUI px, with the stack it shows now; `null` when it's empty. */
internal data class LayoutSlotUi(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val stack: StackUi?,
)

/** An ItemStack as shown: [item] is `null` when it doesn't resolve, so its id shows instead. */
internal data class StackUi(
    val itemId: String,
    val count: Int,
    val item: CatalogItem?,
)

/**
 * A screen's one clock for the recipes it shows: every Slot with alternatives shows alternative
 * `tick % count`, so they all turn together. It runs while it is collected.
 */
internal fun alternativeTicks(): Flow<Int> =
    flow {
        var tick = 0
        while (true) {
            emit(tick++)
            delay(ALTERNATIVE_SHOW_TIME)
        }
    }

/** [display] at [tick]: each slot shows its alternative `tick % count`. */
internal fun layoutOf(
    display: RecipeDisplay,
    items: Map<String, CatalogItem?>,
    tick: Int,
): RecipeLayoutUi =
    when (display) {
        is RecipeDisplay.Generic -> {
            RecipeLayoutUi.Generic(
                inputs = display.inputs.map { stacks -> stacks.alternativeAt(tick)?.let { stackOf(it, items) } },
                results = display.results.map { stackOf(it, items) },
            )
        }

        is RecipeDisplay.Captured -> {
            RecipeLayoutUi.Captured(
                key = display.key,
                width = display.width,
                height = display.height,
                patch = display.patch,
                slots =
                    display.slots.map { slot ->
                        LayoutSlotUi(
                            slot.x,
                            slot.y,
                            slot.width,
                            slot.height,
                            slot.stacks.alternativeAt(tick)?.let { stackOf(it, items) },
                        )
                    },
            )
        }
    }

private fun stackOf(
    stack: DisplayStack,
    items: Map<String, CatalogItem?>,
) = StackUi(stack.itemId, count = stack.count, item = items[stack.itemId])

/** The alternative shown at [tick], or `null` for an empty slot. */
private fun List<DisplayStack>.alternativeAt(tick: Int): DisplayStack? = if (isEmpty()) null else this[tick % size]

internal fun itemOf(resolution: StackResolution): CatalogItem? = (resolution as? StackResolution.Resolved)?.item

/** Each of [ids] with its Item, or `null` when it doesn't resolve; nothing is asked for no ids. */
internal fun resolveItems(
    ids: List<String>,
    resolveStacks: (List<String>) -> Flow<Map<String, StackResolution>>,
): Flow<Map<String, CatalogItem?>> =
    if (ids.isEmpty()) {
        flowOf(emptyMap())
    } else {
        resolveStacks(ids).map { resolutions -> ids.associateWith { id -> resolutions[id]?.let(::itemOf) } }
    }

/**
 * The "From <instance>" choices of a recipe with [variants]: each variant's Instance names, newest
 * first. Empty when the recipe has only one body, so there is nothing to choose.
 */
internal fun variantChoicesOf(variants: List<RecipeVariant>): List<List<String>> =
    if (variants.size > 1) variants.map { it.instanceNames } else emptyList()

/** Keeps a recipe screen's state shared across a configuration change, which briefly stops the collection. */
internal const val STOP_TIMEOUT_MILLIS = 5_000L

/** How long each alternative of a Slot shows. */
private val ALTERNATIVE_SHOW_TIME = 1_000.milliseconds
