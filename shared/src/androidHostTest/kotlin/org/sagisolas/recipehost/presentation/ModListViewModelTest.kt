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

    private fun grid() = ItemGridViewModel(
        ItemSearch(catalog.database, EmptyCoroutineContext),
        modFilters,
        MutableStateFlow<ScanProgress?>(null),
        QrPairing {},
    )

    private suspend fun scanTwoCreateVersions() {
        catalog.scan("Old", minecraft, create5, items = listOf("minecraft:stone" to "Stone", "create:cogwheel" to "Old Cogwheel"))
        catalog.scan("New", minecraft, create6, items = listOf("minecraft:stone" to "Stone", "create:cogwheel" to "Cogwheel"))
    }

    @Test
    fun listsTheModsWithTheirReleasesNewestFirst() = runTest {
        scanTwoCreateVersions()
        val viewModel = modList()
        runCurrent()

        val create = viewModel.state.value.mods.single { it.modId == "create" }
        assertEquals("Create", create.name)
        assertEquals(listOf("6.0.1", "5.1.0"), create.releases.map { it.version })
        assertEquals(catalog.releaseId(create6), create.selectedReleaseId)
    }


    @Test
    fun minecraftItselfShowsNoSeparateMinecraftVersion() = runTest {
        scanTwoCreateVersions()
        val viewModel = modList()
        runCurrent()

        val mods = viewModel.state.value.mods
        assertEquals(listOf<String?>(null), mods.single { it.modId == "minecraft" }.releases.map { it.mcVersion })
        assertEquals(listOf("1.21.1", "1.21.1"), mods.single { it.modId == "create" }.releases.map { it.mcVersion })
    }

    @Test
    fun aDropdownChangeUpdatesTheGrid() = runTest {
        scanTwoCreateVersions()
        val grid = grid()
        val modList = modList()
        runCurrent()
        assertEquals("Cogwheel", grid.state.value.items.single { it.itemId == "create:cogwheel" }.displayName)

        modList.select("create", catalog.releaseId(create5))
        runCurrent()

        assertEquals(catalog.releaseId(create5), modList.state.value.mods.single { it.modId == "create" }.selectedReleaseId)
        assertEquals("Old Cogwheel", grid.state.value.items.single { it.itemId == "create:cogwheel" }.displayName)
    }

    @Test
    fun choosingAModFiltersTheGrid() = runTest {
        scanTwoCreateVersions()
        val grid = grid()
        val modList = modList()
        runCurrent()

        modList.chooseMod("create")
        runCurrent()

        assertEquals("@create", grid.state.value.searchText)
        assertEquals(listOf("create:cogwheel"), grid.state.value.items.map { it.itemId })
    }
}
