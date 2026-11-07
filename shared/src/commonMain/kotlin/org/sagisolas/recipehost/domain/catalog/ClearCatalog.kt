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
    suspend operator fun invoke() = withContext(context) {
        catalog.transaction {
            catalog.recipeQueries.clearOutputs()
            catalog.recipeQueries.clearInputs()
            catalog.recipeQueries.clear()
            catalog.iconQueries.clear()
            catalog.iconQueries.clearAtlases()
            catalog.recipeLayoutQueries.clear()
            // Deleting items fires the triggers that empty item_fts.
            catalog.itemQueries.clear()
            catalog.versionSelectionQueries.clear()
            catalog.instanceQueries.clear()
            catalog.modReleaseQueries.clear()
        }
    }
}
