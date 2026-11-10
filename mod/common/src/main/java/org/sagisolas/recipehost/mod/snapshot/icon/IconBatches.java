package org.sagisolas.recipehost.mod.snapshot.icon;

import com.google.protobuf.UnsafeByteOperations;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import org.sagisolas.recipehost.mod.snapshot.RenderThread;
import org.sagisolas.recipehost.mod.snapshot.image.PngEncoder;
import org.sagisolas.recipehost.protocol.IconBatch;

/**
 * Turns the Snapshot's item ids into its {@link IconBatch}es, one `Icon atlas` per rendered sheet
 * (ADR 0003, 0014), in item order. A sheet may draw fewer Icons than it has cells (its time budget
 * ran out); the rest go into the next one. PNG encoding runs on the calling (sender) thread; only
 * {@link SheetRenderer#render} touches the render thread. The next sheet is requested as soon as
 * the current one is drawn, so it is drawn while the current one is encoded and sent.
 */
public final class IconBatches implements Iterator<IconBatch> {

    /**
     * Draws a sheet of Icons from the front of {@code pending}, taking at least one, without
     * blocking the caller; the future completes with the sheet.
     */
    @FunctionalInterface
    public interface SheetRenderer {
        CompletableFuture<IconSheet> render(List<String> pending);
    }

    private final Deque<String> pending;
    private final SheetRenderer renderer;
    private final PngEncoder encoder;
    /** The sheet being drawn for the front of {@code pending}, or null before the first is requested. */
    private CompletableFuture<IconSheet> inFlight;

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
        IconSheet sheet = RenderThread.join(inFlight != null ? inFlight : requestSheet());
        inFlight = null;
        if (sheet.count() < 1) throw new IllegalStateException("an Icon sheet must draw at least one Icon");
        List<String> drawn = new ArrayList<>(sheet.count());
        for (int i = 0; i < sheet.count(); i++) drawn.add(pending.removeFirst());
        if (!pending.isEmpty()) inFlight = requestSheet();
        return IconBatch.newBuilder()
                // The encoder's array is never touched again, so it is wrapped rather than copied.
                .setPng(UnsafeByteOperations.unsafeWrap(encoder.encode(sheet.image())))
                .setCellSize(sheet.cell())
                .setColumns(sheet.columns())
                .addAllItemIds(drawn)
                .build();
    }

    /** Asks for the sheet of the front of {@code pending}, given as a copy the render thread can read. */
    private CompletableFuture<IconSheet> requestSheet() {
        List<String> candidates = new ArrayList<>(Math.min(IconRenderer.CAPACITY, pending.size()));
        Iterator<String> it = pending.iterator();
        while (candidates.size() < IconRenderer.CAPACITY && it.hasNext()) candidates.add(it.next());
        return renderer.render(Collections.unmodifiableList(candidates));
    }
}
