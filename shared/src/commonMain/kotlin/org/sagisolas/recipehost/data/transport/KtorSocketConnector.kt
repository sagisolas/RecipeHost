package org.sagisolas.recipehost.data.transport

import com.squareup.wire.ProtoReader
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.Buffer
import okio.IOException
import org.sagisolas.recipehost.protocol.Envelope

/** [SocketConnector] on `ktor-network` raw TCP sockets, with [EnvelopeFraming] on the wire. */
internal class KtorSocketConnector(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SocketConnector {
    override suspend fun connect(
        host: String,
        port: Int,
    ): EnvelopeConnection {
        val selector = SelectorManager(dispatcher)
        try {
            val socket = asIoErrors { aSocket(selector).tcp().connect(host, port) }
            return KtorEnvelopeConnection(selector, socket)
        } catch (e: Throwable) {
            selector.close()
            throw e
        }
    }
}

private class KtorEnvelopeConnection(
    private val selector: SelectorManager,
    private val socket: Socket,
) : EnvelopeConnection {
    private val input: ByteReadChannel = socket.openReadChannel()
    private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = false)

    override suspend fun send(envelope: Envelope) =
        asIoErrors {
            val frame = Buffer().also { EnvelopeFraming.write(it, envelope) }
            output.writeFully(frame.readByteArray())
            output.flush()
        }

    /**
     * Reads the varint length prefix a byte at a time (the channel is suspending, okio's
     * readers block), then the body, and hands the whole frame to [EnvelopeFraming.read].
     */
    override suspend fun receive(): Envelope? =
        asIoErrors {
            if (input.exhausted()) return null
            val frame = Buffer()
            do {
                if (frame.size == MAX_VARINT_BYTES) throw IOException("frame length varint too long")
                val byte = input.readByte().toInt()
                frame.writeByte(byte)
            } while (byte and 0x80 != 0)
            val length = ProtoReader(frame.copy()).readVarint32()
            if (length < 0) throw IOException("negative frame length $length")
            frame.write(input.readByteArray(length))
            EnvelopeFraming.read(frame)
        }

    override fun close() {
        socket.close()
        selector.close()
    }

    private companion object {
        const val MAX_VARINT_BYTES = 5L
    }
}

/**
 * Runs [block], reporting every failure except cancellation as [IOException]. ktor's sockets
 * throw platform types (an unresolved address, POSIX errors on iOS, closed-channel errors) that
 * [SessionClient] would otherwise not recognise as network failures.
 */
private inline fun <T> asIoErrors(block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException(e.message ?: e::class.simpleName, e)
    }
