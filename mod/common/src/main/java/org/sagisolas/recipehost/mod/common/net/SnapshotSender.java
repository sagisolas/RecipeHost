package org.sagisolas.recipehost.mod.common.net;

import org.sagisolas.recipehost.protocol.Done;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.Error;
import org.sagisolas.recipehost.protocol.ErrorCode;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.RecipeBatch;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

/**
 * Streams a Snapshot to a byte sink: Handshake, item batches, recipe batches,
 * icon batches, Done. On any failure pulling from the source, writes a trailing
 * Error frame and rethrows as {@link IOException}.
 */
public final class SnapshotSender {

    private SnapshotSender() {}

    public static void send(OutputStream out, SnapshotSource source) throws IOException {
        Handshake handshake = source.handshake();
        EnvelopeFraming.writeFrame(out, new Envelope.Builder().handshake(handshake).build());

        try {
            drainItems(out, source.itemBatches());
            drainRecipes(out, source.recipeBatches());
            drainIcons(out, source.iconBatches());
        } catch (RuntimeException | IOException ex) {
            Error error = new Error.Builder().code(ErrorCode.SOURCE_FAILURE).build();
            EnvelopeFraming.writeFrame(out, new Envelope.Builder().error(error).build());
            if (ex instanceof IOException) throw (IOException) ex;
            throw new IOException(ex);
        }

        EnvelopeFraming.writeFrame(out, new Envelope.Builder().done(new Done.Builder().build()).build());
        out.flush();
    }

    private static void drainItems(OutputStream out, Iterator<ItemBatch> it) throws IOException {
        while (it.hasNext()) {
            EnvelopeFraming.writeFrame(out, new Envelope.Builder().item_batch(it.next()).build());
        }
    }

    private static void drainRecipes(OutputStream out, Iterator<RecipeBatch> it) throws IOException {
        while (it.hasNext()) {
            EnvelopeFraming.writeFrame(out, new Envelope.Builder().recipe_batch(it.next()).build());
        }
    }

    private static void drainIcons(OutputStream out, Iterator<IconBatch> it) throws IOException {
        while (it.hasNext()) {
            EnvelopeFraming.writeFrame(out, new Envelope.Builder().icon_batch(it.next()).build());
        }
    }
}
