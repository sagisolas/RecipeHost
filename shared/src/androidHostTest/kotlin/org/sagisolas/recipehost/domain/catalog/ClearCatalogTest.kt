package org.sagisolas.recipehost.domain.catalog

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.shaped
import org.sagisolas.recipehost.domain.recipes.ItemRecipes
import org.sagisolas.recipehost.domain.recipes.RecipeDirection
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ClearCatalogTest {

    private val catalog = CatalogFixture()
    private val context = EmptyCoroutineContext
    private val clear = ClearCatalog(catalog.database, context)
    private val modList = ModList(catalog.database, context)
    private val search = ItemSearch(catalog.database, context)
    private val resolve = ResolveStack(catalog.database, context)
    private val itemRecipes = ItemRecipes(catalog.database, context)

    @AfterTest
    fun close() = catalog.close()

    private suspend fun scanAtm9() = catalog.scan(
        "ATM9", minecraft, create6,
        items = listOf("minecraft:stick" to "Stick", "create:cogwheel" to "Cogwheel"),
        recipes = listOf(shaped("create:cogwheel", result = "create:cogwheel", "minecraft:stick")),
        icons = listOf("create:cogwheel", "minecraft:stick"),
    )

    @Test
    fun clearingLeavesAnEmptyCatalog() = runTest {
        scanAtm9()

        clear()

        assertEquals(emptyList(), modList.mods.first())
        assertEquals(emptyList(), search.items("").first())
        assertEquals(emptyList(), search.items("stick").first())
        assertEquals(StackResolution.Unresolved("minecraft:stick"), resolve(listOf("minecraft:stick")).first().getValue("minecraft:stick"))
        assertEquals(emptyList(), itemRecipes("create:cogwheel", RecipeDirection.Recipes).first())
        assertEquals(emptyList(), itemRecipes("minecraft:stick", RecipeDirection.Uses).first())
        assertEquals(0L, catalog.database.iconQueries.atlasCount().executeAsOne())
    }

    @Test
    fun aScanAfterClearingFillsTheCatalogAgain() = runTest {
        scanAtm9()
        clear()

        scanAtm9()

        assertEquals(listOf("Stick", "Cogwheel"), search.items("").first().map { it.displayName })
        val icon = assertNotNull(search.items("stick").first().single().icon)
        assertEquals(IconRegion(icon.atlasId, x = 16, y = 0, size = 16), icon)
        assertEquals(CatalogFixture.ATLAS_PNG, IconAtlases(catalog.database, EmptyCoroutineContext).png(icon.atlasId)?.toByteString())
        assertEquals(listOf("create:cogwheel"), itemRecipes("create:cogwheel", RecipeDirection.Recipes).first().single().recipes.map { it.recipeId })
    }
}
