/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.catalog

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import kotlin.coroutines.CoroutineContext

/**
 * Fills the `Catalog` from the bundled `Demo catalog`, a Catalog file collected from a real game, so
 * the app can be tried (and reviewed for the stores) without Minecraft. Load it into an empty Catalog:
 * `ScanController` clears it first.
 *
 * The file is copied to [tempFile] and attached to [catalog]'s open [driver], and its rows are copied
 * in one transaction; the `item` triggers rebuild the full-text index. Copying into the open database
 * spares swapping the file under the driver, and every screen's queries update as after a `Scan`.
 *
 * [readDemo] returns the file's bytes; [context] runs the writes, and `AppGraph` passes an I/O dispatcher.
 */
internal class DemoCatalog(
    private val catalog: CatalogDatabase,
    private val driver: SqlDriver,
    private val fileSystem: FileSystem,
    private val tempFile: Path,
    private val readDemo: suspend () -> ByteArray,
    private val context: CoroutineContext,
) {
    suspend fun load() {
        val bytes = readDemo()
        withContext(context) {
            fileSystem.write(tempFile) { write(bytes) }
            try {
                copyFrom(tempFile)
            } finally {
                fileSystem.delete(tempFile)
            }
        }
        driver.notifyListeners(*CATALOG_TABLES.toTypedArray())
    }

    /**
     * With no suspension between the attach and the detach, so a driver that keeps a connection per
     * thread runs them all on one. SQLite can't attach inside a transaction, so the copy opens its own.
     */
    private fun copyFrom(file: Path) {
        driver.execute(null, "ATTACH DATABASE ? AS $SCHEMA", 1) { bindString(0, file.toString()) }
        try {
            catalog.transaction {
                CATALOG_TABLES.forEach { driver.execute(null, "INSERT INTO main.$it SELECT * FROM $SCHEMA.$it", 0) }
            }
        } finally {
            driver.execute(null, "DETACH DATABASE $SCHEMA", 0)
        }
    }
}

private const val SCHEMA = "demo"

/**
 * Every table of the Catalog schema except the full-text index, parents before the rows that point
 * at them (the reverse of `ClearCatalog`'s deletes). `SELECT *` relies on the demo file having the
 * current schema; `DemoCatalogTest` loads the bundled one to check.
 */
internal val CATALOG_TABLES =
    listOf(
        "mod_release",
        "instance",
        "version_selection",
        "item",
        "recipe_type",
        "layout_patch",
        "recipe_layout",
        "icon_atlas",
        "icon",
        "item_name",
        "slot",
        "slot_item",
        "recipe_type_key",
        "recipe",
        "recipe_slot",
        "recipe_output",
        "bookmark",
    )
