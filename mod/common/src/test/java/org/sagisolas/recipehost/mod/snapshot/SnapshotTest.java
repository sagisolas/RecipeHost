package org.sagisolas.recipehost.mod.snapshot;

import org.junit.Test;
import org.sagisolas.recipehost.mod.snapshot.icon.IconSheet;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.Item;
import org.sagisolas.recipehost.protocol.ModInfo;
import org.sagisolas.recipehost.protocol.Recipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class SnapshotTest {

    private static Snapshot.Contents contents(int items, int recipes) {
        return new Snapshot.Contents("1.21.1", "en_us", "My pack",
                List.of(ModInfo.newBuilder().setId("minecraft").setVersion("1.21.1").build()),
                IntStream.range(0, items).mapToObj(i -> Item.newBuilder().setId("minecraft:item_" + i).build()).toList(),
                IntStream.range(0, recipes).mapToObj(i -> Recipe.newBuilder().setId("minecraft:recipe_" + i).build()).toList(),
                List.of());
    }

    private static Snapshot snapshotOf(Supplier<Snapshot.Contents> contents) {
        return new Snapshot(contents,
                // Draws a whole 8×4 sheet of 1 px cells.
                pending -> new IconSheet(8, 1, Math.min(32, pending.size()), new int[8 * 4]),
                pending -> { throw new AssertionError("no layouts expected"); });
    }

    private static <T> List<T> drain(Iterator<T> it) {
        List<T> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        return out;
    }

    @Test
    public void theHandshakeComesFirstAndAnnouncesWhatTheBatchesCarry() {
        List<Envelope> frames = drain(snapshotOf(() -> contents(300, 257)).frames());

        Handshake handshake = frames.get(0).getHandshake();
        assertEquals(300, handshake.getTotalItems());
        assertEquals(257, handshake.getTotalRecipes());
        assertEquals("My pack", handshake.getInstanceName());
        assertEquals(List.of(256, 44), frames.stream().filter(Envelope::hasItemBatch).map(f -> f.getItemBatch().getItemsCount()).toList());
        assertEquals(List.of(256, 1), frames.stream().filter(Envelope::hasRecipeBatch).map(f -> f.getRecipeBatch().getRecipesCount()).toList());
        assertEquals(List.of(Envelope.PayloadCase.HANDSHAKE, Envelope.PayloadCase.ITEM_BATCH, Envelope.PayloadCase.ITEM_BATCH,
                        Envelope.PayloadCase.RECIPE_BATCH, Envelope.PayloadCase.RECIPE_BATCH),
                frames.stream().map(Envelope::getPayloadCase).limit(5).toList());
        assertEquals(10, frames.stream().filter(Envelope::hasIconBatch).count());
        assertEquals(Envelope.PayloadCase.ICON_BATCH, frames.get(frames.size() - 1).getPayloadCase());
    }

    @Test
    public void theGameIsReadOnceAndOnlyWhenTheSnapshotIsFirstAskedFor() {
        AtomicInteger reads = new AtomicInteger();
        Snapshot snapshot = snapshotOf(() -> {
            reads.incrementAndGet();
            return contents(0, 1);
        });
        assertEquals(0, reads.get());

        drain(snapshot.frames());

        assertEquals(1, reads.get());
    }
}
