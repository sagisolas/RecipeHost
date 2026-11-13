/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import org.sagisolas.recipehost.domain.catalog.ItemSearch
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ItemGridViewModelTest {
    private val catalog = CatalogFixture()
    private val progress = MutableStateFlow<ScanProgress?>(null)
    private val modFilters = ModFilterRequests()
    private var scansRequested = 0

    @BeforeTest
    fun setMain() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        catalog.close()
    }

    private fun viewModel() =
        ItemGridViewModel(
            ItemSearch(catalog.database, EmptyCoroutineContext),
            modFilters,
            progress,
            QrPairing { scansRequested++ },
        )

    private suspend fun scanAtm() =
        catalog.scan(
            "ATM",
            minecraft,
            create6,
            items =
                listOf(
                    "minecraft:stone" to "Stone",
                    "minecraft:cobblestone" to "Cobblestone",
                    "create:cogwheel" to "Cogwheel",
                    "create:andesite_casing" to "Andesite Casing",
                ),
        )

    private val ItemGridViewModel.itemIds get() = state.value.items.map { it.itemId }

    @Test
    fun showsEveryItemOfTheVersionSelection() =
        runTest {
            scanAtm()
            val viewModel = viewModel()
            runCurrent()

            assertEquals(
                listOf("minecraft:cobblestone", "minecraft:stone", "create:andesite_casing", "create:cogwheel"),
                viewModel.itemIds,
            )
        }

    @Test
    fun searchTextReachesTheQuery() =
        runTest {
            scanAtm()
            val viewModel = viewModel()

            viewModel.onSearchTextChange("stone")
            runCurrent()
            assertEquals(listOf("minecraft:stone"), viewModel.itemIds)

            viewModel.onSearchTextChange("@create cas")
            runCurrent()
            assertEquals(listOf("create:andesite_casing"), viewModel.itemIds)
        }

    @Test
    fun aChosenModReplacesTheModFilterAndKeepsTheNameWords() =
        runTest {
            scanAtm()
            val viewModel = viewModel()
            viewModel.onSearchTextChange("@mine c")
            runCurrent()

            modFilters.request("create")
            runCurrent()

            assertEquals("@create c", viewModel.state.value.searchText)
            assertEquals(listOf("create:andesite_casing", "create:cogwheel"), viewModel.itemIds)
        }

    @Test
    fun aThinBarFillsHalfForIconsAndHalfForLayouts() =
        runTest {
            val viewModel = viewModel()

            progress.value = ScanProgress.Icons(received = 250, total = 1000)
            runCurrent()
            assertEquals(0.125f, viewModel.state.value.loadingProgress, "Icons fill the first half")

            progress.value = ScanProgress.Layouts(received = 500, total = 1000)
            runCurrent()
            assertEquals(0.75f, viewModel.state.value.loadingProgress, "layouts fill the second half")

            progress.value = ScanProgress.Done
            runCurrent()
            assertNull(viewModel.state.value.loadingProgress)

            progress.value = ScanProgress.Failed(RuntimeException("Wi-Fi off"))
            runCurrent()
            assertNull(
                viewModel.state.value.loadingProgress,
                "a Scan that stopped during Icons leaves the grid as it is",
            )
        }

    @Test
    fun theScanButtonOpensTheScanner() =
        runTest {
            viewModel().scan()
            runCurrent()

            assertEquals(1, scansRequested)
        }
}
