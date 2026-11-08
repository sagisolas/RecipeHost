package org.sagisolas.recipehost.mod.net;

import org.sagisolas.recipehost.protocol.Done;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.EnvelopeFraming;
import org.sagisolas.recipehost.protocol.Error;
import org.sagisolas.recipehost.protocol.ErrorCode;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.LayoutBatch;
import org.sagisolas.recipehost.protocol.RecipeBatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

/**
 * Streams a Snapshot to a byte sink: Handshake, item batches, recipe batches,
 * icon batches, layout batches, Done. On any failure pulling from the source, writes and flushes
 * a trailing Error frame and rethrows as {@link IOException}.
 */
public final class SnapshotSender {

    private static final Logger LOG = LoggerFactory.getLogger(SnapshotSender.class);

    private SnapshotSender() {}

    public static void send(OutputStream out, SnapshotSource source) throws IOException {
        Handshake handshake = source.handshake();
        EnvelopeFraming.writeFrame(out, Envelope.newBuilder().setHandshake(handshake).build());

        try {
            drainItems(out, source.itemBatches());
            drainRecipes(out, source.recipeBatches());
            drainIcons(out, source.iconBatches());
            drainLayouts(out, source.layoutBatches());
        } catch (RuntimeException | IOException ex) {
            // The app only learns "source failure"; the cause is only ever seen here.
            LOG.error("[recipehost] Snapshot failed while streaming; sending an Error frame", ex);
            Error error = Error.newBuilder().setCode(ErrorCode.SOURCE_FAILURE).build();
            EnvelopeFraming.writeFrame(out, Envelope.newBuilder().setError(error).build());
            out.flush();
            if (ex instanceof IOException) throw (IOException) ex;
            throw new IOException(ex);
        }

        EnvelopeFraming.writeFrame(out, Envelope.newBuilder().setDone(Done.getDefaultInstance()).build());
        out.flush();
    }

    private static void drainItems(OutputStream out, Iterator<ItemBatch> it) throws IOException {
        while (it.hasNext()) {
            EnvelopeFraming.writeFrame(out, Envelope.newBuilder().setItemBatch(it.next()).build());
        }
    }

    private static void drainRecipes(OutputStream out, Iterator<RecipeBatch> it) throws IOException {
        while (it.hasNext()) {
            EnvelopeFraming.writeFrame(out, Envelope.newBuilder().setRecipeBatch(it.next()).build());
        }
    }

    private static void drainIcons(OutputStream out, Iterator<IconBatch> it) throws IOException {
        while (it.hasNext()) {
            EnvelopeFraming.writeFrame(out, Envelope.newBuilder().setIconBatch(it.next()).build());
        }
    }

    private static void drainLayouts(OutputStream out, Iterator<LayoutBatch> it) throws IOException {
        while (it.hasNext()) {
            EnvelopeFraming.writeFrame(out, Envelope.newBuilder().setLayoutBatch(it.next()).build());
        }
    }
}
