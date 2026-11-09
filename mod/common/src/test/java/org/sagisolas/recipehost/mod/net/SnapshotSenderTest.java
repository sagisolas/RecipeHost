package org.sagisolas.recipehost.mod.net;

import org.junit.Test;
import org.sagisolas.recipehost.protocol.Done;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.EnvelopeFraming;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.Item;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.LayoutBatch;
import org.sagisolas.recipehost.protocol.Recipe;
import org.sagisolas.recipehost.protocol.RecipeBatch;
import org.sagisolas.recipehost.protocol.RecipeLayout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SnapshotSenderTest {

    @Test
    public void streamsTheSourcesFramesInOrderThenDone() throws Exception {
        Handshake handshake = Handshake.newBuilder()
                .setProtocolVersion(1)
                .setMinecraftVersion("1.21.1")
                .setLocale("en_us")
                .setTotalItems(1)
                .setTotalRecipes(1)
                .build();

        ItemBatch items = ItemBatch.newBuilder()
                .addAllItems(Collections.singletonList(
                        Item.newBuilder().setId("minecraft:stick").setDisplayName("Stick").build()))
                .build();

        RecipeBatch recipes = RecipeBatch.newBuilder()
                .addAllRecipes(Collections.singletonList(
                        Recipe.newBuilder().setTypeId("minecraft:crafting_shaped").build()))
                .build();

        IconBatch icons = IconBatch.newBuilder()
                .setCellSize(16)
                .setColumns(8)
                .addAllItemIds(Collections.singletonList("minecraft:stick"))
                .build();

        LayoutBatch layouts = LayoutBatch.newBuilder()
                .addAllLayouts(Collections.singletonList(RecipeLayout.newBuilder()
                        .setKey("minecraft:crafting_shaped/3x3")
                        .addAllRecipeIds(Collections.singletonList("minecraft:stick"))
                        .build()))
                .build();

        SnapshotSource source = framesOf(
                Envelope.newBuilder().setHandshake(handshake).build(),
                Envelope.newBuilder().setItemBatch(items).build(),
                Envelope.newBuilder().setRecipeBatch(recipes).build(),
                Envelope.newBuilder().setIconBatch(icons).build(),
                Envelope.newBuilder().setLayoutBatch(layouts).build());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SnapshotSender.send(out, source);

        List<Envelope> frames = drain(out);
        assertEquals(6, frames.size());
        assertEquals(handshake, frames.get(0).getHandshake());
        assertEquals(items, frames.get(1).getItemBatch());
        assertEquals(recipes, frames.get(2).getRecipeBatch());
        assertEquals(icons, frames.get(3).getIconBatch());
        assertEquals(layouts, frames.get(4).getLayoutBatch());
        assertTrue("final frame must be Done", frames.get(5).hasDone());
    }

    @Test
    public void aSnapshotWithNoBatchesIsHandshakeThenDone() throws Exception {
        Handshake handshake = Handshake.newBuilder().setProtocolVersion(1).build();
        SnapshotSource source = framesOf(Envelope.newBuilder().setHandshake(handshake).build());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SnapshotSender.send(out, source);

        List<Envelope> frames = drain(out);
        assertEquals(2, frames.size());
        assertEquals(handshake, frames.get(0).getHandshake());
        assertTrue(frames.get(1).hasDone());
    }

    @Test
    public void writesErrorFrameAndRethrowsIfSourceThrows() throws Exception {
        Handshake handshake = Handshake.newBuilder().setProtocolVersion(1).build();
        SnapshotSource exploding = () -> new Iterator<>() {
            boolean sentHandshake;
            @Override public boolean hasNext() { return true; }
            @Override public Envelope next() {
                if (sentHandshake) throw new RuntimeException("boom");
                sentHandshake = true;
                return Envelope.newBuilder().setHandshake(handshake).build();
            }
        };

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            SnapshotSender.send(out, exploding);
        } catch (IOException expected) {
            // ok — sender reports failure to caller after writing the Error frame.
        }

        List<Envelope> frames = drain(out);
        // Handshake must still be first; an Error must appear.
        assertEquals(handshake, frames.get(0).getHandshake());
        Envelope last = frames.get(frames.size() - 1);
        assertTrue("expected trailing Error frame", last.hasError());
        assertFalse("no Done when erroring", last.hasDone());
    }

    private static List<Envelope> drain(ByteArrayOutputStream out) throws IOException {
        List<Envelope> frames = new ArrayList<>();
        InputStream in = new ByteArrayInputStream(out.toByteArray());
        Envelope e;
        while ((e = EnvelopeFraming.readFrame(in)) != null) {
            frames.add(e);
        }
        return frames;
    }

    private static SnapshotSource framesOf(Envelope... frames) {
        return () -> Arrays.asList(frames).iterator();
    }
}
