package org.sagisolas.recipehost.mod.snapshot.layout;

import com.google.protobuf.ByteString;
import org.junit.Test;
import org.sagisolas.recipehost.protocol.LayoutBatch;
import org.sagisolas.recipehost.protocol.RecipeLayout;
import org.sagisolas.recipehost.protocol.RecipeSlot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.sagisolas.recipehost.mod.snapshot.image.PngEncoder;

public class LayoutBatchesTest {

    private static final String BROKEN_TYPE = "test:broken";
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final List<RecipeSlot> SLOTS = List.of(
            RecipeSlot.newBuilder().setX(1).setY(1).setWidth(2).setHeight(2).setRole(RecipeSlot.Role.INPUT).build());

    /**
     * Stands in for the GL renderer: takes up to {@code perSheet} jobs, lays them
     * left to right and fills each with the colour of its first recipe id. A job of
     * {@link #BROKEN_TYPE} fails as a throwing draw would.
     */
    private static final class FakeSheets implements LayoutBatches.SheetRenderer {
        final int perSheet;
        final Map<String, Integer> colours;
        final List<LayoutJob> rendered = new ArrayList<>();

        FakeSheets(int perSheet, Map<String, Integer> colours) {
            this.perSheet = perSheet;
            this.colours = colours;
        }

        @Override
        public LayoutSheet render(List<LayoutJob> pending) {
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
                    for (int col = 0; col < job.width(); col++)
                        argb[row * width + x + col] = colour;
                placed.add(new LayoutSheet.Placed(job, x, 0));
                x += job.width();
            }
            return new LayoutSheet(width, argb, taken.size(), placed, failed);
        }
    }

    private static LayoutJob hashed(String recipeId) {
        return LayoutJob.hashed("test:type", 4, 3, SLOTS, List.of(recipeId), (g, mx, my) -> {});
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
    public void aRepeatedKeyIsSentOnceWithItsPngThenAsRecipeIdsOnly() throws IOException {
        FakeSheets sheets = new FakeSheets(2, Map.of("r:a", RED, "r:b", BLUE, "r:c", RED));

        List<LayoutBatch> batches = drain(new LayoutBatches(
                List.of(hashed("r:a"), hashed("r:b"), hashed("r:c")), sheets, new PngEncoder()));

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

        assertEquals(RecipeLayout.newBuilder().setKey(a.getKey()).addAllRecipeIds(List.of("r:c")).build(), c);
    }

    @Test
    public void jobsSharingAParameterKeyAreDrawnOnceAndSentAsOneEntry() {
        FakeSheets sheets = new FakeSheets(1, Map.of("r:1", RED, "r:other", BLUE));

        List<LayoutBatch> batches = drain(new LayoutBatches(
                List.of(keyed("test:grid/3x3", "r:1"), hashed("r:other"), keyed("test:grid/3x3", "r:2")),
                sheets, new PngEncoder()));

        assertEquals(List.of(List.of("r:1", "r:2"), List.of("r:other")),
                sheets.rendered.stream().map(LayoutJob::recipeIds).toList());
        List<RecipeLayout> layouts = batches.stream().flatMap(batch -> batch.getLayoutsList().stream()).toList();
        assertEquals(2, layouts.size());
        assertEquals("test:grid/3x3", layouts.get(0).getKey());
        assertEquals(List.of("r:1", "r:2"), layouts.get(0).getRecipeIdsList());
        assertFalse(layouts.get(0).getPng().size() == 0);
    }

    @Test
    public void aJobThatDrawsNothingShipsWithoutALayout() {
        FakeSheets sheets = new FakeSheets(2, Map.of("r:blank", 0x00000000, "r:ok", BLUE));

        List<LayoutBatch> batches = drain(new LayoutBatches(List.of(hashed("r:blank"), hashed("r:ok")), sheets, new PngEncoder()));

        assertEquals(List.of(List.of("r:ok")),
                batches.stream().flatMap(batch -> batch.getLayoutsList().stream()).map(layout -> layout.getRecipeIdsList()).toList());
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
