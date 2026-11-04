package org.sagisolas.recipehost.domain.recipes

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create5
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.shaped
import org.sagisolas.recipehost.domain.catalog.ModList
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipesForTest {

    private val catalog = CatalogFixture()
    private val recipesFor = RecipesFor(catalog.database, EmptyCoroutineContext)
    private val modList = ModList(catalog.database, EmptyCoroutineContext)

    @AfterTest
    fun close() = catalog.close()

    private val pressing = "create:pressing"
    private val crafting = "minecraft:crafting_shaped"

    @Test
    fun recipesWhoseResultIsTheItemAreGroupedByType() = runTest {
        catalog.scan(
            "ATM9", minecraft, create6,
            recipes = listOf(
                shaped("minecraft:iron_ingot_from_nuggets", result = "minecraft:iron_ingot", "minecraft:iron_nugget"),
                shaped("minecraft:iron_ingot_from_block", result = "minecraft:iron_ingot", "minecraft:iron_block"),
                shaped("create:pressing/iron_ingot", result = "create:iron_sheet", "minecraft:iron_ingot", type = pressing),
                shaped("create:crushing/raw_iron", result = "minecraft:iron_ingot", "minecraft:raw_iron", type = "create:crushing"),
            ),
        )

        val groups = recipesFor("minecraft:iron_ingot").first()

        assertEquals(
            listOf(
                "create:crushing" to listOf("create:crushing/raw_iron"),
                crafting to listOf("minecraft:iron_ingot_from_block", "minecraft:iron_ingot_from_nuggets"),
            ),
            groups.map { group -> group.typeId to group.recipes.map { it.recipeId } },
        )
    }

    @Test
    fun onlyRecipesOwnedByTheSelectedReleasesCount() = runTest {
        catalog.scan("Old", minecraft, create5, recipes = listOf(shaped("create:pressing/old", result = "create:iron_sheet", "minecraft:iron_ingot", type = pressing)))
        catalog.scan("New", minecraft, create6, recipes = listOf(shaped("create:pressing/new", result = "create:iron_sheet", "minecraft:iron_ingot", type = pressing)))

        assertEquals(listOf("create:pressing/new"), recipeIdsFor("create:iron_sheet"))

        modList.select("create", catalog.releaseId(create5))

        assertEquals(listOf("create:pressing/old"), recipeIdsFor("create:iron_sheet"))
    }

    @Test
    fun identicalBodiesFromTwoInstancesAreOneVariantListingBothNamesNewestFirst() = runTest {
        val stick = shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")
        catalog.scan("Vanilla", minecraft, recipes = listOf(stick))
        catalog.scan("ATM9", minecraft, recipes = listOf(stick))

        val recipe = recipesFor("minecraft:stick").first().single().recipes.single()

        assertEquals(listOf(listOf("ATM9", "Vanilla")), recipe.variants.map { it.instanceNames })
        assertEquals(stick, recipe.defaultVariant.recipe)
    }

    @Test
    fun differentBodiesAreTwoVariantsAndTheNewestInstanceIsTheDefault() = runTest {
        val vanillaStick = shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")
        val packStick = shaped("minecraft:stick", result = "minecraft:stick", "minecraft:bamboo")
        catalog.scan("ATM9", minecraft, recipes = listOf(packStick))
        catalog.scan("Vanilla", minecraft, recipes = listOf(vanillaStick))
        catalog.scan("Skyblock", minecraft, recipes = listOf(packStick))

        val recipe = recipesFor("minecraft:stick").first().single().recipes.single()

        assertEquals(
            listOf(packStick to listOf("Skyblock", "ATM9"), vanillaStick to listOf("Vanilla")),
            recipe.variants.map { it.recipe to it.instanceNames },
        )
        assertEquals(packStick, recipe.defaultVariant.recipe)
    }

    @Test
    fun anItemWithNoRecipesHasNoGroups() = runTest {
        catalog.scan("ATM9", minecraft, recipes = listOf(shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")))

        assertEquals(emptyList(), recipesFor("minecraft:oak_planks").first())
    }

    private suspend fun recipeIdsFor(itemId: String) =
        recipesFor(itemId).first().flatMap { group -> group.recipes.map { it.recipeId } }
}
