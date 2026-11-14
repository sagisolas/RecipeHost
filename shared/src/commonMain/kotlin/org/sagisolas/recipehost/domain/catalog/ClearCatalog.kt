/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.domain.catalog

import kotlinx.coroutines.withContext
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import kotlin.coroutines.CoroutineContext

/**
 * Deletes everything in the `Catalog` in one transaction. Callers make sure no `Scan` is running,
 * since one committing afterwards fills it again.
 *
 * [context] runs the deletes; `AppGraph` passes an I/O dispatcher.
 */
internal class ClearCatalog(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    suspend operator fun invoke() =
        withContext(context) {
            catalog.transaction {
                // Bookmarks are short-lived: they go with the recipes they point at.
                catalog.bookmarkQueries.clear()
                catalog.recipeQueries.clearOutputs()
                catalog.recipeQueries.clearSlots()
                catalog.recipeQueries.clear()
                catalog.recipeQueries.clearTypeKeys()
                catalog.slotQueries.clearItems()
                catalog.slotQueries.clear()
                catalog.slotQueries.clearNames()
                catalog.iconQueries.clear()
                catalog.iconQueries.clearAtlases()
                catalog.recipeLayoutQueries.clear()
                catalog.recipeLayoutQueries.clearPatches()
                catalog.recipeTypeQueries.clear()
                // Deleting items fires the triggers that empty item_fts.
                catalog.itemQueries.clear()
                catalog.versionSelectionQueries.clear()
                catalog.instanceQueries.clear()
                catalog.modReleaseQueries.clear()
            }
        }
}
