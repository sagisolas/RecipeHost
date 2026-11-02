package org.sagisolas.recipehost.mod.snapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import okio.ByteString;
import org.sagisolas.recipehost.mod.ModDescriptor;
import org.sagisolas.recipehost.mod.Platform;
import org.sagisolas.recipehost.mod.net.SnapshotSource;
import org.sagisolas.recipehost.mod.net.WireProtocol;
import org.sagisolas.recipehost.protocol.Handshake;
import org.sagisolas.recipehost.protocol.Icon;
import org.sagisolas.recipehost.protocol.IconBatch;
import org.sagisolas.recipehost.protocol.ItemBatch;
import org.sagisolas.recipehost.protocol.ModInfo;
import org.sagisolas.recipehost.protocol.RecipeBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Minecraft-backed {@link SnapshotSource}. Holds a live {@link ClientLevel} and
 * does nothing on construction; the first {@link #handshake()} call triggers an
 * eager pass over {@code BuiltInRegistries.ITEM} and the client's
 * {@link net.minecraft.world.item.crafting.RecipeManager}. Icons are rendered
 * lazily in 32-item batches on the render thread, sharing a single
 * {@link IconRenderer} held for the source's lifetime (ADR 0003).
 *
 * <p>GL resources are released by {@link #close()}, posted to the render thread.
 * The caller (Session lifecycle) owns the close.
 */
public final class MinecraftSnapshotSource implements SnapshotSource, AutoCloseable {

    private static final int ITEM_BATCH_SIZE = 256;
    private static final int RECIPE_BATCH_SIZE = 256;
    private static final int ICON_BATCH_SIZE = 32;

    private final ClientLevel level;
    private final Minecraft minecraft;

    private boolean snapshotMaterialized;
    private List<org.sagisolas.recipehost.protocol.Item> items;
    private List<String> itemIds;
    private List<org.sagisolas.recipehost.protocol.Recipe> recipes;
    private List<ModInfo> mods;
    private String locale;

    private volatile IconRenderer iconRenderer;

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
        return new IconBatchIterator(new ArrayList<>(itemIds));
    }

    private void materializeSnapshot() {
        if (snapshotMaterialized) return;

        locale = minecraft.options.languageCode;

        ItemMapper.Result mapped = ItemMapper.map();
        this.items = mapped.wireItems();
        this.itemIds = mapped.ids();

        List<ModInfo> wireMods = new ArrayList<>();
        for (ModDescriptor m : Platform.listMods()) {
            if (mapped.namespacesWithItems().contains(m.id())) {
                wireMods.add(new ModInfo.Builder()
                        .id(m.id())
                        .name(m.name())
                        .version(m.version())
                        .build());
            }
        }
        this.mods = Collections.unmodifiableList(wireMods);

        RegistryAccess registryAccess = level.registryAccess();
        List<org.sagisolas.recipehost.protocol.Recipe> wireRecipes = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            wireRecipes.add(RecipeMapper.toWire(holder, registryAccess));
        }
        this.recipes = Collections.unmodifiableList(wireRecipes);

        this.snapshotMaterialized = true;
    }

    /** Release GL resources on the render thread. Safe to call repeatedly. */
    @Override
    public void close() {
        IconRenderer toClose = iconRenderer;
        iconRenderer = null;
        if (toClose != null) {
            minecraft.execute(toClose::close);
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

        IconBatchIterator(List<String> remaining) {
            this.remaining = remaining;
        }

        @Override
        public boolean hasNext() {
            return !remaining.isEmpty();
        }

        @Override
        public IconBatch next() {
            if (!hasNext()) throw new NoSuchElementException();
            int take = Math.min(ICON_BATCH_SIZE, remaining.size());
            List<String> chunk = new ArrayList<>(remaining.subList(0, take));
            remaining.subList(0, take).clear();

            CompletableFuture<IconBatch> future = new CompletableFuture<>();
            minecraft.execute(() -> {
                try {
                    if (iconRenderer == null) iconRenderer = new IconRenderer(minecraft);
                    List<Icon> icons = new ArrayList<>(chunk.size());
                    for (String id : chunk) {
                        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
                        byte[] png = iconRenderer.renderToPng(new ItemStack(item));
                        icons.add(new Icon.Builder()
                                .item_id(id)
                                .png(ByteString.of(png))
                                .build());
                    }
                    future.complete(new IconBatch.Builder().icons(icons).build());
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
    }
}
