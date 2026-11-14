/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.sagisolas.recipehost.data.catalog.CatalogDatabase

/**
 * Builds the [AppGraph] and routes `recipehost://pair` deep links to it. The manifest keeps this
 * activity alive across configuration changes, so the graph (and a running Scan) isn't rebuilt.
 */
class MainActivity : ComponentActivity() {
    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        super.onCreate(savedInstanceState)

        graph =
            AppGraph(
                driver = AndroidSqliteDriver(CatalogDatabase.Schema, applicationContext, DATABASE_NAME),
                qrScanner = GmsQrScanner(this),
                localNetwork = RuntimeLocalNetworkAccess(this),
                screenWake = WindowScreenWake(this),
            )
        // A restored activity was already handed this intent before the process died.
        if (savedInstanceState == null) routePairingLink(intent)

        setContent {
            App(graph)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routePairingLink(intent)
    }

    private fun routePairingLink(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == PAIRING_SCHEME) graph.pairing.start(uri.toString())
    }

    private companion object {
        const val DATABASE_NAME = "catalog.db"
        const val PAIRING_SCHEME = "recipehost"
    }
}
