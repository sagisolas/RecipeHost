/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.transport

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.sagisolas.recipehost.data.pairing.PairingPayload
import org.sagisolas.recipehost.platform.LocalNetworkAccess
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.Handshake
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PermissionRequestingSessionClientTest {
    private val payload = PairingPayload(listOf("192.168.1.10"), port = 25599, token = ByteArray(32).toByteString())
    private val handshake = Envelope(handshake = Handshake(protocol_version = 3, instance_name = "Pack"))

    /** Records `request`, then `open` per attempt, in call order. */
    private val calls = mutableListOf<String>()

    /** What the user answers to [LocalNetworkAccess.request]. */
    private var granted = true

    private val localNetwork =
        object : LocalNetworkAccess {
            override suspend fun isDenied(host: String) = !granted

            override suspend fun request(): Boolean {
                calls += "request"
                return granted
            }
        }

    @Test
    fun requestsThePermissionBeforeTheSessionOpens() =
        runTest {
            val source =
                SessionSource {
                    flow {
                        calls += "open"
                        emit(handshake)
                    }
                }

            val received = PermissionRequestingSessionClient(source, localNetwork).open(payload).toList()

            assertEquals(listOf(handshake), received)
            assertEquals(listOf("request", "open"), calls)
        }

    @Test
    fun aRetryDoesNotAskAgain() =
        runTest {
            val source =
                SessionSource {
                    flow {
                        calls += "open"
                        throw ScanFailure.Network.Dropped()
                    }
                }

            PermissionRequestingSessionClient(RetryingSessionClient(source), localNetwork)
                .open(payload)
                .catch { }
                .toList()

            assertEquals(listOf("request", "open", "open"), calls)
        }

    @Test
    fun aDenialFailsWithoutTryingTheHosts() =
        runTest {
            granted = false
            val source =
                SessionSource {
                    flow {
                        calls += "open"
                        emit(handshake)
                    }
                }

            val failure =
                assertFailsWith<ScanFailure.Network.LocalNetworkDenied> {
                    PermissionRequestingSessionClient(RetryingSessionClient(source), localNetwork)
                        .open(payload)
                        .toList()
                }

            assertEquals(payload.hosts, failure.hosts)
            assertEquals(listOf("request"), calls)
        }
}
