package org.sagisolas.recipehost.domain.recipes

import okio.ByteString.Companion.toByteString
import org.sagisolas.recipehost.protocol.Recipe
import org.sagisolas.recipehost.protocol.RecipeLayout
import org.sagisolas.recipehost.protocol.RecipeSlot

/** The recipes of one [typeId] (e.g. `minecraft:crafting_shaped`), ordered by recipe id. */
internal data class RecipeGroup(val typeId: String, val recipes: List<CatalogRecipe>)

/**
 * One recipe of a selected `Mod release` (ADR 0011), with its distinct bodies across `Instance`s.
 * [variants] come newest first: the first is from the most recently scanned Instance.
 */
internal data class CatalogRecipe(val releaseId: Long, val recipeId: String, val variants: List<RecipeVariant>) {
    /** The body to show first: the most recently scanned Instance's. */
    val defaultVariant: RecipeVariant get() = variants.first()
}

/**
 * One body of a recipe and the `Instance`s that have it, most recently scanned first. [layout] is
 * the newest of those Instances' stored `Recipe layout`s, or null when none has one yet.
 */
internal data class RecipeVariant(val recipe: Recipe, val instanceNames: List<String>, val layout: CatalogLayout?)

/**
 * A stored `Recipe layout` without its image: [width] and [height] in GUI px, and the `Recipe slot`s
 * the recipe's `layout_stacks` fill by index. The image is loaded by [key].
 */
internal data class CatalogLayout(val key: String, val width: Int, val height: Int, val slots: List<RecipeSlot>)

/** A recipe as one `Instance` has it: the row shape both recipe queries map to. */
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
) {
    /** Null unless the recipe's layout is stored: the query's left join gives all four or none. */
    val layout: CatalogLayout? = if (layoutKey != null && layoutWidth != null && layoutHeight != null && layoutSlots != null) {
        CatalogLayout(layoutKey, layoutWidth.toInt(), layoutHeight.toInt(), RecipeLayout.ADAPTER.decode(layoutSlots).slots)
    } else {
        null
    }
}

/**
 * Groups rows by type, then collapses each `(release, recipe id)` by body hash into variants.
 * Expects the rows ordered by type, recipe id and release, newest Instance first within a recipe,
 * as the recipe queries return them.
 */
internal fun List<InstanceRecipe>.toGroups(): List<RecipeGroup> =
    groupBy { it.typeId }.map { (typeId, rows) ->
        RecipeGroup(
            typeId = typeId,
            recipes = rows.groupBy { it.releaseId to it.recipeId }.map { (key, instances) ->
                CatalogRecipe(releaseId = key.first, recipeId = key.second, variants = instances.toVariants())
            },
        )
    }

/** Rows of one recipe, newest Instance first, collapsed by body hash; groupBy keeps first-seen order. */
private fun List<InstanceRecipe>.toVariants(): List<RecipeVariant> =
    groupBy { it.bodyHash.toByteString() }.values.map { sameBody ->
        RecipeVariant(
            recipe = Recipe.ADAPTER.decode(sameBody.first().body),
            instanceNames = sameBody.map { it.instanceName },
            layout = sameBody.firstNotNullOfOrNull { it.layout },
        )
    }
