package org.sagisolas.recipehost.mod.snapshot.recipe;

import com.mojang.logging.LogUtils;
import org.sagisolas.recipehost.mod.snapshot.RenderThread;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.protocol.Recipe;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns {@link RecipeCandidate}s into a Snapshot's recipes. It owns the rules every recipe source
 * shares (ADR 0008, 0011, 0015):
 * <ul>
 *   <li>tag listings and fuel aren't recipes, so their types are skipped;</li>
 *   <li>a recipe that fails to read is left out, and one whose layout fails ships without it;</li>
 *   <li>a Snapshot names each recipe once: of recipes sharing an id, the first is kept;</li>
 *   <li>the namespace of a recipe's id owns it;</li>
 *   <li>each layout lists the recipe it was laid out for.</li>
 * </ul>
 * Each kind of failure is logged once per type. Layouts are laid out on the {@link RenderThread}
 * a budgeted slice at a time.
 */
public final class RecipeAssembly {

    private static final Logger LOG = LogUtils.getLogger();
    /**
     * Types that aren't recipes: {@code emi:tag} lists a tag's members, which the Snapshot already
     * expands into every Slot, and {@code emi:fuel} lists burn times.
     */
    private static final Set<String> SKIPPED_TYPES = Set.of("emi:tag", "emi:fuel");
    /** JEI's tag listings, one type per registry ({@code minecraft:tag_recipes/item}, ...). */
    private static final String JEI_TAG_TYPE_PREFIX = "minecraft:tag_recipes/";

    /** The Snapshot's recipes in order, the jobs of those with a layout, and the namespaces owning them. */
    public record Assembled(List<Recipe> recipes, List<LayoutJob> layouts, Set<String> namespaces) {
        public Assembled {
            recipes = List.copyOf(recipes);
            layouts = List.copyOf(layouts);
            namespaces = Set.copyOf(namespaces);
        }
    }

    private final RenderThread renderThread;
    private final Set<String> failedReads = new HashSet<>();
    private final Set<String> failedLayouts = new HashSet<>();
    private final Set<String> repeatingTypes = new HashSet<>();

    public RecipeAssembly(RenderThread renderThread) {
        this.renderThread = renderThread;
    }

    public Assembled assemble(List<? extends RecipeCandidate> candidates) {
        List<Read> read = readAll(candidates);
        List<Optional<LaidOut>> laidOut = layOutAll(read);
        List<Recipe> recipes = new ArrayList<>(read.size());
        List<LayoutJob> layouts = new ArrayList<>();
        Set<String> namespaces = new HashSet<>();
        for (int i = 0; i < read.size(); i++) {
            Recipe recipe = read.get(i).recipe();
            Optional<LaidOut> layout = laidOut.get(i);
            recipes.add(layout.map(l -> recipe.toBuilder().addAllLayoutStacks(l.stacks()).build()).orElse(recipe));
            layout.ifPresent(l -> layouts.add(l.job()));
            namespaces.add(namespaceOf(recipe.getId()));
        }
        return new Assembled(recipes, layouts, namespaces);
    }

    /** A candidate that read, with what it read. */
    private record Read(RecipeCandidate candidate, Recipe recipe) {}

    private List<Read> readAll(List<? extends RecipeCandidate> candidates) {
        List<Read> read = new ArrayList<>(candidates.size());
        Set<String> ids = new HashSet<>();
        for (RecipeCandidate candidate : candidates) {
            String typeId = candidate.typeId();
            if (isSkipped(typeId)) continue;
            Recipe recipe;
            try {
                recipe = candidate.read();
            } catch (RuntimeException | LinkageError e) {
                // LinkageError: a mod's recipe compiled against an API that isn't there.
                if (failedReads.add(typeId)) {
                    LOG.warn("[recipehost] skipping recipes of {} that fail to read (logged once per type)", typeId, e);
                }
                continue;
            }
            // Two recipes can share an id: a mod registered both under one, or two recipes without
            // one have the same body once fluids are left out.
            if (!ids.add(recipe.getId())) {
                if (repeatingTypes.add(typeId)) {
                    LOG.warn("[recipehost] recipes of {} repeat the id {}; only the first is sent (logged once per type)",
                            typeId, recipe.getId());
                }
                continue;
            }
            read.add(new Read(candidate, recipe));
        }
        return read;
    }

    /** Lays out every read recipe on the render thread, where mods expect it, a budgeted slice at a time. */
    private List<Optional<LaidOut>> layOutAll(List<Read> read) {
        List<Optional<LaidOut>> laidOut = new ArrayList<>(read.size());
        while (laidOut.size() < read.size()) {
            int from = laidOut.size();
            laidOut.addAll(renderThread.call(deadline -> {
                List<Optional<LaidOut>> slice = new ArrayList<>();
                // At least one per slice, so the loop always makes progress.
                do {
                    slice.add(layOut(read.get(from + slice.size())));
                } while (from + slice.size() < read.size() && System.nanoTime() < deadline);
                return slice;
            }));
        }
        return laidOut;
    }

    private Optional<LaidOut> layOut(Read read) {
        try {
            return read.candidate().layOut(read.recipe());
        } catch (RuntimeException | LinkageError e) {
            String typeId = read.candidate().typeId();
            if (failedLayouts.add(typeId)) {
                LOG.warn("[recipehost] recipes of {} fail to lay out; they ship without a layout (logged once per type)", typeId, e);
            }
            return Optional.empty();
        }
    }

    private static boolean isSkipped(String typeId) {
        return SKIPPED_TYPES.contains(typeId) || typeId.startsWith(JEI_TAG_TYPE_PREFIX);
    }

    /** The namespace of a namespaced id; {@code minecraft} when it has none. */
    private static String namespaceOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }
}
