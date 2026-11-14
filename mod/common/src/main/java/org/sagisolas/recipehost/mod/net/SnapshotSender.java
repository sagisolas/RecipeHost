/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.net;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.function.Supplier;
import org.sagisolas.recipehost.protocol.Done;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.EnvelopeFraming;
import org.sagisolas.recipehost.protocol.Error;
import org.sagisolas.recipehost.protocol.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streams a Snapshot to a byte sink: the source's frames in order, then Done.
 *
 * <p>Two failures are told apart. If pulling from the source fails, the sender logs it, writes a
 * trailing Error frame and throws {@link IOException}. If writing to {@code out} fails (the client
 * went away), the sender throws {@link ClientGone}, saying how far the stream got, and writes
 * nothing more. Every frame is flushed as it is written, so the client never waits on a buffer.
 */
public final class SnapshotSender {

    private static final Logger LOG = LoggerFactory.getLogger(SnapshotSender.class);

    private SnapshotSender() {}

    public static void send(OutputStream out, SnapshotSource source) throws IOException {
        Progress progress = new Progress();
        Iterator<Envelope> frames = pull(source::frames, out);
        while (pull(frames::hasNext, out)) {
            progress.write(out, pull(frames::next, out));
        }
        progress.write(
                out, Envelope.newBuilder().setDone(Done.getDefaultInstance()).build());
    }

    /** Writes frames and remembers how far it got, for {@link ClientGone}. */
    private static final class Progress {
        private int written;
        private long previousWrite = System.nanoTime();

        void write(OutputStream out, Envelope frame) throws ClientGone {
            long silentMillis = (System.nanoTime() - previousWrite) / 1_000_000;
            try {
                EnvelopeFraming.writeFrame(out, frame);
                // Flushed per frame: a dead client is noticed at this frame, not a buffer later.
                out.flush();
            } catch (IOException ex) {
                throw new ClientGone(written, frame.getPayloadCase(), silentMillis, ex);
            }
            written++;
            previousWrite = System.nanoTime();
        }
    }

    /**
     * Writing to the client failed: it went away. Says how far the stream got, so the log shows
     * whether the client gave up while the source was slow (a long {@code silentMillis}).
     */
    static final class ClientGone extends IOException {
        ClientGone(int framesWritten, Envelope.PayloadCase failedFrame, long silentMillis, IOException cause) {
            super(
                    "%s after %d frames, writing %s, %d ms after the previous frame"
                            .formatted(cause, framesWritten, failedFrame, silentMillis),
                    cause);
        }
    }

    /** Runs one step against the source; a failure there ends the stream with an Error frame. */
    private static <T> T pull(Supplier<T> step, OutputStream out) throws IOException {
        try {
            return step.get();
        } catch (RuntimeException ex) {
            // The app only learns "source failure"; the cause is only ever seen here.
            LOG.error("[recipehost] Snapshot source failed; sending an Error frame", ex);
            Error error = Error.newBuilder().setCode(ErrorCode.SOURCE_FAILURE).build();
            EnvelopeFraming.writeFrame(
                    out, Envelope.newBuilder().setError(error).build());
            out.flush();
            throw new IOException(ex);
        }
    }
}
