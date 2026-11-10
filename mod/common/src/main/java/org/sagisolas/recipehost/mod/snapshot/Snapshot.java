package org.sagisolas.recipehost.mod.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;
import org.sagisolas.recipehost.mod.net.SnapshotSource;
import org.sagisolas.recipehost.mod.net.WireProtocol;
import org.sagisolas.recipehost.mod.snapshot.icon.IconBatches;
import org.sagisolas.recipehost.mod.snapshot.image.PngEncoder;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutBatches;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.mod.snapshot.recipe.RecipeAssembly;
import org.sagisolas.recipehost.mod.snapshot.recipe.SharedSlots;
import org.sagisolas.recipehost.protocol.Envelope;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.Item;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.LayoutBatch;
import org.sagisolas.recipehost.protocol.ModInfo;
import org.sagisolas.recipehost.protocol.RecipeBatch;

/**
 * A `Snapshot` as its frames: the {@link Handshake}, then items and recipes in fixed-size batches,
 * then one Icon atlas per Icon sheet, then the layouts. The game is read once, through
 * {@code contents}, when the Snapshot is first asked for; Icons and layouts are drawn by the two
 * sheet renderers as their batches are pulled.
 */
public final class Snapshot implements SnapshotSource {

    private static final int ITEM_BATCH_SIZE = 256;
    private static final int RECIPE_BATCH_SIZE = 256;

    /**
     * What a Snapshot carries, read from the game. Recipes are those the Recipe assembly ships, read
     * but not laid out: each recipe batch lays out its own recipes as it is pulled.
     */
    public record Contents(
            String minecraftVersion,
            String locale,
            String instanceName,
            List<ModInfo> mods,
            List<Item> items,
            RecipeAssembly.Prepared recipes) {
        public Contents {
            mods = List.copyOf(mods);
            items = List.copyOf(items);
        }
    }

    private final Supplier<Contents> read;
    private final IconBatches.SheetRenderer icons;
    private final LayoutBatches.SheetRenderer layouts;
    private Contents contents;

    public Snapshot(Supplier<Contents> read, IconBatches.SheetRenderer icons, LayoutBatches.SheetRenderer layouts) {
        this.read = read;
        this.icons = icons;
        this.layouts = layouts;
    }

    /**
     * The frames in wire order. Each part is started only when the one before it is drained, so
     * nothing is rendered before the recipes are out.
     */
    @Override
    public Iterator<Envelope> frames() {
        // This session's layouts, gathered as its recipe batches are laid out.
        List<LayoutJob> layoutJobs = new ArrayList<>();
        List<Supplier<Iterator<Envelope>>> parts = List.of(
                () -> List.of(Envelope.newBuilder().setHandshake(handshake()).build())
                        .iterator(),
                () -> map(
                        itemBatches(),
                        batch -> Envelope.newBuilder().setItemBatch(batch).build()),
                () -> map(
                        recipeBatches(layoutJobs),
                        batch -> Envelope.newBuilder().setRecipeBatch(batch).build()),
                () -> map(
                        iconBatches(),
                        batch -> Envelope.newBuilder().setIconBatch(batch).build()),
                () -> map(
                        layoutBatches(layoutJobs),
                        batch -> Envelope.newBuilder().setLayoutBatch(batch).build()));
        return new Iterator<>() {
            private int part = 0;
            private Iterator<Envelope> current = Collections.emptyIterator();

            @Override
            public boolean hasNext() {
                while (!current.hasNext() && part < parts.size())
                    current = parts.get(part++).get();
                return current.hasNext();
            }

            @Override
            public Envelope next() {
                if (!hasNext()) throw new NoSuchElementException();
                return current.next();
            }
        };
    }

    private synchronized Handshake handshake() {
        Contents c = contents();
        return Handshake.newBuilder()
                .setProtocolVersion(WireProtocol.PROTOCOL_VERSION)
                .setMinecraftVersion(c.minecraftVersion())
                .setLocale(c.locale())
                .addAllMods(c.mods())
                .setInstanceName(c.instanceName())
                .setTotalItems(c.items().size())
                .setTotalRecipes(c.recipes().count())
                .build();
    }

    private synchronized Iterator<ItemBatch> itemBatches() {
        return batched(
                contents().items(),
                ITEM_BATCH_SIZE,
                chunk -> ItemBatch.newBuilder().addAllItems(chunk).build());
    }

    /**
     * Lays out each batch's recipes as it is pulled, so their layouts are captured while recipes
     * stream, and adds their jobs to {@code layoutJobs}.
     */
    private synchronized Iterator<RecipeBatch> recipeBatches(List<LayoutJob> layoutJobs) {
        RecipeAssembly.Prepared recipes = contents().recipes();
        return new Iterator<>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < recipes.count();
            }

            @Override
            public RecipeBatch next() {
                if (!hasNext()) throw new NoSuchElementException();
                int end = Math.min(i + RECIPE_BATCH_SIZE, recipes.count());
                RecipeAssembly.LaidOutRecipes laidOut = recipes.layOut(i, end);
                i = end;
                layoutJobs.addAll(laidOut.layouts());
                return SharedSlots.batchOf(laidOut.recipes());
            }
        };
    }

    private synchronized Iterator<IconBatch> iconBatches() {
        return new IconBatches(contents().items().stream().map(Item::getId).toList(), icons, new PngEncoder());
    }

    private synchronized Iterator<LayoutBatch> layoutBatches(List<LayoutJob> layoutJobs) {
        return new LayoutBatches(List.copyOf(layoutJobs), layouts, new PngEncoder());
    }

    private Contents contents() {
        if (contents == null) contents = read.get();
        return contents;
    }

    private static <T, R> Iterator<R> map(Iterator<T> it, Function<T, R> f) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public R next() {
                return f.apply(it.next());
            }
        };
    }

    private static <T, B> Iterator<B> batched(List<T> src, int size, Function<List<T>, B> build) {
        return new Iterator<>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < src.size();
            }

            @Override
            public B next() {
                if (!hasNext()) throw new NoSuchElementException();
                int end = Math.min(i + size, src.size());
                B b = build.apply(src.subList(i, end));
                i = end;
                return b;
            }
        };
    }
}
