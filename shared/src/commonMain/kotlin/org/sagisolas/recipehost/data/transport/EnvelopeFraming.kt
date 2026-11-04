package org.sagisolas.recipehost.data.transport

import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import okio.BufferedSink
import okio.BufferedSource
import okio.EOFException
import okio.IOException
import org.sagisolas.recipehost.protocol.Envelope

/**
 * Length-prefixed framing for [Envelope] messages over a byte stream: a
 * protobuf varint length followed by exactly that many bytes of serialized
 * `Envelope`. Byte-for-byte the same format as the Java `EnvelopeFraming` in
 * `:protocol`; both sides are pinned by the same golden vectors in their tests.
 */
internal object EnvelopeFraming {

    fun write(sink: BufferedSink, envelope: Envelope) {
        val body = Envelope.ADAPTER.encodeByteString(envelope)
        ProtoWriter(sink).writeVarint32(body.size)
        sink.write(body)
    }

    /**
     * Reads one frame from [source]. Returns `null` on a clean EOF (no bytes
     * left before the length prefix); throws [EOFException] if the stream ends
     * mid-frame.
     */
    fun read(source: BufferedSource): Envelope? {
        if (source.exhausted()) return null
        val length = ProtoReader(source).readVarint32()
        if (length < 0) throw IOException("negative frame length $length")
        return Envelope.ADAPTER.decode(source.readByteString(length.toLong()))
    }
}
