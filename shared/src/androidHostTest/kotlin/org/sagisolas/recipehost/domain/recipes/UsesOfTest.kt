package org.sagisolas.recipehost.domain.recipes

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create5
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.shaped
import org.sagisolas.recipehost.protocol.ItemStack
import org.sagisolas.recipehost.protocol.Slot
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UsesOfTest {

    private val catalog = CatalogFixture()
    private val usesOf = UsesOf(catalog.database, EmptyCoroutineContext)

    @AfterTest
    fun close() = catalog.close()

    @Test
    fun recipesTakingTheItemInAnySlotAlternativeAreGroupedByType() = runTest {
        val chest = shaped("minecraft:chest", result = "minecraft:chest").copy(
            inputs = listOf(Slot(stacks = listOf(ItemStack(item_id = "minecraft:oak_planks"), ItemStack(item_id = "minecraft:spruce_planks")))),
        )
        catalog.scan(
            "ATM9", minecraft, create6,
            recipes = listOf(
                chest,
                shaped("minecraft:stick", result = "minecraft:stick", "minecraft:spruce_planks"),
                shaped("create:cutting/spruce_planks", result = "minecraft:stick", "minecraft:spruce_planks", type = "create:cutting"),
                shaped("minecraft:spruce_planks", result = "minecraft:spruce_planks", "minecraft:spruce_log"),
            ),
        )

        val groups = usesOf("minecraft:spruce_planks").first()

        assertEquals(
            listOf(
                "minecraft:crafting_shaped" to listOf("minecraft:chest", "minecraft:stick"),
                "create:cutting" to listOf("create:cutting/spruce_planks"),
            ),
            groups.map { group -> group.typeId to group.recipes.map { it.recipeId } },
        )
    }

    @Test
    fun usesFromAReleaseThatIsNotSelectedAreLeftOut() = runTest {
        catalog.scan("Old", minecraft, create5, recipes = listOf(shaped("create:pressing/old", result = "create:iron_sheet", "minecraft:iron_ingot")))
        catalog.scan("New", minecraft, create6)

        assertEquals(emptyList(), usesOf("minecraft:iron_ingot").first())
    }
}
