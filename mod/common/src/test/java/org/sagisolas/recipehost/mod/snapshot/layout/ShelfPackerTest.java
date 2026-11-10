package org.sagisolas.recipehost.mod.snapshot.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

public class ShelfPackerTest {

    private record Box(int x, int y, int w, int h) {
        boolean overlaps(Box o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }
    }

    @Test
    public void variedSizesFitInsideTheSheetWithoutOverlap() {
        ShelfPacker packer = new ShelfPacker(100, 100);
        int[][] sizes = {{60, 30}, {30, 20}, {50, 40}, {40, 40}, {100, 10}, {10, 15}};

        List<Box> placed = new ArrayList<>();
        for (int[] size : sizes) {
            ShelfPacker.Position p = packer.place(size[0], size[1]).orElseThrow();
            placed.add(new Box(p.x(), p.y(), size[0], size[1]));
        }

        for (int i = 0; i < placed.size(); i++) {
            Box a = placed.get(i);
            assertTrue("inside the sheet: " + a, a.x >= 0 && a.y >= 0 && a.x + a.w <= 100 && a.y + a.h <= 100);
            for (int j = i + 1; j < placed.size(); j++) {
                assertFalse(a + " overlaps " + placed.get(j), a.overlaps(placed.get(j)));
            }
        }
    }

    @Test
    public void aLayoutTallerThanTheFreeSpaceIsRefusedAndFitsOnANewSheet() {
        ShelfPacker packer = new ShelfPacker(100, 100);
        packer.place(100, 70).orElseThrow();

        assertEquals(Optional.empty(), packer.place(20, 40));
        assertEquals(Optional.of(new ShelfPacker.Position(0, 0)), new ShelfPacker(100, 100).place(20, 40));
    }

    @Test
    public void aLayoutLargerThanAnEmptySheetIsRefused() {
        assertEquals(Optional.empty(), new ShelfPacker(100, 100).place(101, 10));
        assertEquals(Optional.empty(), new ShelfPacker(100, 100).place(10, 101));
    }
}
