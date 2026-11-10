package org.sagisolas.recipehost.domain.catalog

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create5
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.createAddition
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.jei
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.shaped
import org.sagisolas.recipehost.protocol.ModInfo
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModListTest {
    private val catalog = CatalogFixture()
    private val modList = ModList(catalog.database, EmptyCoroutineContext)

    @AfterTest
    fun close() = catalog.close()

    /** A Scan of [instance] in which each of [mods] adds one Item. */
    private suspend fun scanWithItems(
        instance: String,
        vararg mods: ModInfo,
        mcVersion: String = "1.21.1",
    ) = catalog.scan(instance, *mods, mcVersion = mcVersion, items = mods.map { "${it.id}:thing" to "Thing" })

    @Test
    fun listsMinecraftFirstThenModsByNameEachWithItsReleasesNewestFirst() =
        runTest {
            scanWithItems("Old", minecraft, create5, jei, mcVersion = "1.20.1")
            scanWithItems("New", createAddition, create6, minecraft)

            val mods = modList.mods.first()

            assertEquals(listOf("minecraft", "create", "createaddition", "jei"), mods.map { it.modId })
            assertEquals(
                listOf("Minecraft", "Create", "Create Crafts & Additions", "Just Enough Items"),
                mods.map { it.name },
            )
            assertEquals(
                listOf("6.0.1" to "1.21.1", "5.1.0" to "1.20.1"),
                mods.single { it.modId == "create" }.releases.map { it.version to it.mcVersion },
            )
            assertEquals(
                listOf("1.21.1" to "1.21.1", "1.21.1" to "1.20.1"),
                mods.single { it.modId == "minecraft" }.releases.map { it.version to it.mcVersion },
            )
        }

    @Test
    fun aModThatAddsNoItemsIsLeftOut() =
        runTest {
            val datapack = ModInfo(id = "mydatapack", name = "mydatapack", version = "datapack")
            catalog.scan(
                "ATM9",
                minecraft,
                datapack,
                items = listOf("minecraft:stick" to "Stick"),
                recipes = listOf(shaped("mydatapack:cheap_stick", result = "minecraft:stick", "minecraft:dirt")),
            )

            assertEquals(listOf("minecraft"), modList.mods.first().map { it.modId })
        }

    @Test
    fun aModWithItemsInAnyReleaseListsAllItsReleases() =
        runTest {
            scanWithItems("Old", minecraft, create5)
            catalog.scan("New", minecraft, create6, items = listOf("minecraft:stick" to "Stick"))

            assertEquals(
                listOf("6.0.1", "5.1.0"),
                modList.mods
                    .first()
                    .single { it.modId == "create" }
                    .releases
                    .map { it.version },
            )
        }

    @Test
    fun marksTheSelectedReleaseAndSavesANewChoice() =
        runTest {
            scanWithItems("Old", minecraft, create5)
            scanWithItems("New", minecraft, create6)

            assertEquals(
                catalog.releaseId(create6),
                modList.mods
                    .first()
                    .single { it.modId == "create" }
                    .selectedReleaseId,
            )

            modList.select("create", catalog.releaseId(create5))

            assertEquals(
                catalog.releaseId(create5),
                modList.mods
                    .first()
                    .single { it.modId == "create" }
                    .selectedReleaseId,
            )
            assertEquals(
                catalog.releaseId(create5),
                ModList(catalog.database, EmptyCoroutineContext)
                    .mods
                    .first()
                    .single {
                        it.modId ==
                            "create"
                    }.selectedReleaseId,
            )
        }

    @Test
    fun aModNamedDifferentlyInItsNewestReleaseShowsTheNewestName() =
        runTest {
            scanWithItems("Old", ModInfo(id = "jei", name = "JEI", version = "18.0.0"))
            scanWithItems("New", jei)

            assertEquals(listOf("Just Enough Items"), modList.mods.first().map { it.name })
        }

    @Test
    fun selectingAnotherModsReleaseIsRejected() =
        runTest {
            scanWithItems("ATM9", minecraft, create6)

            assertFailsWith<IllegalArgumentException> { modList.select("create", catalog.releaseId(minecraft)) }
            assertEquals(
                catalog.releaseId(create6),
                modList.mods
                    .first()
                    .single { it.modId == "create" }
                    .selectedReleaseId,
            )
        }
}
