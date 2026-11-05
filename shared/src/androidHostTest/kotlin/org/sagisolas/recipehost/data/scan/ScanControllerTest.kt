package org.sagisolas.recipehost.data.scan

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.pairing.PairingPayload
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
    private val ingest = ScanIngest(CatalogDatabase(driver), Clock.System)
    private val payload = PairingPayload(listOf("192.168.1.10"), port = 25599, token = ByteArray(32).toByteString())

    @AfterTest
    fun closeDriver() = driver.close()

    /** Sends a Handshake, then holds the Session open until [finish] completes. */
    private class GatedSource : SessionSource {
        val finish = CompletableDeferred<Unit>()
        var opened = 0

        override fun open(payload: PairingPayload) = flow {
            opened++
            emit(
                Envelope(
                    handshake = Handshake(
                        protocol_version = 1,
                        instance_name = "ATM9",
                        mods = listOf(ModInfo(id = "minecraft", name = "Minecraft", version = "1.21.1")),
                    ),
                ),
            )
            finish.await()
        }
    }

    private fun TestScope.controller(source: SessionSource) = ScanController(backgroundScope, source, ingest)

    @Test
    fun progressFollowsTheRunningScan() = runTest {
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
    fun onlyOneScanRunsAtATime() = runTest {
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
    fun aNewScanReportsConnectingBeforeItRunsNotTheLastScansEnd() = runTest {
        val source = GatedSource()
        val controller = controller(source)
        source.finish.complete(Unit)
        controller.start(payload)
        runCurrent()
        assertEquals(ScanProgress.Done, controller.progress.value)

        controller.start(payload)

        assertEquals(ScanProgress.Connecting, controller.progress.value, "before the Scan's coroutine runs")
    }
}
