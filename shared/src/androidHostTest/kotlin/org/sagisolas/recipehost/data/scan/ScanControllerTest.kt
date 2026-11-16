/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.scan

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.catalog.RecipeStore
import org.sagisolas.recipehost.data.pairing.PairingPayload
import org.sagisolas.recipehost.data.transport.ScanFailure
import org.sagisolas.recipehost.data.transport.SessionSource
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.Handshake
import org.sagisolas.recipehost.protocol.ModInfo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ScanControllerTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CatalogDatabase.Schema.create(it) }
    private val ingest =
        CatalogDatabase(
            driver,
        ).let { ScanIngest(it, RecipeStore(it), Clock.System, tempRecipeSpool()) }
    private val payload = PairingPayload(listOf("192.168.1.10"), port = 25599, token = ByteArray(32).toByteString())

    @AfterTest
    fun closeDriver() = driver.close()

    /** Sends a Handshake, then holds the Session open until [finish] completes. */
    private class GatedSource : SessionSource {
        val finish = CompletableDeferred<Unit>()
        var opened = 0

        override fun open(payload: PairingPayload) =
            flow {
                opened++
                emit(
                    Envelope(
                        handshake =
                            Handshake(
                                protocol_version = 1,
                                instance_name = "ATM9",
                                mods = listOf(ModInfo(id = "minecraft", name = "Minecraft", version = "1.21.1")),
                            ),
                    ),
                )
                finish.await()
            }
    }

    private var clears = 0

    /** Clears are counted, and each waits for [clearGate] when the test sets one. */
    private var clearGate: CompletableDeferred<Unit>? = null

    /** What a demo load did, in order with the clears: `"clear"` and `"demo"`. */
    private val steps = mutableListOf<String>()

    private fun TestScope.controller(
        source: SessionSource,
        loadDemo: suspend () -> Unit = { steps += "demo" },
    ) = ScanController(backgroundScope, source, ingest, loadDemo) {
        clearGate?.await()
        clears++
        steps += "clear"
    }

    @Test
    fun progressFollowsTheRunningScan() =
        runTest {
            val source = GatedSource()
            val controller = controller(source)
            assertNull(controller.progress.value)

            assertTrue(controller.start(payload))
            runCurrent()
            assertEquals(ScanProgress.Connecting, controller.progress.value)

            source.finish.complete(Unit)
            runCurrent()
            assertEquals(ScanProgress.Done, controller.progress.value)
        }

    @Test
    fun onlyOneScanRunsAtATime() =
        runTest {
            val source = GatedSource()
            val controller = controller(source)

            assertTrue(controller.start(payload))
            runCurrent()
            assertFalse(controller.start(payload), "a second start while scanning is ignored")
            runCurrent()
            assertEquals(1, source.opened)

            source.finish.complete(Unit)
            runCurrent()
            assertTrue(controller.start(payload), "a new Scan can start once the last one finished")
            runCurrent()
            assertEquals(2, source.opened)
        }

    @Test
    fun aNewScanReportsConnectingBeforeItRunsNotTheLastScansEnd() =
        runTest {
            val source = GatedSource()
            val controller = controller(source)
            source.finish.complete(Unit)
            controller.start(payload)
            runCurrent()
            assertEquals(ScanProgress.Done, controller.progress.value)

            controller.start(payload)

            assertEquals(ScanProgress.Connecting, controller.progress.value, "before the Scan's coroutine runs")
        }

    @Test
    fun clearingIsRefusedWhileAScanRuns() =
        runTest {
            val source = GatedSource()
            val controller = controller(source)
            controller.start(payload)
            runCurrent()

            assertFalse(controller.clearCatalog())
            assertEquals(0, clears)

            source.finish.complete(Unit)
            runCurrent()
            assertTrue(controller.clearCatalog())
            assertEquals(1, clears)
        }

    @Test
    fun aScanStartedDuringAClearRunsOnceTheClearIsDone() =
        runTest {
            val source = GatedSource()
            val controller = controller(source)
            val gate = CompletableDeferred<Unit>().also { clearGate = it }
            val cleared = async { controller.clearCatalog() }
            runCurrent()

            assertTrue(controller.start(payload))
            runCurrent()
            assertEquals(0, source.opened, "the Scan waits, so it can't write into a half-cleared Catalog")

            gate.complete(Unit)
            runCurrent()
            assertTrue(cleared.await())
            assertEquals(1, clears)
            assertEquals(1, source.opened)
        }

    @Test
    fun aClearFinishesEvenIfItsCallerIsCancelled() =
        runTest {
            val controller = controller(GatedSource())
            val gate = CompletableDeferred<Unit>().also { clearGate = it }
            val caller = launch { controller.clearCatalog() }
            runCurrent()

            caller.cancel()
            gate.complete(Unit)
            runCurrent()

            assertEquals(1, clears, "e.g. the Settings ViewModel cleared mid-clear by a deep link")
        }

    @Test
    fun runningLastsFromStartUntilTheScanIsDone() =
        runTest {
            val first = GatedSource()
            val controller = controller(first)
            val running = mutableListOf<Boolean>()
            backgroundScope.launch { controller.running.collect { running += it } }
            runCurrent()
            assertEquals(listOf(false), running, "no Scan yet")

            controller.start(payload)
            runCurrent()
            assertEquals(listOf(false, true), running, "Connecting, then Items: reported once")

            first.finish.complete(Unit)
            runCurrent()
            assertEquals(listOf(false, true, false), running)

            controller.start(payload) // the source's gate is open, so this Scan runs straight through
            runCurrent()
            assertEquals(listOf(false, true, false, true, false), running, "each Scan runs once")
        }

    @Test
    fun runningEndsWhenTheScanFails() =
        runTest {
            val controller = controller({ flow { throw ScanFailure.Network.Dropped() } })
            val running = mutableListOf<Boolean>()
            backgroundScope.launch { controller.running.collect { running += it } }

            controller.start(payload)
            runCurrent()

            assertEquals(false, running.last(), "a failed Scan isn't running")
            assertTrue(controller.progress.value is ScanProgress.Failed)
        }

    @Test
    fun theDemoReplacesTheCatalogAndReportsLikeAScan() =
        runTest {
            val controller = controller(GatedSource())
            val progress = mutableListOf<ScanProgress?>()
            backgroundScope.launch { controller.progress.collect { progress += it } }
            runCurrent()

            assertTrue(controller.startDemo())
            runCurrent()

            assertEquals(listOf("clear", "demo"), steps)
            assertEquals(
                listOf(null, ScanProgress.Connecting, ScanProgress.Done),
                progress,
                "the Scan progress screen follows it to the Items tab",
            )
        }

    @Test
    fun theDemoWaitsForARunningScan() =
        runTest {
            val source = GatedSource()
            val controller = controller(source)
            controller.start(payload)
            runCurrent()

            assertFalse(controller.startDemo())
            runCurrent()

            assertEquals(emptyList(), steps)
        }

    @Test
    fun aFailedDemoLoadIsAFailedScan() =
        runTest {
            val controller = controller(GatedSource(), loadDemo = { error("bad demo file") })

            controller.startDemo()
            runCurrent()

            assertTrue(controller.progress.value is ScanProgress.Failed)
        }
}
