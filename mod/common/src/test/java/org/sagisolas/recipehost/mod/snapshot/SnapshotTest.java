package org.sagisolas.recipehost.mod.snapshot;

import org.junit.Test;
import org.sagisolas.recipehost.mod.snapshot.icon.IconSheet;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.mod.snapshot.recipe.LaidOut;
import org.sagisolas.recipehost.mod.snapshot.recipe.RecipeAssembly;
import org.sagisolas.recipehost.mod.snapshot.recipe.RecipeCandidate;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.Item;
import org.sagisolas.recipehost.protocol.ModInfo;
import org.sagisolas.recipehost.protocol.Recipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class SnapshotTest {

    /** Recipe ids laid out so far, in order. */
    private final List<String> laidOut = new ArrayList<>();

    private Snapshot.Contents contents(int items, int recipes) {
        List<RecipeCandidate> candidates = IntStream.range(0, recipes).<RecipeCandidate>mapToObj(i -> new RecipeCandidate() {
            @Override public String typeId() { return "minecraft:crafting"; }
            @Override public Recipe read() { return Recipe.newBuilder().setId("minecraft:recipe_" + i).build(); }
            @Override public Optional<LaidOut> layOut(Recipe read) {
                laidOut.add(read.getId());
                return Optional.empty();
            }
        }).toList();
        return new Snapshot.Contents("1.21.1", "en_us", "My pack",
                List.of(ModInfo.newBuilder().setId("minecraft").setVersion("1.21.1").build()),
                IntStream.range(0, items).mapToObj(i -> Item.newBuilder().setId("minecraft:item_" + i).build()).toList(),
                new RecipeAssembly(new DirectRenderThread()).read(candidates));
    }

    private Snapshot snapshotOf(Supplier<Snapshot.Contents> contents) {
        return new Snapshot(contents,
                // Draws a whole 8×4 sheet of 1 px cells.
                pending -> java.util.concurrent.CompletableFuture.completedFuture(new IconSheet(8, 1, Math.min(32, pending.size()), new int[8 * 4])),
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
    public void theHandshakeGoesOutBeforeAnyLayoutIsCapturedAndEachBatchCapturesOnlyItsOwn() {
        Iterator<Envelope> frames = snapshotOf(() -> contents(0, 300)).frames();

        assertEquals(300, frames.next().getHandshake().getTotalRecipes());
        assertEquals(0, laidOut.size());
        assertEquals(256, frames.next().getRecipeBatch().getRecipesCount());
        assertEquals(256, laidOut.size());
    }

    @Test
    public void aLaterSessionOffersOnlyItsOwnLayoutsToTheRenderer() {
        List<Integer> offered = new ArrayList<>();
        Snapshot.Contents withOneLayout = new Snapshot.Contents("1.21.1", "en_us", "My pack", List.of(), List.of(),
                new RecipeAssembly(new DirectRenderThread()).read(List.of(new RecipeCandidate() {
                    @Override public String typeId() { return "minecraft:crafting"; }
                    @Override public Recipe read() { return Recipe.newBuilder().setId("minecraft:stick").build(); }
                    @Override public Optional<LaidOut> layOut(Recipe read) {
                        return Optional.of(new LaidOut(LayoutJob.hashed("minecraft:crafting", 8, 8, List.of(), List.of(read.getId()), (g, x, y) -> {}), List.of()));
                    }
                })));
        Snapshot snapshot = new Snapshot(() -> withOneLayout,
                pending -> { throw new AssertionError("no Icons expected"); },
                pending -> {
                    offered.add(pending.size());
                    return CompletableFuture.failedFuture(new IllegalStateException("stops the session"));
                });

        for (int session = 0; session < 2; session++) {
            try {
                drain(snapshot.frames());
            } catch (IllegalStateException expected) {
                // The renderer stops each session at its first layout sheet.
            }
        }

        assertEquals(List.of(1, 1), offered);
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
