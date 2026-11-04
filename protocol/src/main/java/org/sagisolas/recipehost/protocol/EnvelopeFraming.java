package org.sagisolas.recipehost.protocol;

import com.squareup.wire.ProtoReader;
import com.squareup.wire.ProtoWriter;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;

import java.io.EOFException;
import java.io.IOException;

/**
 * Length-prefixed framing for {@link Envelope} messages over a byte stream.
 * Each frame is a protobuf varint length followed by exactly that many bytes
 * of serialized {@code Envelope}. Byte-for-byte the same format as the Kotlin
 * {@code EnvelopeFraming} in {@code :shared}; both sides are pinned by the same
 * golden vectors in their tests.
 *
 * <p>Callers wrap a connection's streams once ({@code Okio.buffer(...)}) and
 * reuse the same source for every frame, since buffering reads ahead. Writes
 * are buffered too: flush the sink when the peer should see them.
 */
public final class EnvelopeFraming {

    private EnvelopeFraming() {}

    public static void writeFrame(BufferedSink sink, Envelope envelope) throws IOException {
        ByteString body = Envelope.ADAPTER.encodeByteString(envelope);
        new ProtoWriter(sink).writeVarint32(body.size());
        sink.write(body);
    }

    /**
     * Reads one frame from {@code source}. Returns {@code null} on clean EOF
     * (no bytes left before the length prefix); throws {@link EOFException} if
     * the stream ends mid-frame.
     */
    public static Envelope readFrame(BufferedSource source) throws IOException {
        if (source.exhausted()) return null;
        int length = new ProtoReader(source).readVarint32();
        if (length < 0) throw new IOException("negative frame length " + length);
        return Envelope.ADAPTER.decode(source.readByteString(length));
    }
}
