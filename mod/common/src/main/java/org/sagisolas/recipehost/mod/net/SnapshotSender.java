package org.sagisolas.recipehost.mod.net;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import org.sagisolas.recipehost.protocol.Done;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.EnvelopeFraming;
import org.sagisolas.recipehost.protocol.Error;
import org.sagisolas.recipehost.protocol.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streams a Snapshot to a byte sink: the source's frames in order, then Done. On any failure pulling
 * from the source, writes and flushes a trailing Error frame and rethrows as {@link IOException}.
 */
public final class SnapshotSender {

    private static final Logger LOG = LoggerFactory.getLogger(SnapshotSender.class);

    private SnapshotSender() {}

    public static void send(OutputStream out, SnapshotSource source) throws IOException {
        try {
            Iterator<Envelope> frames = source.frames();
            while (frames.hasNext()) {
                EnvelopeFraming.writeFrame(out, frames.next());
            }
        } catch (RuntimeException | IOException ex) {
            // The app only learns "source failure"; the cause is only ever seen here.
            LOG.error("[recipehost] Snapshot failed while streaming; sending an Error frame", ex);
            Error error = Error.newBuilder().setCode(ErrorCode.SOURCE_FAILURE).build();
            EnvelopeFraming.writeFrame(
                    out, Envelope.newBuilder().setError(error).build());
            out.flush();
            if (ex instanceof IOException) throw (IOException) ex;
            throw new IOException(ex);
        }
        EnvelopeFraming.writeFrame(
                out, Envelope.newBuilder().setDone(Done.getDefaultInstance()).build());
        out.flush();
    }
}
