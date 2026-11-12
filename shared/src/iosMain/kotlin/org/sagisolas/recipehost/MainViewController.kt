package org.sagisolas.recipehost

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseFileContext
import kotlinx.cinterop.ExperimentalForeignApi
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.platform.AvFoundationQrScanner
import org.sagisolas.recipehost.platform.NetworkFrameworkLocalNetworkAccess
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.UIKit.UIViewController

private const val CATALOG_DB = "catalog.db"

/**
 * The iOS half of the composition root: builds the [AppGraph] with the iOS platform collaborators.
 * The Swift `iOSApp` calls it once per process and keeps the graph.
 */
fun createAppGraph(): AppGraph {
    val driver = NativeSqliteDriver(CatalogDatabase.Schema, CATALOG_DB)
    // The driver may open its connection lazily: a first query makes sure the files exist. The catalog
    // is a cache the app rebuilds from the game, so it stays out of iCloud backups.
    driver.executeQuery(null, "PRAGMA user_version", { QueryResult.Unit }, 0)
    excludeFromBackup(DatabaseFileContext.databasePath(CATALOG_DB, null))
    return AppGraph(
        driver = driver,
        qrScanner = AvFoundationQrScanner(),
        localNetwork = NetworkFrameworkLocalNetworkAccess(),
    )
}

/** Marks the database file at [dbPath], and its `-wal` and `-shm` files if present, as excluded from backup. */
@OptIn(ExperimentalForeignApi::class)
private fun excludeFromBackup(dbPath: String) {
    listOf(dbPath, "$dbPath-wal", "$dbPath-shm")
        .filter { NSFileManager.defaultManager.fileExistsAtPath(it) }
        .forEach { NSURL.fileURLWithPath(it).setResourceValue(true, NSURLIsExcludedFromBackupKey, null) }
}

/**
 * The iOS wrapper's root view controller, showing [App] for the process's [graph]. Compose binds
 * the swipe from the left edge to back (since CMP 1.10, with no flag), as Android's system back:
 * it reaches the innermost back handler, so a pushed screen pops and a full-screen route (whose
 * `onBack` does nothing) stays.
 */
@Suppress("ktlint:standard:function-naming") // Swift calls it by this name.
fun MainViewController(graph: AppGraph): UIViewController = ComposeUIViewController { App(graph) }
