package org.sagisolas.recipehost

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.scan.ScanController
import org.sagisolas.recipehost.data.scan.ScanIngest
import org.sagisolas.recipehost.data.transport.KtorSocketConnector
import org.sagisolas.recipehost.data.transport.RetryingSessionClient
import org.sagisolas.recipehost.data.transport.SessionClient
import org.sagisolas.recipehost.platform.QrScanner
import org.sagisolas.recipehost.presentation.PairingEntry
import org.sagisolas.recipehost.presentation.navigation.CatalogEmptiness
import org.sagisolas.recipehost.presentation.navigation.Navigator
import org.sagisolas.recipehost.ui.item.ItemIconCache
import kotlin.time.Clock

/**
 * The composition root: builds every app-scoped instance and wires them together. The Android
 * or iOS wrapper builds one per app process, with its platform's [driver] (whose schema is
 * [CatalogDatabase.Schema]) and [qrScanner], and passes it to [App].
 */
class AppGraph(driver: SqlDriver, qrScanner: QrScanner) {

    private val catalog = CatalogDatabase(driver)

    /**
     * Runs `Scan`s, so they outlive any screen. On I/O, since the `Catalog` writes block; a
     * failed Scan doesn't cancel the next one.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal val scans = ScanController(
        scope = appScope,
        sessions = RetryingSessionClient(SessionClient(KtorSocketConnector())),
        ingest = ScanIngest(catalog, Clock.System),
    )

    // TODO(issue 05): replace with the domain query, e.g. ItemSearch or the mod list being empty.
    private val catalogEmptiness = CatalogEmptiness { catalog.modReleaseQueries.all().executeAsList().isEmpty() }

    internal val navigator = Navigator(catalogEmptiness)

    /** Where the wrapper sends `recipehost://pair` deep links. */
    val pairing = PairingEntry(scans, navigator, qrScanner)

    internal val itemIcons = ItemIconCache()

    // TODO(issue 05): the domain queries (ItemSearch, ModList, RecipesFor, UsesOf, ResolveStack,
    //  ClearCatalog), built from `catalog`.
    // TODO(issues 08-10): the ViewModel factories, built from the queries above, `scans`,
    //  `pairing` and `navigator`.
}
