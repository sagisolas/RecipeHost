package org.sagisolas.recipehost.mod.snapshot.layout;

import java.util.List;

/**
 * One read-back of {@link LayoutRenderer}'s sheet: the first {@code taken} jobs of
 * those it was given, each either placed on the sheet or failed. Plain CPU data,
 * so cropping, hashing and PNG encoding run off the render thread.
 */
public final class LayoutSheet {

    record Placed(LayoutJob job, int x, int y) {}

    record Failed(LayoutJob job, Throwable cause) {}

    private final int width;
    private final int[] argb;
    private final int taken;
    private final List<Placed> placed;
    private final List<Failed> failed;

    /**
     * @param width sheet width in px; {@code argb} holds its top-down, row-major pixels
     * @param taken how many jobs from the front of the given list this sheet consumed
     */
    LayoutSheet(int width, int[] argb, int taken, List<Placed> placed, List<Failed> failed) {
        this.width = width;
        this.argb = argb;
        this.taken = taken;
        this.placed = List.copyOf(placed);
        this.failed = List.copyOf(failed);
    }

    int taken() {
        return taken;
    }

    List<Placed> placed() {
        return placed;
    }

    List<Failed> failed() {
        return failed;
    }

    /** The placed layout's own pixels, top-down and row-major, {@code width * height} of them. */
    int[] crop(Placed layout) {
        int w = layout.job().width();
        int h = layout.job().height();
        int[] out = new int[w * h];
        for (int row = 0; row < h; row++) {
            System.arraycopy(argb, (layout.y() + row) * width + layout.x(), out, row * w, w);
        }
        return out;
    }
}
