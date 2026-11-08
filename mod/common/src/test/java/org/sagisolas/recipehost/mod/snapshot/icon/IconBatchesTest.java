package org.sagisolas.recipehost.mod.snapshot.icon;

import org.junit.Test;
import org.sagisolas.recipehost.mod.snapshot.image.PngEncoder;
import org.sagisolas.recipehost.protocol.IconBatch;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class IconBatchesTest {

    private static final int CELL = 2;
    private static final int COLUMNS = 2;

    /** Stands in for the GL renderer: draws up to {@code perSheet} Icons, as a deadline would cut a sheet short. */
    private static IconBatches.SheetRenderer drawingAtMost(int perSheet) {
        return pending -> {
            int count = Math.min(perSheet, pending.size());
            int[] argb = new int[COLUMNS * CELL * 2 * CELL];
            java.util.Arrays.fill(argb, 0xFF00FF00);
            return new IconSheet(COLUMNS, CELL, count, argb);
        };
    }

    private static List<IconBatch> drain(IconBatches batches) {
        List<IconBatch> out = new ArrayList<>();
        batches.forEachRemaining(out::add);
        return out;
    }

    @Test
    public void aSheetCutShortLeavesTheRestForTheNextBatchInOrder() {
        List<IconBatch> batches = drain(new IconBatches(
                List.of("minecraft:a", "minecraft:b", "minecraft:c"), drawingAtMost(2), new PngEncoder()));

        assertEquals(List.of(List.of("minecraft:a", "minecraft:b"), List.of("minecraft:c")),
                batches.stream().map(IconBatch::getItemIdsList).toList());
    }

    @Test
    public void eachBatchIsTheSheetAsAnAtlasCutAfterItsLastRow() throws IOException {
        IconBatch batch = drain(new IconBatches(List.of("minecraft:a"), drawingAtMost(4), new PngEncoder())).get(0);
        BufferedImage atlas = ImageIO.read(new ByteArrayInputStream(batch.getPng().toByteArray()));

        assertEquals(CELL, batch.getCellSize());
        assertEquals(COLUMNS, batch.getColumns());
        assertEquals(COLUMNS * CELL, atlas.getWidth());
        assertEquals(CELL, atlas.getHeight());
    }

    @Test(expected = IllegalStateException.class)
    public void aSheetThatDrawsNothingIsABrokenRenderer() {
        new IconBatches(List.of("minecraft:a"), drawingAtMost(0), new PngEncoder()).next();
    }
}
