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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.sagisolas.recipehost.data.scan.ScanProgress
import org.sagisolas.recipehost.domain.CATALOG_REFRESH_PERIOD
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
class ModVersionsViewModelTest {
    private val catalog = CatalogFixture()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        catalog.close()
    }

    private fun modVersions() = ModVersionsViewModel(ModList(catalog.database, EmptyCoroutineContext))

    private fun grid() =
        ItemGridViewModel(
            ItemSearch(catalog.database, EmptyCoroutineContext),
            ModFilterRequests(),
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
    fun listsTheModsWithTheirReleasesNewestFirst() =
        runTest {
            scanTwoCreateVersions()
            val viewModel = modVersions()
            runCurrent()

            val create =
                viewModel.state.value.mods
                    .single { it.modId == "create" }
            assertEquals("Create", create.name)
            assertEquals(listOf("6.0.1", "5.1.0"), create.releases.map { it.version })
            assertEquals(catalog.releaseId(create6), create.selectedReleaseId)
        }

    @Test
    fun minecraftItselfShowsNoSeparateMinecraftVersion() =
        runTest {
            scanTwoCreateVersions()
            val viewModel = modVersions()
            runCurrent()

            val mods = viewModel.state.value.mods
            assertEquals(listOf<String?>(null), mods.single { it.modId == "minecraft" }.releases.map { it.mcVersion })
            assertEquals(listOf("1.21.1", "1.21.1"), mods.single { it.modId == "create" }.releases.map { it.mcVersion })
        }

    @Test
    fun pickingAReleaseUpdatesTheGridAtOnce() =
        runTest {
            scanTwoCreateVersions()
            val grid = grid()
            val modVersions = modVersions()
            runCurrent()
            assertEquals(
                "Cogwheel",
                grid.state.value.items
                    .single { it.itemId == "create:cogwheel" }
                    .displayName,
            )

            modVersions.select("create", catalog.releaseId(create5))
            // The grid's query re-runs at most once per refresh period.
            advanceTimeBy(CATALOG_REFRESH_PERIOD)
            runCurrent()

            assertEquals(
                catalog.releaseId(create5),
                modVersions.state.value.mods
                    .single { it.modId == "create" }
                    .selectedReleaseId,
            )
            assertEquals(
                "Old Cogwheel",
                grid.state.value.items
                    .single { it.itemId == "create:cogwheel" }
                    .displayName,
            )
        }
}
