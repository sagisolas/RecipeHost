/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.pairing.PairingPayload
import org.sagisolas.recipehost.data.pairing.PairingPayloadException
import org.sagisolas.recipehost.data.scan.ScanController
import org.sagisolas.recipehost.data.scan.ScanIngest
import org.sagisolas.recipehost.data.scan.tempRecipeSpool
import org.sagisolas.recipehost.data.transport.SessionSource
import org.sagisolas.recipehost.platform.QrScanner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock

class PairingEntryTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CatalogDatabase.Schema.create(it) }

    /** Records each Session it opens and holds it open. */
    private class RecordingSource : SessionSource {
        val opened = mutableListOf<PairingPayload>()

        override fun open(payload: PairingPayload) =
            flow<Nothing> {
                opened += payload
                awaitCancellation()
            }
    }

    private val source = RecordingSource()

    /** What the fake [QrScanner] returns; `null` is a cancelled scan. */
    private var scanned: String? = null

    @AfterTest
    fun closeDriver() = driver.close()

    private fun TestScope.entry() =
        PairingEntry(
            ScanController(
                backgroundScope,
                source,
                ScanIngest(CatalogDatabase(driver), Clock.System, tempRecipeSpool()),
            ) {},
            QrScanner { scanned },
        )

    @Test
    fun aValidPairingUriStartsAScanAndShowsItsProgress() =
        runTest {
            val entry = entry()

            entry.start("recipehost://pair?v=1&h=192.168.1.5,10.0.0.2&p=51234&t=$ZERO_TOKEN")
            runCurrent()

            assertEquals(listOf(listOf("192.168.1.5", "10.0.0.2")), source.opened.map { it.hosts })
            assertEquals(51234, source.opened.single().port)
            assertEquals(Unit, entry.progressRequests.first(), "asks the UI to show Scan progress")
        }

    @Test
    fun anUnparseablePairingUriShowsADomainErrorAndStartsNoScan() =
        runTest {
            val entry = entry()

            entry.start("recipehost://pair?v=2&h=192.168.1.5&p=51234&t=$ZERO_TOKEN")
            runCurrent()

            assertEquals(emptyList(), source.opened)
            assertIs<PairingPayloadException.UnsupportedVersion>(entry.failure.value)
            assertEquals(Unit, entry.progressRequests.first(), "asks the UI to show Scan progress")
        }

    @Test
    fun aValidUriClearsTheLastFailure() =
        runTest {
            val entry = entry()
            entry.start("not a pairing uri")
            assertIs<PairingPayloadException.MalformedUri>(entry.failure.value)

            entry.start("recipehost://pair?v=1&h=192.168.1.5&p=51234&t=$ZERO_TOKEN")
            assertNull(entry.failure.value)
        }

    @Test
    fun theInAppScannerStartsAScanFromTheCodeItReads() =
        runTest {
            scanned = "recipehost://pair?v=1&h=192.168.1.5&p=51234&t=$ZERO_TOKEN"
            val entry = entry()

            entry.scanQr()
            runCurrent()

            assertEquals(51234, source.opened.single().port)
            assertEquals(Unit, entry.progressRequests.first(), "asks the UI to show Scan progress")
        }

    @Test
    fun cancellingTheInAppScannerStaysWhereTheUserWas() =
        runTest {
            scanned = null
            val entry = entry()

            entry.scanQr()
            runCurrent()

            assertEquals(emptyList(), source.opened)
            assertNull(entry.failure.value)
            assertNull(withTimeoutOrNull(1) { entry.progressRequests.first() }, "no request to show Scan progress")
        }

    private companion object {
        /** 32 zero bytes, base64url without padding. */
        const val ZERO_TOKEN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}
