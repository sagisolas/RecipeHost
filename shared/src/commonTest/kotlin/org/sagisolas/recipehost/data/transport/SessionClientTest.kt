package org.sagisolas.recipehost.data.transport

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import okio.IOException
import org.sagisolas.recipehost.data.pairing.PairingPayload
import org.sagisolas.recipehost.data.transport.FakeSocketConnector.Attempt.Accept
import org.sagisolas.recipehost.data.transport.FakeSocketConnector.Attempt.Hang
import org.sagisolas.recipehost.data.transport.FakeSocketConnector.Attempt.Refuse
import org.sagisolas.recipehost.data.transport.FakeSocketConnector.ConnectCall
import org.sagisolas.recipehost.protocol.Auth
import org.sagisolas.recipehost.protocol.Done
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.Error
import org.sagisolas.recipehost.protocol.ErrorCode
import org.sagisolas.recipehost.protocol.Handshake
import org.sagisolas.recipehost.protocol.Item
import org.sagisolas.recipehost.protocol.ItemBatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionClientTest {

    private val token = ByteArray(32) { it.toByte() }.toByteString()

    private val handshake = Envelope(handshake = Handshake(protocol_version = 2, instance_name = "Pack"))
    private val items = Envelope(item_batch = ItemBatch(items = listOf(Item(id = "minecraft:stone", display_name = "Stone"))))
    private val done = Envelope(done = Done())

    private fun payload(vararg hosts: String) = PairingPayload(hosts.toList(), port = 25599, token = token)

    private fun client(connector: FakeSocketConnector) = RetryingSessionClient(SessionClient(connector))

    @Test
    fun sendsAuthFirstAndPassesEnvelopesThroughUntilDone() = runTest {
        val connector = FakeSocketConnector(this, Accept(listOf(handshake, items, done, items)))

        val received = client(connector).open(payload("192.168.1.10")).toList()

        assertEquals(listOf(handshake, items), received)
        assertEquals(listOf(Envelope(auth = Auth(token = token))), connector.written.single())
        assertEquals(1, connector.closedConnections)
    }

    @Test
    fun triesHostsInOrderMovingOnAfterATimeoutOrRefusal() = runTest {
        val connector = FakeSocketConnector(this, Hang, Refuse, Accept(listOf(handshake, done)))

        val received = client(connector).open(payload("192.168.1.10", "10.0.0.5", "fe80::1%wlan0")).toList()

        assertEquals(listOf(handshake), received)
        assertEquals(
            listOf(
                ConnectCall("192.168.1.10", 25599, atMillis = 0),
                ConnectCall("10.0.0.5", 25599, atMillis = 1_500),
                ConnectCall("fe80::1%wlan0", 25599, atMillis = 1_500),
            ),
            connector.connects,
        )
        assertEquals(1, connector.written.size, "Auth is written only to the host that connected")
    }

    @Test
    fun retriesOnceAfterAMidStreamDropAndRestartsFromTheHandshake() = runTest {
        val connector = FakeSocketConnector(
            this,
            Accept(listOf(handshake, items), failWith = IOException("connection reset")),
            Accept(listOf(handshake, items, done)),
        )

        val received = client(connector).open(payload("192.168.1.10")).toList()

        assertEquals(listOf(handshake, items, handshake, items), received)
        assertEquals(2, connector.written.size)
        connector.written.forEach { assertEquals(listOf(Envelope(auth = Auth(token = token))), it) }
        assertEquals(2, connector.closedConnections)
    }

    @Test
    fun aSecondDropSurfacesANetworkFailureWithoutCompletingTheStream() = runTest {
        val connector = FakeSocketConnector(
            this,
            Accept(listOf(handshake, items)), // EOF before Done
            Accept(listOf(handshake), failWith = IOException("connection reset")),
        )
        val received = mutableListOf<Envelope>()
        var failure: Throwable? = null

        client(connector).open(payload("192.168.1.10"))
            .catch { failure = it }
            .collect { received += it }

        assertIs<ScanFailure.Network.Dropped>(failure)
        assertEquals(listOf(handshake, items, handshake), received)
        assertEquals(2, connector.connects.size)
    }

    @Test
    fun noHostReachableTwiceIsANetworkFailure() = runTest {
        val connector = FakeSocketConnector(this, Hang, Refuse, Refuse, Hang)

        val failure = runCatching { client(connector).open(payload("192.168.1.10", "10.0.0.5")).toList() }
            .exceptionOrNull()

        assertIs<ScanFailure.Network.Unreachable>(failure)
        assertEquals(4, connector.connects.size)
    }

    @Test
    fun modErrorsAreDomainFailuresWithNoRetry() = runTest {
        for (code in listOf(ErrorCode.UNAUTHORIZED, ErrorCode.BUSY, ErrorCode.SOURCE_FAILURE)) {
            val connector = FakeSocketConnector(this, Accept(listOf(Envelope(error = Error(code = code)))))

            val failure = runCatching { client(connector).open(payload("192.168.1.10")).toList() }
                .exceptionOrNull()

            assertEquals(code, assertIs<ScanFailure.Domain.ModError>(failure).code)
            assertEquals(1, connector.connects.size, "no retry on $code")
        }
    }

    @Test
    fun aProtocolVersionOtherThanOneIsADomainFailureWithNoRetry() = runTest {
        val connector = FakeSocketConnector(
            this,
            Accept(listOf(Envelope(handshake = Handshake(protocol_version = 3)), items, done)),
        )
        val received = mutableListOf<Envelope>()

        val failure = runCatching { client(connector).open(payload("192.168.1.10")).collect { received += it } }
            .exceptionOrNull()

        assertEquals(3, assertIs<ScanFailure.Domain.UnsupportedProtocolVersion>(failure).version)
        assertEquals(emptyList(), received)
        assertEquals(1, connector.connects.size)
    }

    @Test
    fun aConnectThatCompletesAsItsTimeoutFiresIsUsedNotLeaked() = runTest {
        // The mod claims the Session on accept, so dropping this socket would leave every
        // later attempt with BUSY.
        val connector = FakeSocketConnector(this, Accept(listOf(handshake, done), connectMillis = 2_000))

        val received = client(connector).open(payload("192.168.1.10", "10.0.0.5")).toList()

        assertEquals(listOf(handshake), received)
        assertEquals(1, connector.connects.size)
        assertEquals(1, connector.closedConnections)
    }

    @Test
    fun aConnectionThatGoesSilentCountsAsADropAndIsRetried() = runTest {
        val connector = FakeSocketConnector(
            this,
            Accept(listOf(handshake, items), thenHang = true),
            Accept(listOf(handshake, done)),
        )

        val received = client(connector).open(payload("192.168.1.10")).toList()

        assertEquals(listOf(handshake, items, handshake), received)
        assertEquals(listOf(0L, 15_000L), connector.connects.map { it.atMillis })
        assertEquals(2, connector.closedConnections)
    }
}
