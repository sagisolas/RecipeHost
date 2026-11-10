package org.sagisolas.recipehost.mod.snapshot.layout;

import com.google.protobuf.UnsafeByteOperations;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.sagisolas.recipehost.mod.snapshot.RenderThread;
import org.sagisolas.recipehost.mod.snapshot.image.PngEncoder;
import org.sagisolas.recipehost.protocol.LayoutBatch;
import org.sagisolas.recipehost.protocol.RecipeLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns {@link LayoutJob}s into the Snapshot's {@link LayoutBatch}es, one per
 * rendered sheet. Jobs that share a parameter key are merged up front, so that key
 * is drawn once and its entry lists all their recipes. Each key is sent once with
 * its PNG; a later hashed job that draws to an already-sent key is sent as an entry
 * carrying only its {@code recipe_ids}. A job whose draw failed or drew nothing is
 * dropped, so its recipes ship without a layout; the failure is logged once per
 * {@code type_id}.
 *
 * <p>Cropping, hashing and PNG encoding run on the calling (sender) thread; only
 * {@link SheetRenderer#render} touches the render thread. The next sheet is requested as soon as
 * the current one is drawn, so it is drawn while the current one is cropped, encoded and sent.
 */
public final class LayoutBatches implements Iterator<LayoutBatch> {

    private static final Logger LOG = LoggerFactory.getLogger(LayoutBatches.class);

    /**
     * Renders one sheet from the front of {@code pending}, taking at least one job, without
     * blocking the caller; the future completes with the sheet.
     */
    @FunctionalInterface
    public interface SheetRenderer {
        CompletableFuture<LayoutSheet> render(List<LayoutJob> pending);
    }

    /** How many jobs from the front a sheet is offered: more than fit on one. */
    private static final int OFFERED_PER_SHEET = 512;

    private final List<LayoutJob> pending;
    /** How many jobs from the front of {@code pending} are drawn; the rest are still to come. */
    private int done;

    private final SheetRenderer renderer;
    private final PngEncoder encoder;
    private final Set<String> sentKeys = new HashSet<>();
    private final Set<String> loggedTypes = new HashSet<>();
    private LayoutBatch next;
    /** The sheet being drawn for the front of {@code pending}, or null when none is requested. */
    private CompletableFuture<LayoutSheet> inFlight;

    public LayoutBatches(List<LayoutJob> jobs, SheetRenderer renderer, PngEncoder encoder) {
        this.pending = mergeParameterKeys(jobs);
        this.renderer = renderer;
        this.encoder = encoder;
    }

    @Override
    public boolean hasNext() {
        while (next == null && done < pending.size()) {
            List<RecipeLayout> layouts = nextLayouts();
            if (!layouts.isEmpty())
                next = LayoutBatch.newBuilder().addAllLayouts(layouts).build();
        }
        return next != null;
    }

    @Override
    public LayoutBatch next() {
        if (!hasNext()) throw new NoSuchElementException();
        LayoutBatch batch = next;
        next = null;
        return batch;
    }

    /** The entries of the next sheet; empty if all of its jobs failed. */
    private List<RecipeLayout> nextLayouts() {
        LayoutSheet sheet = RenderThread.join(inFlight != null ? inFlight : requestSheet());
        inFlight = null;
        if (sheet.taken() < 1) throw new IllegalStateException("a layout sheet must take at least one job");
        done += sheet.taken();
        if (done < pending.size()) inFlight = requestSheet();

        sheet.failed().forEach(failed -> logOncePerType(failed.job(), failed.cause()));
        List<RecipeLayout> layouts = new ArrayList<>();
        for (LayoutSheet.Placed placed : sheet.placed()) {
            LayoutJob job = placed.job();
            int[] pixels = sheet.crop(placed);
            if (isBlank(pixels)) {
                logOncePerType(job, new IllegalStateException("the layout drew nothing"));
                continue;
            }
            String key = job.parameterKey() != null
                    ? job.parameterKey()
                    : LayoutKeys.contentHash(pixels, job.width(), job.height(), job.slots());
            layouts.add(sentKeys.add(key) ? withImage(key, job, pixels) : recipeIdsOnly(key, job));
        }
        return layouts;
    }

    /** Asks for the sheet of the jobs still to come, given as a copy the render thread can read. */
    private CompletableFuture<LayoutSheet> requestSheet() {
        return renderer.render(List.copyOf(pending.subList(done, Math.min(done + OFFERED_PER_SHEET, pending.size()))));
    }

    /**
     * The jobs in order, with each job that repeats an earlier parameter key folded
     * into that earlier job's recipe ids, so a parameter key is never drawn twice.
     */
    private static List<LayoutJob> mergeParameterKeys(List<LayoutJob> jobs) {
        List<LayoutJob> merged = new ArrayList<>(jobs.size());
        Map<String, List<String>> recipeIdsOfKey = new HashMap<>();
        for (LayoutJob job : jobs) {
            String key = job.parameterKey();
            if (key == null) {
                merged.add(job);
            } else if (recipeIdsOfKey.containsKey(key)) {
                recipeIdsOfKey.get(key).addAll(job.recipeIds());
            } else {
                recipeIdsOfKey.put(key, new ArrayList<>(job.recipeIds()));
                merged.add(job);
            }
        }
        merged.replaceAll(
                job -> job.parameterKey() == null ? job : job.withRecipeIds(recipeIdsOfKey.get(job.parameterKey())));
        return merged;
    }

    private static boolean isBlank(int[] argb) {
        for (int pixel : argb) {
            if (pixel >>> 24 != 0) return false;
        }
        return true;
    }

    private RecipeLayout withImage(String key, LayoutJob job, int[] pixels) {
        BufferedImage image = new BufferedImage(job.width(), job.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, job.width(), job.height(), pixels, 0, job.width());
        return RecipeLayout.newBuilder()
                .setKey(key)
                // The encoder's array is never touched again, so it is wrapped rather than copied.
                .setPng(UnsafeByteOperations.unsafeWrap(encoder.encode(image)))
                .setWidth(job.width())
                .setHeight(job.height())
                .addAllSlots(job.slots())
                .addAllRecipeIds(job.recipeIds())
                .build();
    }

    private static RecipeLayout recipeIdsOnly(String key, LayoutJob job) {
        return RecipeLayout.newBuilder()
                .setKey(key)
                .addAllRecipeIds(job.recipeIds())
                .build();
    }

    private void logOncePerType(LayoutJob job, Throwable cause) {
        if (loggedTypes.add(job.typeId())) {
            LOG.warn("Couldn't draw a recipe layout of {}; its recipes ship without one", job.typeId(), cause);
        }
    }
}
