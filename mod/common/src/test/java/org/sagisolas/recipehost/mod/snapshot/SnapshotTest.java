package org.sagisolas.recipehost.mod.snapshot;

import org.junit.Test;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.Item;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.ModInfo;
import org.sagisolas.recipehost.protocol.Recipe;
import org.sagisolas.recipehost.protocol.RecipeBatch;

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
                pending -> { throw new AssertionError("no Icons expected"); },
                pending -> { throw new AssertionError("no layouts expected"); });
    }

    private static <T> List<T> drain(Iterator<T> it) {
        List<T> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        return out;
    }

    @Test
    public void theHandshakeAnnouncesWhatTheBatchesCarry() {
        Snapshot snapshot = snapshotOf(() -> contents(300, 257));

        Handshake handshake = snapshot.handshake();

        assertEquals(300, handshake.getTotalItems());
        assertEquals(257, handshake.getTotalRecipes());
        assertEquals("My pack", handshake.getInstanceName());
        assertEquals(List.of(256, 44), drain(snapshot.itemBatches()).stream().map(ItemBatch::getItemsCount).toList());
        assertEquals(List.of(256, 1), drain(snapshot.recipeBatches()).stream().map(RecipeBatch::getRecipesCount).toList());
    }

    @Test
    public void theGameIsReadOnceAndOnlyWhenTheSnapshotIsFirstAskedFor() {
        AtomicInteger reads = new AtomicInteger();
        Snapshot snapshot = snapshotOf(() -> {
            reads.incrementAndGet();
            return contents(1, 1);
        });
        assertEquals(0, reads.get());

        snapshot.handshake();
        drain(snapshot.itemBatches());
        drain(snapshot.recipeBatches());

        assertEquals(1, reads.get());
    }
}
