package org.sagisolas.recipehost.domain.catalog

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create5
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.shaped
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogStatsTest {
    private val catalog = CatalogFixture()
    private val stats = CatalogStats(catalog.database, EmptyCoroutineContext)

    @AfterTest
    fun close() = catalog.close()

    @Test
    fun anEmptyCatalogCountsNothing() =
        runTest {
            assertEquals(CatalogCounts(mods = 0, items = 0, recipes = 0), stats.counts.first())
        }

    @Test
    fun eachModReleaseCountsAndARecipeKeptForSeveralInstancesCountsOnce() =
        runTest {
            val planks = shaped("minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_log")
            val stick = shaped("minecraft:stick", "minecraft:stick", "minecraft:oak_planks")
            catalog.scan(
                "Old",
                minecraft,
                create5,
                mcVersion = "1.20.1",
                items = listOf("minecraft:stone" to "Stone", "create:cog" to "Cogwheel"),
                recipes = listOf(planks),
            )
            catalog.scan(
                "A",
                minecraft,
                create6,
                items = listOf("minecraft:stone" to "Stone"),
                recipes = listOf(planks, stick),
            )
            catalog.scan(
                "B",
                minecraft,
                create6,
                items = listOf("minecraft:stone" to "Stone"),
                recipes = listOf(planks),
            )

            // minecraft 1.20.1 and 1.21.1, create 5 and 6; stone twice (one per minecraft release) and the cog;
            // planks under each minecraft release (A and B share one), and the stick.
            assertEquals(CatalogCounts(mods = 4, items = 3, recipes = 3), stats.counts.first())
        }
}
