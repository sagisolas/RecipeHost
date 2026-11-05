package org.sagisolas.recipehost.domain.catalog

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create5
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.createAddition
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.jei
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemSearchTest {

    private val catalog = CatalogFixture()
    private val search = ItemSearch(catalog.database, EmptyCoroutineContext)
    private val modList = ModList(catalog.database, EmptyCoroutineContext)

    @AfterTest
    fun close() = catalog.close()

    @Test
    fun showsTheItemsOfTheSelectedReleasesAndFollowsASwitch() = runTest {
        catalog.scan("Old pack", minecraft, create5, items = listOf("minecraft:stick" to "Stick", "create:andesite_alloy" to "Andesite Alloy"))
        catalog.scan("New pack", minecraft, create6, items = listOf("minecraft:stick" to "Stick", "create:cogwheel" to "Cogwheel"))

        assertEquals(listOf("Stick", "Cogwheel"), search.items("").first().names())

        modList.select("create", catalog.releaseId(create5))

        assertEquals(listOf("Stick", "Andesite Alloy"), search.items("").first().names())
    }

    @Test
    fun anAtTokenFiltersByModIdPrefixAndTheRestMatchesNamePrefixes() = runTest {
        catalog.scan(
            "ATM9", minecraft, create6, createAddition,
            items = listOf(
                "minecraft:stick" to "Stick",
                "minecraft:iron_ingot" to "Iron Ingot",
                "create:cogwheel" to "Cogwheel",
                "create:large_cogwheel" to "Large Cogwheel",
                "create:iron_sheet" to "Iron Sheet",
                "createaddition:copper_wire" to "Copper Wire",
            ),
        )

        assertEquals(listOf("Cogwheel", "Large Cogwheel", "Iron Sheet", "Copper Wire"), search.items("@create").first().names())
        assertEquals(listOf("Copper Wire"), search.items("@createa").first().names())
        assertEquals(listOf("Iron Ingot", "Iron Sheet"), search.items("iro").first().names())
        assertEquals(listOf("Iron Sheet"), search.items("@create iron").first().names())
        assertEquals(listOf("Iron Sheet"), search.items("sheet  @create IRON").first().names())
        assertEquals(listOf("Large Cogwheel"), search.items("cog lar").first().names())
    }

    @Test
    fun searchTextWithFtsSyntaxIsTakenAsPlainWords() = runTest {
        catalog.scan("ATM9", minecraft, items = listOf("minecraft:jack_o_lantern" to "Jack o'Lantern", "minecraft:stick" to "Stick"))

        assertEquals(listOf("Jack o'Lantern"), search.items("o'lan").first().names())
        assertEquals(listOf("Jack o'Lantern"), search.items("\"JACK*\" ( -").first().names())
        assertEquals(listOf("Jack o'Lantern", "Stick"), search.items("  @  ").first().names())
    }

    @Test
    fun theModFilterKeepsOneModAndCombinesWithTheSearchText() = runTest {
        catalog.scan(
            "ATM9", minecraft, create6,
            items = listOf("minecraft:iron_ingot" to "Iron Ingot", "create:iron_sheet" to "Iron Sheet", "create:cogwheel" to "Cogwheel"),
        )

        assertEquals(listOf("Iron Sheet", "Cogwheel"), search.items("", modId = "create").first().names())
        assertEquals(listOf("Iron Sheet"), search.items("iron", modId = "create").first().names())
        assertEquals(emptyList(), search.items("@minecraft", modId = "create").first().names())
    }

    @Test
    fun minecraftComesFirstThenTheModsInTheirLatestScanOrderThenRegistryOrder() = runTest {
        catalog.scan(
            "ATM9", create6, jei, minecraft,
            items = listOf("create:cogwheel" to "Cogwheel", "jei:bookmark" to "Bookmark", "minecraft:stone" to "Stone", "minecraft:dirt" to "Dirt"),
        )
        catalog.scan("Other", jei, create6, items = listOf("jei:bookmark" to "Bookmark", "create:cogwheel" to "Cogwheel"))

        assertEquals(listOf("Stone", "Dirt", "Bookmark", "Cogwheel"), search.items("").first().names())
    }

    @Test
    fun anArrivingIconIsEmittedToARunningSearch() = runTest {
        catalog.scan("ATM9", minecraft, items = listOf("minecraft:stick" to "Stick"))
        val emissions = mutableListOf<List<CatalogItem>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { search.items("").toList(emissions) }

        catalog.scan("ATM9", minecraft, items = listOf("minecraft:stick" to "Stick"), icons = listOf("minecraft:stick"))

        assertNull(emissions.first().single().icon)
        assertEquals(IconRegion(atlasId = 1, x = 0, y = 0, size = 16), emissions.last().single().icon)
    }

    private fun List<CatalogItem>.names() = map { it.displayName }
}
