/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.domain.recipes

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create5
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.shaped
import org.sagisolas.recipehost.domain.catalog.ClearCatalog
import org.sagisolas.recipehost.domain.catalog.ModList
import org.sagisolas.recipehost.protocol.RecipeType
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class BookmarksTest {
    private val catalog = CatalogFixture()

    private var nowMillis = 0L
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(nowMillis++)
        }
    private val bookmarks = Bookmarks(catalog.database, EmptyCoroutineContext, clock)
    private val modList = ModList(catalog.database, EmptyCoroutineContext)

    @AfterTest
    fun close() = catalog.close()

    private val planks = shaped("minecraft:oak_planks", result = "minecraft:oak_planks", "minecraft:oak_log")
    private val stick = shaped("minecraft:stick", result = "minecraft:stick", "minecraft:oak_planks")
    private val cogwheel = shaped("create:cogwheel", result = "create:cogwheel", "minecraft:stick")

    private suspend fun scanAtm9() = catalog.scan("ATM9", minecraft, create6, recipes = listOf(planks, stick, cogwheel))

    private suspend fun shownIds() = bookmarks.recipes.first().map { it.recipeId }

    @Test
    fun togglingBookmarksARecipeThenRemovesIt() =
        runTest {
            scanAtm9()

            bookmarks.toggle("minecraft:stick")
            assertEquals(setOf("minecraft:stick"), bookmarks.ids.first())
            assertEquals(listOf("minecraft:stick"), shownIds())

            bookmarks.toggle("minecraft:stick")
            assertEquals(emptySet(), bookmarks.ids.first())
            assertEquals(emptyList(), shownIds())
        }

    @Test
    fun removeDeletesTheBookmark() =
        runTest {
            scanAtm9()
            bookmarks.toggle("minecraft:stick")

            bookmarks.remove("minecraft:stick")

            assertEquals(emptySet(), bookmarks.ids.first())
        }

    @Test
    fun theNewestBookmarkComesFirst() =
        runTest {
            scanAtm9()

            bookmarks.toggle("minecraft:stick")
            bookmarks.toggle("create:cogwheel")
            bookmarks.toggle("minecraft:oak_planks")

            assertEquals(listOf("minecraft:oak_planks", "create:cogwheel", "minecraft:stick"), shownIds())
        }

    @Test
    fun aBookmarkCarriesItsTypeAndItsVariantsNewestInstanceFirst() =
        runTest {
            val crafting = RecipeType(id = "minecraft:crafting_shaped", name = "Crafting")
            catalog.scan("ATM9", minecraft, recipes = listOf(stick), types = listOf(crafting))
            val override = shaped("minecraft:stick", result = "minecraft:stick", "minecraft:bamboo")
            catalog.scan("Vanilla+", minecraft, recipes = listOf(override), types = listOf(crafting))
            bookmarks.toggle("minecraft:stick")

            val bookmarked = bookmarks.recipes.first().single()

            assertEquals("minecraft:crafting_shaped", bookmarked.typeId)
            assertEquals("Crafting", bookmarked.typeName)
            assertEquals(listOf(listOf("Vanilla+"), listOf("ATM9")), bookmarked.variants.map { it.instanceNames })
            assertEquals(
                listOf(DisplayStack("minecraft:stick", 1)),
                bookmarked.variants
                    .first()
                    .display.results,
            )
        }

    @Test
    fun aBookmarkShowsTheRecipeOfTheSelectedRelease() =
        runTest {
            val oldCogwheel = shaped("create:cogwheel", result = "create:cogwheel", "minecraft:oak_planks")
            catalog.scan("Old", minecraft, create5, recipes = listOf(oldCogwheel))
            scanAtm9()
            bookmarks.toggle("create:cogwheel")
            val inputOf = { bookmarked: BookmarkedRecipe ->
                (bookmarked.variants.single().display as RecipeDisplay.Generic)
                    .inputs
                    .single()
                    .single()
                    .itemId
            }
            assertEquals("minecraft:stick", inputOf(bookmarks.recipes.first().single()))

            modList.select("create", catalog.releaseId(create5))

            assertEquals("minecraft:oak_planks", inputOf(bookmarks.recipes.first().single()))
        }

    @Test
    fun aBookmarkWithNoRecipeInTheSelectedReleasesIsHiddenButKept() =
        runTest {
            catalog.scan("Old", minecraft, create5)
            scanAtm9()
            bookmarks.toggle("create:cogwheel")

            modList.select("create", catalog.releaseId(create5))

            assertEquals(emptyList(), shownIds())
            assertTrue(bookmarks.isEmpty(), "only bookmarks that resolve count")
            assertEquals(setOf("create:cogwheel"), bookmarks.ids.first())

            modList.select("create", catalog.releaseId(create6))
            assertEquals(listOf("create:cogwheel"), shownIds())
            assertFalse(bookmarks.isEmpty())
        }

    @Test
    fun clearingTheCatalogDeletesBookmarks() =
        runTest {
            scanAtm9()
            bookmarks.toggle("minecraft:stick")

            ClearCatalog(catalog.database, EmptyCoroutineContext)()
            scanAtm9()

            assertEquals(emptySet(), bookmarks.ids.first())
            assertEquals(emptyList(), shownIds())
        }
}
