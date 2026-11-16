/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.catalog

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.sagisolas.recipehost.domain.CatalogFixture
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoCatalogTest {
    private val target = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CatalogDatabase.Schema.create(it) }
    private val catalog = CatalogDatabase(target)
    private val tempFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "recipehost-demo-${Random.nextLong().toULong()}.db"

    @AfterTest
    fun close() {
        target.close()
        FileSystem.SYSTEM.delete(tempFile)
    }

    private fun demoCatalog(bytes: () -> ByteArray) =
        DemoCatalog(catalog, target, FileSystem.SYSTEM, tempFile, { bytes() }, Dispatchers.IO)

    @Test
    fun loadingCopiesTheDemoIntoTheCatalogAndKeepsItSearchable() =
        runTest {
            val source =
                demoFileOf {
                    scan(
                        "Demo",
                        CatalogFixture.minecraft,
                        items =
                            listOf("minecraft:oak_log" to "Oak Log"),
                    )
                }

            demoCatalog { FileSystem.SYSTEM.read(source) { readByteArray() } }.load()

            assertEquals(1L, catalog.itemQueries.count().executeAsOne())
            assertEquals(
                listOf("Oak Log"),
                catalog.itemQueries
                    .searchNames("oak*")
                    .executeAsList()
                    .map { it.display_name },
            )
            assertFalse(FileSystem.SYSTEM.exists(tempFile), "the temp copy goes once loaded")
        }

    @Test
    fun theBundledDemoCatalogMatchesTheCurrentSchema() =
        runTest {
            demoCatalog { FileSystem.SYSTEM.read(BUNDLED_DEMO) { readByteArray() } }.load()

            assertTrue(catalog.itemQueries.count().executeAsOne() > 0, "the demo has items")
            assertTrue(
                catalog.itemQueries
                    .searchNames("oak*")
                    .executeAsList()
                    .isNotEmpty(),
                "its items are searchable",
            )
        }

    @Test
    fun everyCatalogTableIsCopied() {
        assertEquals(catalogTablesOf(target), CATALOG_TABLES.toSet())
    }

    /** A Catalog file holding what [seed] scans into it. */
    private suspend fun demoFileOf(seed: suspend CatalogFixture.() -> Unit): Path {
        val fixture = CatalogFixture()
        try {
            fixture.seed()
            val file = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "recipehost-source-${Random.nextLong().toULong()}.db"
            fixture.driver.execute(null, "VACUUM INTO ?", 1) { bindString(0, file.toString()) }
            return file
        } finally {
            fixture.close()
        }
    }
}

/** The schema's own tables: not SQLite's, nor the full-text index, which triggers on `item` fill. */
private fun catalogTablesOf(driver: SqlDriver): Set<String> =
    driver
        .executeQuery(
            null,
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'item_fts%'",
            { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.next().value) names += cursor.getString(0)!!
                QueryResult.Value(names)
            },
            0,
        ).value

/** Gradle runs the tests from the module's directory. */
private val BUNDLED_DEMO = "src/commonMain/composeResources/files/demo-catalog.db".toPath()
