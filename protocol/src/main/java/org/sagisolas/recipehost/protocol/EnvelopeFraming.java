/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Length-prefixed framing for {@link Envelope} messages over a byte stream.
 * Each frame is a protobuf varint length followed by exactly that many bytes
 * of serialized {@code Envelope}. Byte-for-byte the same format as the Kotlin
 * {@code EnvelopeFraming} in {@code :shared}; both sides are pinned by the same
 * golden vectors in their tests.
 *
 * <p>Callers wrap a connection's streams once (in a {@code BufferedInputStream} /
 * {@code BufferedOutputStream}) and reuse them for every frame. Writes are
 * buffered: flush the stream when the peer should see them.
 */
public final class EnvelopeFraming {

    /** A varint32 takes at most 5 bytes. */
    private static final int MAX_VARINT32_BYTES = 5;

    private EnvelopeFraming() {}

    public static void writeFrame(OutputStream out, Envelope envelope) throws IOException {
        envelope.writeDelimitedTo(out);
    }

    /**
     * Reads one frame from {@code in}. Returns {@code null} on clean EOF
     * (no bytes left before the length prefix); throws {@link EOFException} if
     * the stream ends mid-frame.
     */
    public static Envelope readFrame(InputStream in) throws IOException {
        int first = in.read();
        if (first < 0) return null;
        int length = readVarint32(first, in);
        if (length < 0) throw new IOException("negative frame length " + length);
        byte[] body = in.readNBytes(length);
        if (body.length < length) throw new EOFException("stream ended inside a " + length + "-byte frame");
        return Envelope.parseFrom(body);
    }

    /** The rest of a varint32 whose first byte is {@code first}; bits past 32 are dropped, as protobuf does. */
    private static int readVarint32(int first, InputStream in) throws IOException {
        int value = first & 0x7f;
        int b = first;
        for (int i = 1; (b & 0x80) != 0; i++) {
            if (i >= MAX_VARINT32_BYTES) throw new IOException("malformed varint length prefix");
            b = in.read();
            if (b < 0) throw new EOFException("stream ended inside a length prefix");
            value |= (b & 0x7f) << (7 * i);
        }
        return value;
    }
}
