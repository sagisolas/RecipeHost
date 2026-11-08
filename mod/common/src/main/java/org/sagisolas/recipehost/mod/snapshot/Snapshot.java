package org.sagisolas.recipehost.mod.snapshot;

import org.sagisolas.recipehost.mod.net.SnapshotSource;
import org.sagisolas.recipehost.mod.net.WireProtocol;
import org.sagisolas.recipehost.mod.snapshot.icon.IconBatches;
import org.sagisolas.recipehost.mod.snapshot.image.PngEncoder;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutBatches;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.Item;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.LayoutBatch;
import org.sagisolas.recipehost.protocol.ModInfo;
import org.sagisolas.recipehost.protocol.Recipe;
import org.sagisolas.recipehost.protocol.RecipeBatch;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A `Snapshot` as batches: the {@link Handshake}, then items and recipes in fixed-size batches,
 * then one Icon atlas per Icon sheet, then the layouts. The game is read once, through
 * {@code contents}, when the Snapshot is first asked for; Icons and layouts are drawn by the two
 * sheet renderers as their batches are pulled.
 */
public final class Snapshot implements SnapshotSource {

    private static final int ITEM_BATCH_SIZE = 256;
    private static final int RECIPE_BATCH_SIZE = 256;

    /** What a Snapshot carries, read from the game. Recipes are those the Recipe assembly ships. */
    public record Contents(
            String minecraftVersion,
            String locale,
            String instanceName,
            List<ModInfo> mods,
            List<Item> items,
            List<Recipe> recipes,
            List<LayoutJob> layoutJobs) {
        public Contents {
            mods = List.copyOf(mods);
            items = List.copyOf(items);
            recipes = List.copyOf(recipes);
            layoutJobs = List.copyOf(layoutJobs);
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

    @Override
    public synchronized Handshake handshake() {
        Contents c = contents();
        return Handshake.newBuilder()
                .setProtocolVersion(WireProtocol.PROTOCOL_VERSION)
                .setMinecraftVersion(c.minecraftVersion())
                .setLocale(c.locale())
                .addAllMods(c.mods())
                .setInstanceName(c.instanceName())
                .setTotalItems(c.items().size())
                .setTotalRecipes(c.recipes().size())
                .build();
    }

    @Override
    public synchronized Iterator<ItemBatch> itemBatches() {
        return batched(contents().items(), ITEM_BATCH_SIZE, chunk -> ItemBatch.newBuilder().addAllItems(chunk).build());
    }

    @Override
    public synchronized Iterator<RecipeBatch> recipeBatches() {
        return batched(contents().recipes(), RECIPE_BATCH_SIZE, chunk -> RecipeBatch.newBuilder().addAllRecipes(chunk).build());
    }

    @Override
    public synchronized Iterator<IconBatch> iconBatches() {
        return new IconBatches(contents().items().stream().map(Item::getId).toList(), icons, new PngEncoder());
    }

    @Override
    public synchronized Iterator<LayoutBatch> layoutBatches() {
        return new LayoutBatches(contents().layoutJobs(), layouts, new PngEncoder());
    }

    private Contents contents() {
        if (contents == null) contents = read.get();
        return contents;
    }

    private static <T, B> Iterator<B> batched(List<T> src, int size, Function<List<T>, B> build) {
        return new Iterator<>() {
            int i = 0;
            @Override public boolean hasNext() { return i < src.size(); }
            @Override public B next() {
                if (!hasNext()) throw new NoSuchElementException();
                int end = Math.min(i + size, src.size());
                B b = build.apply(src.subList(i, end));
                i = end;
                return b;
            }
        };
    }
}
