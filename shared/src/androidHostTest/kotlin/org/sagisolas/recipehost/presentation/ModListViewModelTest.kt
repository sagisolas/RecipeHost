package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.sagisolas.recipehost.data.scan.ScanProgress
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create5
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import org.sagisolas.recipehost.domain.catalog.ItemSearch
import org.sagisolas.recipehost.domain.catalog.ModList
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ModListViewModelTest {
    private val catalog = CatalogFixture()
    private val modFilters = ModFilterRequests()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        catalog.close()
    }

    private fun modList() = ModListViewModel(ModList(catalog.database, EmptyCoroutineContext), modFilters)

    private fun grid() =
        ItemGridViewModel(
            ItemSearch(catalog.database, EmptyCoroutineContext),
            modFilters,
            MutableStateFlow<ScanProgress?>(null),
            QrPairing {},
        )

    private suspend fun scanTwoCreateVersions() {
        catalog.scan(
            "Old",
            minecraft,
            create5,
            items =
                listOf(
                    "minecraft:stone" to "Stone",
                    "create:cogwheel" to "Old Cogwheel",
                ),
        )
        catalog.scan(
            "New",
            minecraft,
            create6,
            items =
                listOf(
                    "minecraft:stone" to "Stone",
                    "create:cogwheel" to "Cogwheel",
                ),
        )
    }

    @Test
    fun listsTheModsMinecraftFirst() =
        runTest {
            scanTwoCreateVersions()
            val viewModel = modList()
            runCurrent()

            assertEquals(
                listOf(
                    ModListEntry("minecraft", "Minecraft", itemCount = 1),
                    ModListEntry("create", "Create", itemCount = 1),
                ),
                viewModel.state.value.mods,
            )
        }

    @Test
    fun eachModCountsOnlyItsSelectedReleasesItems() =
        runTest {
            catalog.scan("Old", minecraft, create5, items = listOf("create:cogwheel" to "Cogwheel"))
            catalog.scan(
                "New",
                minecraft,
                create6,
                items =
                    listOf(
                        "minecraft:stone" to "Stone",
                        "minecraft:granite" to "Granite",
                        "create:cogwheel" to "Cogwheel",
                        "create:shaft" to "Shaft",
                        "create:belt" to "Belt",
                    ),
            )
            val viewModel = modList()
            runCurrent()

            // Create 6 is selected: its three Items, not the four across both Create releases.
            assertEquals(
                listOf(
                    ModListEntry("minecraft", "Minecraft", itemCount = 2),
                    ModListEntry("create", "Create", itemCount = 3),
                ),
                viewModel.state.value.mods,
            )
        }

    @Test
    fun aHiddenModReturnsOnceItsSelectedReleaseHasItems() =
        runTest {
            catalog.scan("Old", minecraft, create5, items = listOf("create:cogwheel" to "Cogwheel"))
            catalog.scan("New", minecraft, create6, items = listOf("minecraft:stone" to "Stone"))
            val viewModel = modList()
            runCurrent()

            ModList(catalog.database, EmptyCoroutineContext).select("create", catalog.releaseId(create5))
            runCurrent()

            assertEquals(
                listOf("minecraft", "create"),
                viewModel.state.value.mods
                    .map { it.modId },
            )
        }

    @Test
    fun aModWhoseSelectedReleaseHasNoItemsIsHidden() =
        runTest {
            catalog.scan("Old", minecraft, create5, items = listOf("create:cogwheel" to "Cogwheel"))
            // Create 6 is now selected, and this Scan brings none of its Items.
            catalog.scan("New", minecraft, create6, items = listOf("minecraft:stone" to "Stone"))
            val viewModel = modList()
            runCurrent()

            assertEquals(
                listOf("minecraft"),
                viewModel.state.value.mods
                    .map { it.modId },
            )
        }

    @Test
    fun choosingAModFiltersTheGrid() =
        runTest {
            scanTwoCreateVersions()
            val grid = grid()
            val modList = modList()
            runCurrent()

            modList.chooseMod("create")
            runCurrent()

            assertEquals("@create", grid.state.value.searchText)
            assertEquals(
                listOf("create:cogwheel"),
                grid.state.value.items
                    .map { it.itemId },
            )
        }
}
