package org.sagisolas.recipehost.domain.recipes

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
 * One body of a recipe and the `Instance`s that have it, most recently scanned first. [display] is
 * drawn in the newest of those Instances' stored `Recipe layout`s, or generically when none has one yet.
 */
internal data class RecipeVariant(val display: RecipeDisplay, val instanceNames: List<String>)
