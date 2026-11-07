package org.sagisolas.recipehost.mod.snapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import okio.ByteString;
import org.sagisolas.recipehost.mod.Platform;
import org.sagisolas.recipehost.mod.net.SnapshotSource;
import org.sagisolas.recipehost.mod.net.WireProtocol;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.LayoutBatch;
import org.sagisolas.recipehost.protocol.ModInfo;
import org.sagisolas.recipehost.protocol.RecipeBatch;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Minecraft-backed {@link SnapshotSource}. Holds a live {@link ClientLevel} and
 * does nothing on construction; the first {@link #handshake()} call triggers an
 * eager pass over {@code BuiltInRegistries.ITEM} and the client's
 * {@link net.minecraft.world.item.crafting.RecipeManager}. Icons are rendered
 * lazily on the render thread, one {@link IconRenderer} sheet per batch within
 * a time budget, sharing a single renderer held for the source's lifetime
 * (ADR 0003). Each sheet goes out whole as one Icon atlas (ADR 0014); its PNG
 * encoding runs on the calling (sender) thread.
 *
 * <p>Recipe layouts follow the same pattern with a {@link LayoutRenderer}: they are
 * rendered only once the sender asks for them, after the last Icon batch, one
 * sheet per batch within the same time budget ({@link LayoutBatches}).
 *
 * <p>GL resources are released by {@link #close()}, posted to the render thread.
 * The caller (Session lifecycle) owns the close.
 */
public final class MinecraftSnapshotSource implements SnapshotSource, AutoCloseable {

    private static final int ITEM_BATCH_SIZE = 256;
    private static final int RECIPE_BATCH_SIZE = 256;
    /** Render-thread time one icon or layout batch may take, so streaming them doesn't drop frames. */
    private static final long RENDER_BUDGET_NANOS = 3_000_000L;

    private final ClientLevel level;
    private final Minecraft minecraft;

    private boolean snapshotMaterialized;
    private List<org.sagisolas.recipehost.protocol.Item> items;
    private List<String> itemIds;
    private List<org.sagisolas.recipehost.protocol.Recipe> recipes;
    private List<ModInfo> mods;
    private String locale;
    private String instanceName;
    private List<LayoutJob> layoutJobs;

    private volatile IconRenderer iconRenderer;
    private volatile LayoutRenderer layoutRenderer;

    public MinecraftSnapshotSource(ClientLevel level) {
        this.level = level;
        this.minecraft = Minecraft.getInstance();
    }

    @Override
    public synchronized Handshake handshake() {
        materializeSnapshot();
        return new Handshake.Builder()
                .protocol_version(WireProtocol.PROTOCOL_VERSION)
                .minecraft_version(Platform.minecraftVersion())
                .locale(locale)
                .mods(mods)
                .instance_name(instanceName)
                .total_items(items.size())
                .total_recipes(recipes.size())
                .build();
    }

    @Override
    public synchronized Iterator<ItemBatch> itemBatches() {
        materializeSnapshot();
        return batched(items, ITEM_BATCH_SIZE,
                chunk -> new ItemBatch.Builder().items(chunk).build());
    }

    @Override
    public synchronized Iterator<RecipeBatch> recipeBatches() {
        materializeSnapshot();
        return batched(recipes, RECIPE_BATCH_SIZE,
                chunk -> new RecipeBatch.Builder().recipes(chunk).build());
    }

    @Override
    public synchronized Iterator<IconBatch> iconBatches() {
        materializeSnapshot();
        return new IconBatchIterator(new ArrayList<>(itemIds), new PngEncoder());
    }

    @Override
    public synchronized Iterator<LayoutBatch> layoutBatches() {
        materializeSnapshot();
        return new LayoutBatches(layoutJobs, this::renderLayoutSheet, new PngEncoder());
    }

    /**
     * Runs on the sender thread, which blocks until the render thread is done, so
     * {@code pending} isn't touched while the sheet is drawn.
     */
    private LayoutSheet renderLayoutSheet(List<LayoutJob> pending) {
        return onRenderThread(() -> {
            long deadline = System.nanoTime() + RENDER_BUDGET_NANOS;
            if (layoutRenderer == null) layoutRenderer = new LayoutRenderer(minecraft);
            return layoutRenderer.render(pending, deadline);
        });
    }

    private void materializeSnapshot() {
        if (snapshotMaterialized) return;

        locale = minecraft.options.languageCode;
        instanceName = InstanceName.of(minecraft.gameDirectory.toPath().toAbsolutePath().normalize(), Files::exists);

        ItemMapper.Result mapped = ItemMapper.map();
        this.items = mapped.wireItems();
        this.itemIds = mapped.ids();

        RegistryAccess registryAccess = level.registryAccess();
        List<org.sagisolas.recipehost.protocol.Recipe> wireRecipes = new ArrayList<>();
        List<LayoutJob> jobs = new ArrayList<>();
        RecipeMapper recipeMapper = new RecipeMapper(new VanillaLayouts(minecraft));
        Set<String> ownerNamespaces = new HashSet<>(mapped.namespacesWithItems());
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            RecipeMapper.Mapped recipe = recipeMapper.map(holder, registryAccess);
            wireRecipes.add(recipe.recipe());
            recipe.layout().ifPresent(jobs::add);
            ownerNamespaces.add(holder.id().getNamespace());
        }
        this.recipes = Collections.unmodifiableList(wireRecipes);
        // TODO(recipe-layouts issue 05): with EMI loaded, its layout capture supplies the jobs instead.
        this.layoutJobs = Collections.unmodifiableList(jobs);

        this.mods = Collections.unmodifiableList(ModInfos.assemble(Platform.listMods(), ownerNamespaces));

        this.snapshotMaterialized = true;
    }

    /** Release GL resources on the render thread. Safe to call repeatedly. */
    @Override
    public void close() {
        IconRenderer icons = iconRenderer;
        iconRenderer = null;
        if (icons != null) {
            minecraft.execute(icons::close);
        }
        LayoutRenderer layouts = layoutRenderer;
        layoutRenderer = null;
        if (layouts != null) {
            minecraft.execute(layouts::close);
        }
    }

    /** Runs {@code work} on the render thread and blocks the calling thread for its result. */
    private <T> T onRenderThread(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        minecraft.execute(() -> {
            try {
                future.complete(work.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        }
    }

    private static <T, B> Iterator<B> batched(List<T> src, int size, Function<List<T>, B> build) {
        return new Iterator<>() {
            int i = 0;
            @Override public boolean hasNext() { return i < src.size(); }
            @Override public B next() {
                if (!hasNext()) throw new NoSuchElementException();
                int end = Math.min(i + size, src.size());
                B b = build.apply(new ArrayList<>(src.subList(i, end)));
                i = end;
                return b;
            }
        };
    }

    private final class IconBatchIterator implements Iterator<IconBatch> {
        private final List<String> remaining;
        private final PngEncoder encoder;

        IconBatchIterator(List<String> remaining, PngEncoder encoder) {
            this.remaining = remaining;
            this.encoder = encoder;
        }

        @Override
        public boolean hasNext() {
            return !remaining.isEmpty();
        }

        @Override
        public IconBatch next() {
            if (!hasNext()) throw new NoSuchElementException();
            List<String> candidates = new ArrayList<>(
                    remaining.subList(0, Math.min(IconRenderer.CAPACITY, remaining.size())));

            IconSheet sheet = onRenderThread(() -> {
                long deadline = System.nanoTime() + RENDER_BUDGET_NANOS;
                if (iconRenderer == null) iconRenderer = new IconRenderer(minecraft);
                List<ItemStack> stacks = new ArrayList<>(candidates.size());
                for (String id : candidates) {
                    stacks.add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))));
                }
                return iconRenderer.render(stacks, deadline);
            });
            List<String> drawn = new ArrayList<>(candidates.subList(0, sheet.count()));
            remaining.subList(0, sheet.count()).clear();
            return new IconBatch.Builder()
                    .png(ByteString.of(encoder.encode(sheet.image())))
                    .cell_size(sheet.cell())
                    .columns(sheet.columns())
                    .item_ids(drawn)
                    .build();
        }
    }
}
