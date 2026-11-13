/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
import kotlin.test.assertTrue

class CatalogStatsTest {
    private val catalog = CatalogFixture()
    private val stats = CatalogStats(catalog.database, catalog.driver, EmptyCoroutineContext)

    @AfterTest
    fun close() = catalog.close()

    @Test
    fun anEmptyCatalogCountsNothing() =
        runTest {
            assertEquals(
                CatalogCounts(mods = 0, items = 0, recipes = 0, bytes = 0),
                stats.counts.first().copy(bytes = 0),
            )
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
            assertEquals(
                CatalogCounts(mods = 4, items = 3, recipes = 3, bytes = 0),
                stats.counts.first().copy(bytes = 0),
            )
        }

    @Test
    fun theSizeGrowsWithAScanAndShrinksWithAClear() =
        runTest {
            val empty = stats.counts.first().bytes
            catalog.scan(
                "A",
                minecraft,
                create6,
                items = (1..500).map { "minecraft:item_$it" to "Item $it" },
            )
            val scanned = stats.counts.first().bytes
            ClearCatalog(catalog.database, EmptyCoroutineContext)()
            val cleared = stats.counts.first().bytes

            assertTrue(empty > 0, "the schema alone takes pages")
            assertTrue(scanned > empty, "$scanned after the scan, $empty before")
            assertTrue(cleared < scanned, "$cleared after the clear, $scanned before")
        }
}
