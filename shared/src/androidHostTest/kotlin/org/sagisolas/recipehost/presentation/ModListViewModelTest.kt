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
    fun eachModShowsOnlyItsSelectedRelease() =
        runTest {
            scanTwoCreateVersions()
            val viewModel = modList()
            runCurrent()

            assertEquals(
                listOf(
                    ModListEntry("minecraft", "Minecraft", version = "1.21.1", mcVersion = null),
                    ModListEntry("create", "Create", version = "6.0.1", mcVersion = "1.21.1"),
                ),
                viewModel.state.value.mods,
            )
        }

    @Test
    fun followsTheVersionSelection() =
        runTest {
            scanTwoCreateVersions()
            val viewModel = modList()
            runCurrent()

            ModList(catalog.database, EmptyCoroutineContext).select("create", catalog.releaseId(create5))
            runCurrent()

            assertEquals(
                ModListEntry("create", "Create", version = "5.1.0", mcVersion = "1.21.1"),
                viewModel.state.value.mods
                    .single { it.modId == "create" },
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
