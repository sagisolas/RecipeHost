package org.sagisolas.recipehost

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.platform.AvFoundationQrScanner
import org.sagisolas.recipehost.platform.NetworkFrameworkLocalNetworkAccess
import platform.UIKit.UIViewController

/**
 * The iOS half of the composition root: builds the [AppGraph] with the iOS platform collaborators.
 * The Swift `iOSApp` calls it once per process and keeps the graph.
 */
fun createAppGraph(): AppGraph =
    AppGraph(
        driver = NativeSqliteDriver(CatalogDatabase.Schema, "catalog.db"),
        qrScanner = AvFoundationQrScanner(),
        localNetwork = NetworkFrameworkLocalNetworkAccess(),
    )

/**
 * The iOS wrapper's root view controller, showing [App] for the process's [graph]. Compose binds
 * the swipe from the left edge to back (since CMP 1.10, with no flag), as Android's system back:
 * it reaches the innermost back handler, so a pushed screen pops and a full-screen route (whose
 * `onBack` does nothing) stays.
 */
@Suppress("ktlint:standard:function-naming") // Swift calls it by this name.
fun MainViewController(graph: AppGraph): UIViewController = ComposeUIViewController { App(graph) }
