package org.sagisolas.recipehost.mod.snapshot.icon;

import com.google.protobuf.ByteString;
import org.sagisolas.recipehost.mod.snapshot.image.PngEncoder;
import org.sagisolas.recipehost.protocol.IconBatch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Turns the Snapshot's item ids into its {@link IconBatch}es, one `Icon atlas` per rendered sheet
 * (ADR 0003, 0014), in item order. A sheet may draw fewer Icons than it has cells (its time budget
 * ran out); the rest go into the next one. PNG encoding runs on the calling (sender) thread; only
 * {@link SheetRenderer#render} touches the render thread.
 */
public final class IconBatches implements Iterator<IconBatch> {

    /** Draws a sheet of Icons from the front of {@code pending}, taking at least one. */
    @FunctionalInterface
    public interface SheetRenderer {
        IconSheet render(List<String> pending);
    }

    private final Deque<String> pending;
    private final SheetRenderer renderer;
    private final PngEncoder encoder;

    public IconBatches(List<String> itemIds, SheetRenderer renderer, PngEncoder encoder) {
        this.pending = new ArrayDeque<>(itemIds);
        this.renderer = renderer;
        this.encoder = encoder;
    }

    @Override
    public boolean hasNext() {
        return !pending.isEmpty();
    }

    @Override
    public IconBatch next() {
        if (!hasNext()) throw new NoSuchElementException();
        List<String> candidates = new ArrayList<>(Math.min(IconRenderer.CAPACITY, pending.size()));
        Iterator<String> it = pending.iterator();
        while (candidates.size() < IconRenderer.CAPACITY && it.hasNext()) candidates.add(it.next());

        IconSheet sheet = renderer.render(Collections.unmodifiableList(candidates));
        if (sheet.count() < 1) throw new IllegalStateException("an Icon sheet must draw at least one Icon");
        List<String> drawn = candidates.subList(0, sheet.count());
        for (int i = 0; i < sheet.count(); i++) pending.removeFirst();
        return IconBatch.newBuilder()
                .setPng(ByteString.copyFrom(encoder.encode(sheet.image())))
                .setCellSize(sheet.cell())
                .setColumns(sheet.columns())
                .addAllItemIds(drawn)
                .build();
    }
}
