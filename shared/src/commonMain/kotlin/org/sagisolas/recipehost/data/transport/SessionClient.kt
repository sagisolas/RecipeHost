/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import okio.IOException
import org.sagisolas.recipehost.data.pairing.PairingPayload
import org.sagisolas.recipehost.platform.LocalNetworkAccess
import org.sagisolas.recipehost.protocol.Auth
import org.sagisolas.recipehost.protocol.Envelope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Something that opens a `Session` for a [PairingPayload] and streams its `Snapshot`. */
internal fun interface SessionSource {
    /**
     * Emits the mod's envelopes, `Handshake` first, and completes after `Done` (which isn't
     * emitted). Fails with a [ScanFailure].
     */
    fun open(payload: PairingPayload): Flow<Envelope>
}

/**
 * Opens one `Session` over a [SocketConnector], with no retry (see [RetryingSessionClient]).
 * [localNetwork] tells a host that's down from one the OS won't let the app reach.
 */
internal class SessionClient(
    private val connector: SocketConnector,
    private val localNetwork: LocalNetworkAccess,
    private val connectTimeout: Duration = 1_500.milliseconds,
    /** How long the connection may stay silent before it counts as dropped (a half-open socket). */
    private val idleTimeout: Duration = 15.seconds,
) : SessionSource {
    override fun open(payload: PairingPayload): Flow<Envelope> =
        flow {
            val connection = connectFirstReachable(payload)
            try {
                droppedOnIoError(received = 0) { connection.send(Envelope(auth = Auth(token = payload.token))) }
                var received = 0
                while (true) {
                    val envelope = receiveWithinIdleTimeout(connection, received)
                    received++
                    if (envelope.done != null) break
                    envelope.error?.let { throw ScanFailure.Domain.ModError(it.code) }
                    envelope.handshake?.let {
                        if (it.protocol_version != PROTOCOL_VERSION) {
                            throw ScanFailure.Domain.UnsupportedProtocolVersion(it.protocol_version)
                        }
                    }
                    emit(envelope)
                }
            } finally {
                connection.close()
            }
        }

    /**
     * Tries the hosts one at a time, in payload order, never in parallel. The mod's
     * `SessionListener` serves only the newest authenticated connection, so a losing parallel
     * attempt that authenticates last would cut off the winning one.
     */
    private suspend fun connectFirstReachable(payload: PairingPayload): EnvelopeConnection {
        for (host in payload.hosts) {
            // Assigned inside the timeout block rather than returned from it: a connect that
            // completes just as the timeout fires is still a claimed Session, so use it rather
            // than drop it unclosed.
            var connection: EnvelopeConnection? = null
            try {
                withTimeoutOrNull(connectTimeout) { connection = connector.connect(host, payload.port) }
            } catch (_: IOException) {
                // Refused or unreachable: try the next host.
            }
            connection?.let { return it }
        }
        // The permission is per app, not per host: asking about one is enough.
        if (payload.hosts.firstOrNull()?.let { localNetwork.isDenied(it) } == true) {
            throw ScanFailure.Network.LocalNetworkDenied(payload.hosts)
        }
        throw ScanFailure.Network.Unreachable(payload.hosts)
    }

    /**
     * The next envelope; a clean EOF, an I/O error or [idleTimeout] of silence is a drop.
     * [received] is how many envelopes came before, for the drop's message.
     */
    private suspend fun receiveWithinIdleTimeout(
        connection: EnvelopeConnection,
        received: Int,
    ): Envelope =
        withTimeoutOrNull(idleTimeout) {
            droppedOnIoError(received) { connection.receive() }
                ?: throw ScanFailure.Network.Dropped(IOException("the mod closed the connection"), received)
        } ?: throw ScanFailure.Network.Dropped(IOException("no envelope for $idleTimeout"), received)

    private inline fun <T> droppedOnIoError(
        received: Int,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (e: IOException) {
            throw ScanFailure.Network.Dropped(e, received)
        }

    private companion object {
        const val PROTOCOL_VERSION = 3
    }
}
