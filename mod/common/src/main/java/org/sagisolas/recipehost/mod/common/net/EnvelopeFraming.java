package org.sagisolas.recipehost.mod.common.net;

import org.sagisolas.recipehost.protocol.Envelope;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Length-prefixed framing for {@link Envelope} messages over a byte stream.
 * Each frame is a protobuf varint length followed by exactly that many bytes
 * of serialized {@code Envelope}. Matches the on-wire conventions used by
 * {@code writeDelimitedTo}/{@code parseDelimitedFrom} in other protobuf libs.
 */
public final class EnvelopeFraming {

    private EnvelopeFraming() {}

    public static void writeFrame(OutputStream out, Envelope envelope) throws IOException {
        byte[] body = Envelope.ADAPTER.encode(envelope);
        writeVarint(out, body.length);
        out.write(body);
    }

    /**
     * Reads one frame from {@code in}. Returns {@code null} on clean EOF
     * (no bytes available at all); throws {@link EOFException} if the stream
     * ends mid-frame.
     */
    public static Envelope readFrame(InputStream in) throws IOException {
        int length = readVarintOrEof(in);
        if (length < 0) return null;
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) throw new EOFException("stream ended after " + read + " of " + length + " body bytes");
            read += n;
        }
        return Envelope.ADAPTER.decode(body);
    }

    private static void writeVarint(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
    }

    /** Returns -1 if the stream is cleanly exhausted before any varint byte. */
    private static int readVarintOrEof(InputStream in) throws IOException {
        int first = in.read();
        if (first < 0) return -1;
        int result = first & 0x7F;
        int shift = 7;
        int b = first;
        while ((b & 0x80) != 0) {
            b = in.read();
            if (b < 0) throw new EOFException("stream ended inside varint length prefix");
            result |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new IOException("varint length prefix too long");
        }
        return result;
    }
}
