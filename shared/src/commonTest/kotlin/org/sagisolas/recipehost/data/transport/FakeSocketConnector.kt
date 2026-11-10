package org.sagisolas.recipehost.data.transport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import okio.IOException
import org.sagisolas.recipehost.protocol.Envelope

/**
 * A [SocketConnector] scripted per connect attempt. Each call to [connect] takes the next
 * [Attempt] in order, whatever the host, and records the host and the virtual time it was tried.
 */
@OptIn(ExperimentalCoroutinesApi::class) // TestCoroutineScheduler.currentTime
internal class FakeSocketConnector(
    private val scope: TestScope,
    vararg attempts: Attempt,
) : SocketConnector {
    sealed interface Attempt {
        /** The connect never completes (until cancelled by a timeout). */
        data object Hang : Attempt

        /** The connect fails straight away. */
        data object Refuse : Attempt

        /**
         * The connect succeeds and the mod sends [frames], then either ends the stream cleanly
         * or, with [failWith], breaks it with that I/O error.
         */
        class Accept(
            val frames: List<Envelope>,
            val failWith: IOException? = null,
            /** Virtual time the connect takes, ignoring cancellation (a connect racing its timeout). */
            val connectMillis: Long = 0,
            /** After [frames], the connection goes silent instead of ending (a half-open socket). */
            val thenHang: Boolean = false,
        ) : Attempt
    }

    data class ConnectCall(
        val host: String,
        val port: Int,
        val atMillis: Long,
    )

    private val remaining = ArrayDeque(attempts.toList())

    val connects = mutableListOf<ConnectCall>()

    /** Frames the client wrote, one list per accepted connection. */
    val written = mutableListOf<MutableList<Envelope>>()

    var closedConnections = 0
        private set

    override suspend fun connect(
        host: String,
        port: Int,
    ): EnvelopeConnection {
        connects += ConnectCall(host, port, scope.testScheduler.currentTime)
        return when (val attempt = remaining.removeFirstOrNull() ?: error("unexpected connect to $host")) {
            Attempt.Hang -> {
                awaitCancellation()
            }

            Attempt.Refuse -> {
                throw IOException("connection refused: $host")
            }

            is Attempt.Accept -> {
                if (attempt.connectMillis > 0) withContext(NonCancellable) { delay(attempt.connectMillis) }
                ScriptedConnection(attempt).also { written += it.sent }
            }
        }
    }

    private inner class ScriptedConnection(
        attempt: Attempt.Accept,
    ) : EnvelopeConnection {
        val sent = mutableListOf<Envelope>()
        private val incoming = ArrayDeque(attempt.frames)
        private val failWith = attempt.failWith
        private val thenHang = attempt.thenHang

        override suspend fun send(envelope: Envelope) {
            sent += envelope
        }

        override suspend fun receive(): Envelope? {
            incoming.removeFirstOrNull()?.let { return it }
            failWith?.let { throw it }
            if (thenHang) awaitCancellation()
            return null
        }

        override fun close() {
            closedConnections++
        }
    }
}
