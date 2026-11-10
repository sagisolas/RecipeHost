package org.sagisolas.recipehost

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.platform.QrScanner
import platform.UIKit.UIViewController

/** The iOS wrapper's entry point. iOS is not in v1: it only has to keep compiling. */
@Suppress("ktlint:standard:function-naming") // Swift calls it by this name.
fun MainViewController(): UIViewController {
    val graph = AppGraph(
        driver = NativeSqliteDriver(CatalogDatabase.Schema, "catalog.db"),
        // TODO(iOS): an AVFoundation scanner, and routing the recipehost:// URL scheme to graph.pairing.
        qrScanner = QrScanner { null },
    )
    return ComposeUIViewController { App(graph) }
}
