package org.sagisolas.recipehost

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.scan.ScanController
import org.sagisolas.recipehost.data.scan.ScanIngest
import org.sagisolas.recipehost.data.scan.ScanProgress
import org.sagisolas.recipehost.data.transport.KtorSocketConnector
import org.sagisolas.recipehost.data.transport.RetryingSessionClient
import org.sagisolas.recipehost.data.transport.SessionClient
import org.sagisolas.recipehost.domain.catalog.CatalogStats
import org.sagisolas.recipehost.domain.catalog.ClearCatalog
import org.sagisolas.recipehost.domain.catalog.IconAtlases
import org.sagisolas.recipehost.domain.catalog.ItemSearch
import org.sagisolas.recipehost.domain.catalog.ModList
import org.sagisolas.recipehost.domain.catalog.ResolveStacks
import org.sagisolas.recipehost.domain.recipes.Bookmarks
import org.sagisolas.recipehost.domain.recipes.ItemRecipes
import org.sagisolas.recipehost.domain.recipes.LayoutImages
import org.sagisolas.recipehost.platform.QrScanner
import org.sagisolas.recipehost.presentation.BookmarksViewModel
import org.sagisolas.recipehost.presentation.ItemGridViewModel
import org.sagisolas.recipehost.presentation.ItemViewModel
import org.sagisolas.recipehost.presentation.ModFilterRequests
import org.sagisolas.recipehost.presentation.ModListViewModel
import org.sagisolas.recipehost.presentation.ModVersionsViewModel
import org.sagisolas.recipehost.presentation.PairingEntry
import org.sagisolas.recipehost.presentation.QrPairing
import org.sagisolas.recipehost.presentation.ScanProgressViewModel
import org.sagisolas.recipehost.presentation.SettingsViewModel
import org.sagisolas.recipehost.presentation.WelcomeViewModel
import org.sagisolas.recipehost.ui.item.IconAtlasCache
import org.sagisolas.recipehost.ui.item.LayoutImageCache
import org.sagisolas.recipehost.ui.navigation.BookmarksEmptiness
import org.sagisolas.recipehost.ui.navigation.CatalogEmptiness
import kotlin.time.Clock

/**
 * The composition root: builds every app-scoped instance and wires them together. The Android
 * or iOS wrapper builds one per app process, with its platform's [driver] (whose schema is
 * [CatalogDatabase.Schema]) and [qrScanner], and passes it to [App].
 */
class AppGraph(
    driver: SqlDriver,
    qrScanner: QrScanner,
) {
    private val catalog = CatalogDatabase(driver)

    /**
     * Runs `Scan`s, so they outlive any screen. On I/O, since the `Catalog` writes block; a
     * failed Scan doesn't cancel the next one.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal val scans =
        ScanController(
            scope = appScope,
            sessions = RetryingSessionClient(SessionClient(KtorSocketConnector())),
            ingest = ScanIngest(catalog, Clock.System),
            clear = ClearCatalog(catalog, Dispatchers.IO)::invoke,
        )

    internal val itemSearch = ItemSearch(catalog, Dispatchers.IO)
    internal val modList = ModList(catalog, Dispatchers.IO)
    internal val itemRecipes = ItemRecipes(catalog, Dispatchers.IO)
    internal val resolveStacks = ResolveStacks(catalog, Dispatchers.IO)
    private val bookmarks = Bookmarks(catalog, Dispatchers.IO, Clock.System)
    private val catalogStats = CatalogStats(catalog, driver, Dispatchers.IO)

    /** Read on the main thread, only on a fresh start (not a restore): a count over one table. */
    internal val catalogEmptiness = CatalogEmptiness { catalog.itemQueries.count().executeAsOne() == 0L }

    /** Read on the main thread, only on a fresh start: a count over the bookmarks that resolve. */
    internal val bookmarksEmptiness = BookmarksEmptiness(bookmarks::isEmpty)

    /** Where the wrapper sends `recipehost://pair` deep links. */
    val pairing = PairingEntry(scans, qrScanner)

    /** Decoded `Icon atlas`es for every screen that shows Icons; loads and decodes on I/O. */
    internal val iconAtlases =
        IconAtlasCache(appScope, IconAtlases(catalog, Dispatchers.IO)::png, capacity = ICON_ATLASES_KEPT)

    /** Decoded `Recipe layout` images for the Item screen; loads and decodes on I/O. */
    internal val layoutImages = LayoutImageCache(appScope, LayoutImages(catalog, Dispatchers.IO)::png)

    init {
        // A Scan may have replaced the image under a layout key, so the cached images go when it
        // ends. On the main thread, where the cache is used.
        appScope.launch(Dispatchers.Main) {
            scans.progress.filter { it is ScanProgress.Done || it is ScanProgress.Failed }.collect {
                layoutImages
                    .clear()
            }
        }
    }

    private val qrPairing = QrPairing { pairing.scanQr() }

    /** From the mod list to the Items tab's grid, which owns the search text. */
    private val modFilters = ModFilterRequests()

    // The ViewModel factories, called by `viewModel { }` in each screen's entry. Navigation state
    // is the UI's: `App` builds the `Navigator`, and the screens navigate after calling their ViewModel.

    internal fun welcomeViewModel() = WelcomeViewModel(qrPairing)

    internal fun scanProgressViewModel() = ScanProgressViewModel(scans.progress, pairing.failure, qrPairing)

    internal fun itemGridViewModel() = ItemGridViewModel(itemSearch, modFilters, scans.progress, qrPairing)

    internal fun modListViewModel() = ModListViewModel(modList, modFilters)

    internal fun modVersionsViewModel() = ModVersionsViewModel(modList)

    internal fun itemViewModel(itemId: String) =
        ItemViewModel(itemId, itemRecipes::invoke, resolveStacks::invoke, bookmarks.ids, bookmarks::toggle)

    internal fun bookmarksViewModel() = BookmarksViewModel(bookmarks.recipes, resolveStacks::invoke, bookmarks::remove)

    internal fun settingsViewModel() = SettingsViewModel(scans::clearCatalog, scans.progress, catalogStats.counts)
}

/** Decoded Icon atlases kept: 64 sheets of 16×8 32 px cells, about 32 MB (ADR 0014). */
private const val ICON_ATLASES_KEPT = 64
