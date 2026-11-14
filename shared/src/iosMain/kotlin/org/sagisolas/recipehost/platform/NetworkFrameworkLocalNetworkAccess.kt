/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSURL
import platform.Network._nw_parameters_configure_protocol_default_configuration
import platform.Network._nw_parameters_configure_protocol_disable
import platform.Network.nw_connection_cancel
import platform.Network.nw_connection_copy_current_path
import platform.Network.nw_connection_create
import platform.Network.nw_connection_set_queue
import platform.Network.nw_connection_set_state_changed_handler
import platform.Network.nw_connection_start
import platform.Network.nw_connection_state_failed
import platform.Network.nw_connection_state_ready
import platform.Network.nw_connection_state_waiting
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_parameters_create_secure_udp
import platform.Network.nw_path_get_unsatisfied_reason
import platform.Network.nw_path_unsatisfied_reason_local_network_denied
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_queue_create
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * [LocalNetworkAccess] on the Network framework, which reports a path blocked by the Local
 * Network permission as [nw_path_unsatisfied_reason_local_network_denied] (the BSD sockets under
 * Ktor fail the same way for a host that's simply down).
 *
 * Probes with a UDP "connection" to an unused port: it sends nothing and never reaches the mod's
 * TCP listener, which would take the one `Session` on accept. An answer that doesn't come within
 * [PROBE_TIMEOUT] counts as not denied, so the user sees the general network error.
 */
@OptIn(ExperimentalForeignApi::class)
internal class NetworkFrameworkLocalNetworkAccess : LocalNetworkAccess {
    private val queue = dispatch_queue_create("org.sagisolas.recipehost.local-network-probe", null)

    override suspend fun isDenied(host: String): Boolean =
        withTimeoutOrNull(PROBE_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                val connection =
                    nw_connection_create(
                        nw_endpoint_create_host(host, PROBE_PORT),
                        nw_parameters_create_secure_udp(
                            _nw_parameters_configure_protocol_disable,
                            _nw_parameters_configure_protocol_default_configuration,
                        ),
                    )
                nw_connection_set_queue(connection, queue)
                continuation.invokeOnCancellation { nw_connection_cancel(connection) }
                nw_connection_set_state_changed_handler(connection) { state, _ ->
                    val denied =
                        when (state) {
                            nw_connection_state_ready -> {
                                false
                            }

                            nw_connection_state_waiting, nw_connection_state_failed -> {
                                nw_connection_copy_current_path(connection)?.let(::nw_path_get_unsatisfied_reason) ==
                                    nw_path_unsatisfied_reason_local_network_denied
                            }

                            else -> {
                                null
                            }
                        }
                    if (denied != null) {
                        nw_connection_cancel(connection)
                        if (continuation.isActive) continuation.resume(denied)
                    }
                }
                nw_connection_start(connection)
            }
        } ?: false

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

/** The discard port: nothing of ours listens there, and a UDP probe sends nothing anyway. */
private const val PROBE_PORT = "9"

private val PROBE_TIMEOUT = 1.seconds
