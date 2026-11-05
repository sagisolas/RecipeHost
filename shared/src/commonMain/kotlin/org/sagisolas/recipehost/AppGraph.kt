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
import org.sagisolas.recipehost.domain.catalog.ClearCatalog
import org.sagisolas.recipehost.domain.catalog.ItemSearch
import org.sagisolas.recipehost.domain.catalog.ModList
import org.sagisolas.recipehost.domain.catalog.ResolveStack
import org.sagisolas.recipehost.domain.recipes.RecipesFor
import org.sagisolas.recipehost.domain.recipes.UsesOf
import org.sagisolas.recipehost.platform.QrScanner
import org.sagisolas.recipehost.presentation.ItemGridViewModel
import org.sagisolas.recipehost.presentation.ModFilterRequests
import org.sagisolas.recipehost.presentation.ModListViewModel
import org.sagisolas.recipehost.presentation.PairingEntry
import org.sagisolas.recipehost.presentation.QrPairing
import org.sagisolas.recipehost.presentation.ScanProgressViewModel
import org.sagisolas.recipehost.presentation.WelcomeViewModel
import org.sagisolas.recipehost.ui.navigation.CatalogEmptiness
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

    internal val itemSearch = ItemSearch(catalog, Dispatchers.IO)
    internal val modList = ModList(catalog, Dispatchers.IO)
    internal val recipesFor = RecipesFor(catalog, Dispatchers.IO)
    internal val usesOf = UsesOf(catalog, Dispatchers.IO)
    internal val resolveStack = ResolveStack(catalog, Dispatchers.IO)
    internal val clearCatalog = ClearCatalog(catalog, Dispatchers.IO)

    /** Read on the main thread, only on a fresh start (not a restore): a count over one table. */
    internal val catalogEmptiness = CatalogEmptiness { catalog.itemQueries.count().executeAsOne() == 0L }

    /** Where the wrapper sends `recipehost://pair` deep links. */
    val pairing = PairingEntry(scans, qrScanner)

    internal val itemIcons = ItemIconCache()

    private val qrPairing = QrPairing { pairing.scanQr() }

    /** From the mod list to the Items tab's grid, which owns the search text. */
    private val modFilters = ModFilterRequests()

    // The ViewModel factories, called by `viewModel { }` in each screen's entry. Navigation state
    // is the UI's: `App` builds the `Navigator`, and the screens navigate after calling their ViewModel.
    // TODO(issues 09-10): the Item and Settings ViewModels.

    internal fun welcomeViewModel() = WelcomeViewModel(qrPairing)

    internal fun scanProgressViewModel() = ScanProgressViewModel(scans.progress, pairing.failure, qrPairing)

    internal fun itemGridViewModel() = ItemGridViewModel(itemSearch, modFilters, scans.progress, qrPairing)

    internal fun modListViewModel() = ModListViewModel(modList, modFilters)
}
