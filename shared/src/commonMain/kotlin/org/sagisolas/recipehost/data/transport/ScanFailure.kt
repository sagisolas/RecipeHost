package org.sagisolas.recipehost.data.transport

import org.sagisolas.recipehost.protocol.ErrorCode

/** Why a `Session` ended without a full `Snapshot`. Thrown from [SessionSource.open]'s flow. */
internal sealed class ScanFailure(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** The connection failed. Worth one retry: the listener stays up after a drop (ADR 0001). */
    sealed class Network(
        message: String,
        cause: Throwable? = null,
    ) : ScanFailure(message, cause) {
        /** No host in the payload accepted a connection within its timeout. */
        class Unreachable(
            val hosts: List<String>,
        ) : Network("no host reachable: ${hosts.joinToString()}")

        /** No host accepted a connection, and the OS denies this app the local network (iOS). */
        class LocalNetworkDenied(
            val hosts: List<String>,
        ) : Network("local network access denied: ${hosts.joinToString()}")

        /** The connection broke, or ended before `Done`. */
        class Dropped(
            cause: Throwable? = null,
        ) : Network("connection dropped before Done", cause)
    }

    /** The mod refused or the peers disagree. Retrying with the same payload can't help. */
    sealed class Domain(
        message: String,
    ) : ScanFailure(message) {
        /** The mod sent `Error{code}` (`UNAUTHORIZED`, `BUSY` or `SOURCE_FAILURE`). */
        class ModError(
            val code: ErrorCode,
        ) : Domain("companion mod reported $code")

        /** The `Handshake` names a wire protocol version this app doesn't speak. */
        class UnsupportedProtocolVersion(
            val version: Int,
        ) : Domain("unsupported protocol version $version")
    }
}
