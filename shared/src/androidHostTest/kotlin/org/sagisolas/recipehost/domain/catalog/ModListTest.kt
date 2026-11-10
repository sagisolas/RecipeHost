package org.sagisolas.recipehost.domain.catalog

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create5
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.createAddition
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.jei
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
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

    @Test
    fun listsMinecraftFirstThenModsByNameEachWithItsReleasesNewestFirst() =
        runTest {
            catalog.scan("Old", minecraft, create5, jei, mcVersion = "1.20.1")
            catalog.scan("New", createAddition, create6, minecraft)

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
    fun marksTheSelectedReleaseAndSavesANewChoice() =
        runTest {
            catalog.scan("Old", minecraft, create5)
            catalog.scan("New", minecraft, create6)

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
            catalog.scan("Old", ModInfo(id = "jei", name = "JEI", version = "18.0.0"))
            catalog.scan("New", jei)

            assertEquals(listOf("Just Enough Items"), modList.mods.first().map { it.name })
        }

    @Test
    fun selectingAnotherModsReleaseIsRejected() =
        runTest {
            catalog.scan("ATM9", minecraft, create6)

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
