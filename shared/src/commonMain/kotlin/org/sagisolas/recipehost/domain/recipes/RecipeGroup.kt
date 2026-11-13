/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.domain.recipes

/**
 * The recipes of one [typeId] (e.g. `minecraft:crafting`, or an EMI category such as `create:mixing`),
 * ordered by recipe id. [name] is the type's name in the game's language, null when the `Catalog` has none.
 */
internal data class RecipeGroup(
    val typeId: String,
    val name: String?,
    val recipes: List<CatalogRecipe>,
)

/**
 * One recipe of a selected `Mod release` (ADR 0011), with its distinct bodies across `Instance`s.
 * [variants] come newest first: the first is from the most recently scanned Instance.
 */
internal data class CatalogRecipe(
    val releaseId: Long,
    val recipeId: String,
    val variants: List<RecipeVariant>,
) {
    /** The body to show first: the most recently scanned Instance's. */
    val defaultVariant: RecipeVariant get() = variants.first()
}

/**
 * One body of a recipe and the `Instance`s that have it, most recently scanned first. [display] is
 * drawn in the newest of those Instances' stored `Recipe layout`s, or generically when none has one
 * yet. It is decoded on first use: a screen shows one variant of one recipe per section, so the
 * rest of an Item's recipes, often thousands for a common Item's Uses, are never decoded.
 */
internal class RecipeVariant(
    val instanceNames: List<String>,
    decode: () -> RecipeDisplay,
) {
    constructor(display: RecipeDisplay, instanceNames: List<String>) : this(instanceNames, { display })

    val display: RecipeDisplay by lazy(decode)
}
