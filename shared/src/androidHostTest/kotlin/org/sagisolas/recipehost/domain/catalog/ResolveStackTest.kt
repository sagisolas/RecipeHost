package org.sagisolas.recipehost.domain.catalog

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create5
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveStackTest {

    private val catalog = CatalogFixture()
    private val resolve = ResolveStack(catalog.database, EmptyCoroutineContext)
    private val modList = ModList(catalog.database, EmptyCoroutineContext)

    @AfterTest
    fun close() = catalog.close()

    @Test
    fun anItemOfASelectedReleaseResolvesToItsNameAndIcon() = runTest {
        catalog.scan("ATM9", minecraft, items = listOf("minecraft:stick" to "Stick"), icons = listOf("minecraft:stick"))

        // The fresh Catalog's first atlas has id 1.
        assertEquals(
            StackResolution.Resolved(
                CatalogItem(catalog.releaseId(minecraft), "minecraft:stick", "Stick", IconRegion(atlasId = 1, x = 0, y = 0, size = 16)),
            ),
            resolve("minecraft:stick").first(),
        )
    }

    @Test
    fun anItemOfAModWithNoReleaseIsUnresolved() = runTest {
        catalog.scan("ATM9", minecraft, items = listOf("minecraft:stick" to "Stick"))

        assertEquals(StackResolution.Unresolved("create:cogwheel"), resolve("create:cogwheel").first())
    }

    @Test
    fun anItemMissingFromTheSelectedReleaseIsUnresolvedUntilAReleaseThatHasItIsSelected() = runTest {
        catalog.scan("Old", minecraft, create5, items = listOf("create:andesite_alloy" to "Andesite Alloy"))
        catalog.scan("New", minecraft, create6, items = listOf("create:cogwheel" to "Cogwheel"))

        assertEquals(StackResolution.Unresolved("create:andesite_alloy"), resolve("create:andesite_alloy").first())

        modList.select("create", catalog.releaseId(create5))

        assertEquals(
            StackResolution.Resolved(CatalogItem(catalog.releaseId(create5), "create:andesite_alloy", "Andesite Alloy", null)),
            resolve("create:andesite_alloy").first(),
        )
    }
}
