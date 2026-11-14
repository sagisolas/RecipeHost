/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.protobuf.ByteString;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.Test;
import org.sagisolas.recipehost.mod.snapshot.image.PngEncoder;
import org.sagisolas.recipehost.protocol.LayoutBatch;
import org.sagisolas.recipehost.protocol.LayoutPatch;
import org.sagisolas.recipehost.protocol.RecipeLayout;
import org.sagisolas.recipehost.protocol.RecipeSlot;

public class LayoutBatchesTest {

    private static final String BROKEN_TYPE = "test:broken";
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final List<RecipeSlot> SLOTS = List.of(RecipeSlot.newBuilder()
            .setX(1)
            .setY(1)
            .setWidth(2)
            .setHeight(2)
            .setRole(RecipeSlot.Role.INPUT)
            .build());

    /** One pixel a job draws over its fill colour. */
    private record Dot(int x, int y, int colour) {}

    /**
     * Stands in for the GL renderer: takes up to {@code perSheet} jobs, lays them
     * left to right and fills each with the colour of its first recipe id, then
     * draws that recipe id's dot if it has one. A job of {@link #BROKEN_TYPE} fails
     * as a throwing draw would.
     */
    private static final class FakeSheets implements LayoutBatches.SheetRenderer {
        final int perSheet;
        final Map<String, Integer> colours;
        final Map<String, Dot> dots;
        final List<LayoutJob> rendered = new ArrayList<>();

        FakeSheets(int perSheet, Map<String, Integer> colours) {
            this(perSheet, colours, Map.of());
        }

        FakeSheets(int perSheet, Map<String, Integer> colours, Map<String, Dot> dots) {
            this.perSheet = perSheet;
            this.colours = colours;
            this.dots = dots;
        }

        @Override
        public java.util.concurrent.CompletableFuture<LayoutSheet> render(List<LayoutJob> pending) {
            requests++;
            return java.util.concurrent.CompletableFuture.completedFuture(draw(pending));
        }

        int requests;

        private LayoutSheet draw(List<LayoutJob> pending) {
            List<LayoutJob> taken = pending.subList(0, Math.min(perSheet, pending.size()));
            int width = taken.stream().mapToInt(LayoutJob::width).sum();
            int height = taken.stream().mapToInt(LayoutJob::height).max().orElse(0);
            int[] argb = new int[width * height];
            List<LayoutSheet.Placed> placed = new ArrayList<>();
            List<LayoutSheet.Failed> failed = new ArrayList<>();
            int x = 0;
            for (LayoutJob job : taken) {
                rendered.add(job);
                if (job.typeId().equals(BROKEN_TYPE)) {
                    failed.add(new LayoutSheet.Failed(job, new RuntimeException("draw threw")));
                    continue;
                }
                int colour = colours.get(job.recipeIds().get(0));
                for (int row = 0; row < job.height(); row++)
                    for (int col = 0; col < job.width(); col++) argb[row * width + x + col] = colour;
                Dot dot = dots.get(job.recipeIds().get(0));
                if (dot != null) argb[dot.y() * width + x + dot.x()] = dot.colour();
                placed.add(new LayoutSheet.Placed(job, x, 0));
                x += job.width();
            }
            return new LayoutSheet(width, argb, taken.size(), placed, failed);
        }
    }

    private static LayoutJob hashed(String recipeId) {
        return LayoutJob.hashed("test:type", 4, 3, SLOTS, List.of(recipeId), (g, mx, my) -> {});
    }

    /** A hashed job of another type than {@link #hashed}, with the same size and slots. */
    private static LayoutJob hashedOtherType(String recipeId) {
        return LayoutJob.hashed("test:other", 4, 3, SLOTS, List.of(recipeId), (g, mx, my) -> {});
    }

    /** A hashed job 20 x 10 px: three columns and two rows of 8 px tiles, the last ones cut. */
    private static LayoutJob wide(String recipeId) {
        return LayoutJob.hashed("test:type", 20, 10, SLOTS, List.of(recipeId), (g, mx, my) -> {});
    }

    private static List<RecipeLayout> layoutsOf(List<LayoutBatch> batches) {
        return batches.stream()
                .flatMap(batch -> batch.getLayoutsList().stream())
                .toList();
    }

    private static BufferedImage imageOf(ByteString png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png.toByteArray()));
    }

    private static LayoutJob keyed(String key, String recipeId) {
        return LayoutJob.keyed(key, "test:type", 4, 3, SLOTS, List.of(recipeId), (g, mx, my) -> {});
    }

    private static List<LayoutBatch> drain(LayoutBatches batches) {
        List<LayoutBatch> out = new ArrayList<>();
        batches.forEachRemaining(out::add);
        return out;
    }

    private static int pixelOf(ByteString png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png.toByteArray()));
        assertEquals(4, image.getWidth());
        assertEquals(3, image.getHeight());
        return image.getRGB(3, 2);
    }

    @Test
    public void theNextSheetIsRequestedBeforeTheCurrentBatchIsHandedBack() {
        FakeSheets sheets = new FakeSheets(1, Map.of("r:a", RED, "r:b", BLUE));
        LayoutBatches batches = new LayoutBatches(List.of(hashed("r:a"), hashed("r:b")), sheets, new PngEncoder());

        batches.next();

        assertEquals(2, sheets.requests);
    }

    @Test
    public void aRepeatedKeyIsSentOnceWithItsPngThenAsRecipeIdsOnly() throws IOException {
        FakeSheets sheets = new FakeSheets(2, Map.of("r:a", RED, "r:b", BLUE, "r:c", RED));

        List<LayoutBatch> batches = drain(new LayoutBatches(
                List.of(hashed("r:a"), hashedOtherType("r:b"), hashed("r:c")), sheets, new PngEncoder()));

        assertEquals(2, batches.size());
        RecipeLayout a = batches.get(0).getLayoutsList().get(0);
        RecipeLayout b = batches.get(0).getLayoutsList().get(1);
        RecipeLayout c = batches.get(1).getLayoutsList().get(0);

        assertEquals(List.of("r:a"), a.getRecipeIdsList());
        assertEquals(4, a.getWidth());
        assertEquals(3, a.getHeight());
        assertEquals(SLOTS, a.getSlotsList());
        assertEquals(RED, pixelOf(a.getPng()));
        assertEquals(BLUE, pixelOf(b.getPng()));
        assertFalse("different pixels, different keys", a.getKey().equals(b.getKey()));

        assertEquals(
                RecipeLayout.newBuilder()
                        .setKey(a.getKey())
                        .addAllRecipeIds(List.of("r:c"))
                        .build(),
                c);
    }

    @Test
    public void aJobLikeAnEarlierOneOfItsTypeIsSentAsThatLayoutWithAPatchOfTheTilesThatDiffer() throws IOException {
        FakeSheets sheets =
                new FakeSheets(2, Map.of("r:base", RED, "r:dot", RED), Map.of("r:dot", new Dot(17, 9, BLUE)));

        List<RecipeLayout> layouts =
                layoutsOf(drain(new LayoutBatches(List.of(wide("r:base"), wide("r:dot")), sheets, new PngEncoder())));

        RecipeLayout base = layouts.get(0);
        RecipeLayout dot = layouts.get(1);
        assertFalse(base.hasPatch());
        assertEquals(base.getKey(), dot.getKey());
        assertEquals(List.of("r:dot"), dot.getRecipeIdsList());
        assertEquals(0, dot.getPng().size());
        // (17, 9) is in the third column and second row of tiles.
        assertEquals(List.of(5), dot.getPatch().getTilesList());
        BufferedImage strip = imageOf(dot.getPatch().getPng());
        assertEquals(8, strip.getWidth());
        assertEquals(8, strip.getHeight());
        assertEquals(BLUE, strip.getRGB(1, 1));
        assertEquals(RED, strip.getRGB(0, 0));
        assertEquals("past the layout's edge", 0, strip.getRGB(4, 0) >>> 24);
        assertEquals("past the layout's edge", 0, strip.getRGB(0, 2) >>> 24);
    }

    @Test
    public void aJobDrawnLikeItsTypesLayoutIsSentAsThatLayoutAlone() {
        FakeSheets sheets = new FakeSheets(2, Map.of("r:base", RED, "r:same", RED));

        List<RecipeLayout> layouts =
                layoutsOf(drain(new LayoutBatches(List.of(wide("r:base"), wide("r:same")), sheets, new PngEncoder())));

        assertEquals(
                RecipeLayout.newBuilder()
                        .setKey(layouts.get(0).getKey())
                        .addRecipeIds("r:same")
                        .build(),
                layouts.get(1));
    }

    @Test
    public void aRepeatedPatchIsSentOnceThenByKeyOnly() {
        Dot dot = new Dot(3, 3, BLUE);
        FakeSheets sheets = new FakeSheets(
                3, Map.of("r:base", RED, "r:one", RED, "r:two", RED), Map.of("r:one", dot, "r:two", dot));

        List<RecipeLayout> layouts = layoutsOf(drain(
                new LayoutBatches(List.of(wide("r:base"), wide("r:one"), wide("r:two")), sheets, new PngEncoder())));

        LayoutPatch first = layouts.get(1).getPatch();
        assertFalse(first.getPng().isEmpty());
        assertEquals(
                LayoutPatch.newBuilder().setKey(first.getKey()).build(),
                layouts.get(2).getPatch());
    }

    @Test
    public void jobsSharingAParameterKeyAreDrawnOnceAndSentAsOneEntry() {
        FakeSheets sheets = new FakeSheets(1, Map.of("r:1", RED, "r:other", BLUE));

        List<LayoutBatch> batches = drain(new LayoutBatches(
                List.of(keyed("test:grid/3x3", "r:1"), hashed("r:other"), keyed("test:grid/3x3", "r:2")),
                sheets,
                new PngEncoder()));

        assertEquals(
                List.of(List.of("r:1", "r:2"), List.of("r:other")),
                sheets.rendered.stream().map(LayoutJob::recipeIds).toList());
        List<RecipeLayout> layouts = batches.stream()
                .flatMap(batch -> batch.getLayoutsList().stream())
                .toList();
        assertEquals(2, layouts.size());
        assertEquals("test:grid/3x3", layouts.get(0).getKey());
        assertEquals(List.of("r:1", "r:2"), layouts.get(0).getRecipeIdsList());
        assertFalse(layouts.get(0).getPng().size() == 0);
    }

    @Test
    public void aJobThatDrawsNothingShipsWithoutALayout() {
        FakeSheets sheets = new FakeSheets(2, Map.of("r:blank", 0x00000000, "r:ok", BLUE));

        List<LayoutBatch> batches =
                drain(new LayoutBatches(List.of(hashed("r:blank"), hashed("r:ok")), sheets, new PngEncoder()));

        assertEquals(
                List.of(List.of("r:ok")),
                batches.stream()
                        .flatMap(batch -> batch.getLayoutsList().stream())
                        .map(layout -> layout.getRecipeIdsList())
                        .toList());
    }

    @Test
    public void aFailedJobsRecipesShipWithoutALayout() {
        LayoutJob broken = LayoutJob.hashed(BROKEN_TYPE, 4, 3, SLOTS, List.of("r:broken"), (g, mx, my) -> {});
        FakeSheets sheets = new FakeSheets(2, Map.of("r:ok", BLUE));

        List<LayoutBatch> batches = drain(new LayoutBatches(List.of(broken, hashed("r:ok")), sheets, new PngEncoder()));

        List<String> ids = batches.stream()
                .flatMap(batch -> batch.getLayoutsList().stream())
                .flatMap(layout -> layout.getRecipeIdsList().stream())
                .toList();
        assertEquals(List.of("r:ok"), ids);
    }

    @Test
    public void aSheetWhoseJobsAllFailSendsNoBatch() {
        LayoutJob broken = LayoutJob.hashed(BROKEN_TYPE, 4, 3, SLOTS, List.of("r:broken"), (g, mx, my) -> {});

        LayoutBatches batches = new LayoutBatches(List.of(broken), new FakeSheets(1, Map.of()), new PngEncoder());

        assertFalse(batches.hasNext());
    }
}
