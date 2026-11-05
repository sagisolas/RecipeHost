package org.sagisolas.recipehost.mod.net;

import okio.Buffer;
import org.junit.Test;
import org.sagisolas.recipehost.protocol.Done;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.EnvelopeFraming;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.Item;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.Recipe;
import org.sagisolas.recipehost.protocol.RecipeBatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SnapshotSenderTest {

    @Test
    public void streamsHandshakeThenBatchesThenDone() throws Exception {
        Handshake handshake = new Handshake.Builder()
                .protocol_version(1)
                .minecraft_version("1.21.1")
                .locale("en_us")
                .total_items(1)
                .total_recipes(1)
                .build();

        ItemBatch items = new ItemBatch.Builder()
                .items(Collections.singletonList(
                        new Item.Builder().id("minecraft:stick").display_name("Stick").build()))
                .build();

        RecipeBatch recipes = new RecipeBatch.Builder()
                .recipes(Collections.singletonList(
                        new Recipe.Builder().type_id("minecraft:crafting_shaped").build()))
                .build();

        IconBatch icons = new IconBatch.Builder()
                .cell_size(16)
                .columns(8)
                .item_ids(Collections.singletonList("minecraft:stick"))
                .build();

        FakeSource source = new FakeSource(
                handshake,
                Collections.singletonList(items),
                Collections.singletonList(recipes),
                Collections.singletonList(icons));

        Buffer out = new Buffer();
        SnapshotSender.send(out, source);

        List<Envelope> frames = drain(out);
        assertEquals(5, frames.size());
        assertEquals(handshake, frames.get(0).handshake);
        assertEquals(items, frames.get(1).item_batch);
        assertEquals(recipes, frames.get(2).recipe_batch);
        assertEquals(icons, frames.get(3).icon_batch);
        assertNotNull("final frame must be Done", frames.get(4).done);
    }

    @Test
    public void emptySourceStillSendsHandshakeAndDone() throws Exception {
        Handshake handshake = new Handshake.Builder().protocol_version(1).build();
        FakeSource source = new FakeSource(
                handshake,
                Collections.<ItemBatch>emptyList(),
                Collections.<RecipeBatch>emptyList(),
                Collections.<IconBatch>emptyList());

        Buffer out = new Buffer();
        SnapshotSender.send(out, source);

        List<Envelope> frames = drain(out);
        assertEquals(2, frames.size());
        assertEquals(handshake, frames.get(0).handshake);
        assertNotNull(frames.get(1).done);
    }

    @Test
    public void writesErrorFrameAndRethrowsIfSourceThrows() throws Exception {
        Handshake handshake = new Handshake.Builder().protocol_version(1).build();
        SnapshotSource exploding = new SnapshotSource() {
            @Override public Handshake handshake() { return handshake; }
            @Override public Iterator<ItemBatch> itemBatches() {
                return new Iterator<ItemBatch>() {
                    @Override public boolean hasNext() { return true; }
                    @Override public ItemBatch next() { throw new RuntimeException("boom"); }
                };
            }
            @Override public Iterator<RecipeBatch> recipeBatches() { return Collections.<RecipeBatch>emptyIterator(); }
            @Override public Iterator<IconBatch> iconBatches() { return Collections.<IconBatch>emptyIterator(); }
        };

        Buffer out = new Buffer();
        try {
            SnapshotSender.send(out, exploding);
        } catch (IOException expected) {
            // ok — sender reports failure to caller after writing the Error frame.
        }

        List<Envelope> frames = drain(out);
        // Handshake must still be first; an Error must appear.
        assertEquals(handshake, frames.get(0).handshake);
        Envelope last = frames.get(frames.size() - 1);
        assertNotNull("expected trailing Error frame", last.error);
        assertNull("no Done when erroring", last.done);
    }

    private static List<Envelope> drain(Buffer out) throws IOException {
        List<Envelope> frames = new ArrayList<>();
        Envelope e;
        while ((e = EnvelopeFraming.readFrame(out)) != null) {
            frames.add(e);
        }
        return frames;
    }

    private static final class FakeSource implements SnapshotSource {
        private final Handshake handshake;
        private final List<ItemBatch> items;
        private final List<RecipeBatch> recipes;
        private final List<IconBatch> icons;

        FakeSource(Handshake h, List<ItemBatch> i, List<RecipeBatch> r, List<IconBatch> ic) {
            this.handshake = h;
            this.items = new ArrayList<>(i);
            this.recipes = new ArrayList<>(r);
            this.icons = new ArrayList<>(ic);
        }
        @Override public Handshake handshake() { return handshake; }
        @Override public Iterator<ItemBatch> itemBatches() { return items.iterator(); }
        @Override public Iterator<RecipeBatch> recipeBatches() { return recipes.iterator(); }
        @Override public Iterator<IconBatch> iconBatches() { return icons.iterator(); }
    }
}
