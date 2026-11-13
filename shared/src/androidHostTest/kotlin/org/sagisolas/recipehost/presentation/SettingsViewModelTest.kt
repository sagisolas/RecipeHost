/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.sagisolas.recipehost.data.scan.ScanProgress
import org.sagisolas.recipehost.domain.CatalogFixture
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.create6
import org.sagisolas.recipehost.domain.CatalogFixture.Companion.minecraft
import org.sagisolas.recipehost.domain.catalog.CatalogCounts
import org.sagisolas.recipehost.domain.catalog.CatalogStats
import org.sagisolas.recipehost.domain.catalog.ClearCatalog
import org.sagisolas.recipehost.domain.catalog.ItemSearch
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val catalog = CatalogFixture()
    private val progress = MutableStateFlow<ScanProgress?>(null)
    private val search = ItemSearch(catalog.database, EmptyCoroutineContext)

    @BeforeTest
    fun setMain() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        catalog.close()
    }

    private val clear = ClearCatalog(catalog.database, EmptyCoroutineContext)

    /** Refuses like `ScanController` does while a Scan runs; `ScanControllerTest` covers that refusal itself. */
    private var refuseClear = false

    private fun viewModel() =
        SettingsViewModel(
            clearCatalog = { (!refuseClear).also { if (it) clear() } },
            scanProgress = progress,
            counts = CatalogStats(catalog.database, catalog.driver, EmptyCoroutineContext).counts,
        )

    private suspend fun scanAtm() =
        catalog.scan("ATM", minecraft, create6, items = listOf("minecraft:stone" to "Stone"))

    /** What [scanAtm] leaves: minecraft and create, one Item, no recipes. `CatalogStatsTest` covers the size. */
    private val atm = CatalogCounts(mods = 2, items = 1, recipes = 0, bytes = 0)
    private val empty = CatalogCounts(mods = 0, items = 0, recipes = 0, bytes = 0)

    /** [SettingsViewModel.state] without the database size, which these tests don't pin. */
    private val SettingsViewModel.sizelessState
        get() = state.value.let { it.copy(counts = it.counts?.copy(bytes = 0)) }

    private suspend fun itemNames() = search.items("").first().map { it.displayName }

    @Test
    fun confirmingClearsTheCatalogAndLeavesForWelcome() =
        runTest {
            scanAtm()
            val viewModel = viewModel()
            runCurrent()

            viewModel.requestClear()
            runCurrent()
            assertTrue(viewModel.state.value.confirming)
            viewModel.confirmClear()
            runCurrent()

            assertEquals(emptyList(), itemNames())
            assertEquals(
                SettingsUiState(counts = empty, clearEnabled = true, confirming = false, cleared = true),
                viewModel.sizelessState,
            )
        }

    @Test
    fun countsFollowTheCatalog() =
        runTest {
            val viewModel = viewModel()
            runCurrent()
            assertEquals(empty, viewModel.sizelessState.counts)

            scanAtm()
            runCurrent()
            assertEquals(atm, viewModel.sizelessState.counts)
        }

    @Test
    fun cancellingKeepsTheCatalog() =
        runTest {
            scanAtm()
            val viewModel = viewModel()
            runCurrent()

            viewModel.requestClear()
            viewModel.cancelClear()
            runCurrent()

            assertEquals(listOf("Stone"), itemNames())
            assertEquals(
                SettingsUiState(counts = atm, clearEnabled = true, confirming = false, cleared = false),
                viewModel.sizelessState,
            )
        }

    @Test
    fun aRunningScanDisablesClearing() =
        runTest {
            scanAtm()
            val viewModel = viewModel()
            viewModel.requestClear()
            progress.value = ScanProgress.Icons(received = 1, total = 4)
            refuseClear = true
            runCurrent()

            assertEquals(
                SettingsUiState(counts = atm, clearEnabled = false, confirming = false, cleared = false),
                viewModel.sizelessState,
            )
            viewModel.confirmClear()
            runCurrent()
            assertEquals(listOf("Stone"), itemNames())

            progress.value = ScanProgress.Done
            refuseClear = false
            runCurrent()
            assertEquals(
                SettingsUiState(counts = atm, clearEnabled = true, confirming = false, cleared = false),
                viewModel.sizelessState,
            )
        }
}
