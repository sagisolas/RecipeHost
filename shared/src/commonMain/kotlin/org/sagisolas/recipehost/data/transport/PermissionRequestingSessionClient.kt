/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.sagisolas.recipehost.data.pairing.PairingPayload
import org.sagisolas.recipehost.platform.LocalNetworkAccess
import org.sagisolas.recipehost.protocol.Envelope

/**
 * Wraps a [SessionSource] so that each `Scan` first [requests][LocalNetworkAccess.request] the
 * Local Network permission. It sits outside [RetryingSessionClient], so a retry doesn't ask again.
 * A known denial fails with [ScanFailure.Network.LocalNetworkDenied] straight away, without
 * trying the hosts; an OS that answers only on connection (iOS) is caught by [SessionClient].
 */
internal class PermissionRequestingSessionClient(
    private val source: SessionSource,
    private val localNetwork: LocalNetworkAccess,
) : SessionSource {
    override fun open(payload: PairingPayload): Flow<Envelope> =
        flow {
            if (!localNetwork.request()) throw ScanFailure.Network.LocalNetworkDenied(payload.hosts)
            emitAll(source.open(payload))
        }
}
