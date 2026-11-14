/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import org.sagisolas.recipehost.data.pairing.PairingPayload
import org.sagisolas.recipehost.protocol.Envelope

/**
 * Wraps a [SessionSource] with the retry rule: a [ScanFailure.Network] reopens the whole
 * `Session` once with the same payload (the listener stays up after a drop, ADR 0001), and a
 * [ScanFailure.Domain] fails straight away.
 *
 * Envelopes from the failed attempt have already been emitted. A retry starts over with a new
 * `Handshake`, so a consumer drops whatever it has staged whenever a `Handshake` arrives. The
 * failed attempt never completes, so its frames never reach the consumer as a complete
 * `Snapshot`.
 */
internal class RetryingSessionClient(
    private val source: SessionSource,
) : SessionSource {
    override fun open(payload: PairingPayload): Flow<Envelope> =
        source.open(payload).retryWhen { cause, attempt ->
            val retry = cause is ScanFailure.Network && attempt == 0L
            // Logged here, as the retry's own failure is all that reaches the Scan's log.
            if (retry) println("[recipehost] Session failed, retrying once: ${cause.stackTraceToString()}")
            retry
        }
}
