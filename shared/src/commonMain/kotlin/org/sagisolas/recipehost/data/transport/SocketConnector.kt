package org.sagisolas.recipehost.data.transport

import okio.IOException
import org.sagisolas.recipehost.protocol.Envelope

/** Opens TCP connections to the companion mod's listener that carry framed [Envelope]s. */
internal fun interface SocketConnector {
    /**
     * Connects to [host]:[port]. Throws [IOException] if the host refuses or can't be reached;
     * callers bound the wait with their own timeout, so this must stay cancellable.
     */
    suspend fun connect(host: String, port: Int): EnvelopeConnection
}

/** One open connection, already framing [Envelope]s in both directions. */
internal interface EnvelopeConnection {
    /** Writes and flushes one frame. Throws [IOException] if the connection is broken. */
    suspend fun send(envelope: Envelope)

    /**
     * Reads the next frame, or returns `null` on a clean EOF. Throws [IOException] on a broken
     * connection, a frame cut short, or bytes that don't decode.
     */
    suspend fun receive(): Envelope?

    fun close()
}
