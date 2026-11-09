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
            resolveOne("minecraft:stick"),
        )
    }

    @Test
    fun anItemOfAModWithNoReleaseIsUnresolved() = runTest {
        catalog.scan("ATM9", minecraft, items = listOf("minecraft:stick" to "Stick"))

        assertEquals(StackResolution.Unresolved("create:cogwheel"), resolveOne("create:cogwheel"))
    }

    @Test
    fun anItemMissingFromTheSelectedReleaseIsUnresolvedUntilAReleaseThatHasItIsSelected() = runTest {
        catalog.scan("Old", minecraft, create5, items = listOf("create:andesite_alloy" to "Andesite Alloy"))
        catalog.scan("New", minecraft, create6, items = listOf("create:cogwheel" to "Cogwheel"))

        assertEquals(StackResolution.Unresolved("create:andesite_alloy"), resolveOne("create:andesite_alloy"))

        modList.select("create", catalog.releaseId(create5))

        assertEquals(
            StackResolution.Resolved(CatalogItem(catalog.releaseId(create5), "create:andesite_alloy", "Andesite Alloy", null)),
            resolveOne("create:andesite_alloy"),
        )
    }

    @Test
    fun severalIdsResolveTogetherEachAgainstItsOwnModsSelectedRelease() = runTest {
        catalog.scan("ATM9", minecraft, create6, items = listOf("minecraft:stick" to "Stick", "create:cogwheel" to "Cogwheel"))

        assertEquals(
            mapOf(
                "minecraft:stick" to StackResolution.Resolved(CatalogItem(catalog.releaseId(minecraft), "minecraft:stick", "Stick", null)),
                "create:cogwheel" to StackResolution.Resolved(CatalogItem(catalog.releaseId(create6), "create:cogwheel", "Cogwheel", null)),
                "gadgets:widget" to StackResolution.Unresolved("gadgets:widget"),
            ),
            resolve(listOf("minecraft:stick", "create:cogwheel", "gadgets:widget")).first(),
        )
    }

    private suspend fun resolveOne(itemId: String) = resolve(listOf(itemId)).first().getValue(itemId)
}
