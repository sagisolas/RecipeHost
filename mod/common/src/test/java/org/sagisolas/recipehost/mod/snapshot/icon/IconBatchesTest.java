/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.icon;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Test;
import org.sagisolas.recipehost.mod.snapshot.image.PngEncoder;
import org.sagisolas.recipehost.protocol.IconBatch;

public class IconBatchesTest {

    private static final int CELL = 2;
    private static final int COLUMNS = 2;

    /** Stands in for the GL renderer: draws up to {@code perSheet} Icons, as a deadline would cut a sheet short. */
    private static IconBatches.SheetRenderer drawingAtMost(int perSheet) {
        return pending -> {
            int count = Math.min(perSheet, pending.size());
            int[] argb = new int[COLUMNS * CELL * 2 * CELL];
            java.util.Arrays.fill(argb, 0xFF00FF00);
            return java.util.concurrent.CompletableFuture.completedFuture(new IconSheet(COLUMNS, CELL, count, argb));
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

        assertEquals(
                List.of(List.of("minecraft:a", "minecraft:b"), List.of("minecraft:c")),
                batches.stream().map(IconBatch::getItemIdsList).toList());
    }

    @Test
    public void eachBatchIsTheSheetAsAnAtlasCutAfterItsLastRow() throws IOException {
        IconBatch batch = drain(new IconBatches(List.of("minecraft:a"), drawingAtMost(4), new PngEncoder()))
                .get(0);
        BufferedImage atlas =
                ImageIO.read(new ByteArrayInputStream(batch.getPng().toByteArray()));

        assertEquals(CELL, batch.getCellSize());
        assertEquals(COLUMNS, batch.getColumns());
        assertEquals(COLUMNS * CELL, atlas.getWidth());
        assertEquals(CELL, atlas.getHeight());
    }

    @Test
    public void theNextSheetIsRequestedBeforeTheCurrentBatchIsHandedBack() {
        List<List<String>> requested = new ArrayList<>();
        IconBatches.SheetRenderer drawing = drawingAtMost(1);
        IconBatches batches = new IconBatches(
                List.of("minecraft:a", "minecraft:b"),
                pending -> {
                    requested.add(List.copyOf(pending));
                    return drawing.render(pending);
                },
                new PngEncoder());

        batches.next();

        assertEquals(List.of(List.of("minecraft:a", "minecraft:b"), List.of("minecraft:b")), requested);
    }

    @Test(expected = IllegalStateException.class)
    public void aSheetThatDrawsNothingIsABrokenRenderer() {
        new IconBatches(List.of("minecraft:a"), drawingAtMost(0), new PngEncoder()).next();
    }
}
