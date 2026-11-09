package org.sagisolas.recipehost.mod.snapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.sagisolas.recipehost.mod.Platform;
import org.sagisolas.recipehost.mod.net.SnapshotSource;
import org.sagisolas.recipehost.mod.snapshot.icon.IconRenderer;
import org.sagisolas.recipehost.mod.snapshot.icon.IconSheet;
import org.sagisolas.recipehost.mod.snapshot.item.ItemMapper;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutRenderer;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutSheet;
import org.sagisolas.recipehost.mod.snapshot.recipe.RecipeAssembly;
import org.sagisolas.recipehost.protocol.Envelope;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The {@link Snapshot} of the running game: reads the item registry, the {@link RecipeSource}
 * (through the {@link RecipeAssembly}), the mod list and the game's settings, and draws Icons and
 * layouts with GL renderers on the {@link RenderThread} (ADR 0003, 0015). Does nothing on
 * construction; the game is read when the Snapshot is first asked for.
 *
 * <p>The renderers are created on first use and held for the source's lifetime; their GL
 * resources are released by {@link #close()}, posted to the render thread. The caller (Session
 * lifecycle) owns the close.
 */
public final class MinecraftSnapshotSource implements SnapshotSource, AutoCloseable {

    private final RecipeSource recipeSource;
    private final RenderThread renderThread;
    private final Minecraft minecraft;
    private final Snapshot snapshot;

    private volatile IconRenderer iconRenderer;
    private volatile LayoutRenderer layoutRenderer;

    public MinecraftSnapshotSource(RecipeSource recipeSource, RenderThread renderThread) {
        this.recipeSource = recipeSource;
        this.renderThread = renderThread;
        this.minecraft = Minecraft.getInstance();
        this.snapshot = new Snapshot(this::read, this::renderIconSheet, this::renderLayoutSheet);
    }

    @Override
    public Iterator<Envelope> frames() {
        return snapshot.frames();
    }

    /** Runs on the sender thread, on the Snapshot's first use. */
    private Snapshot.Contents read() {
        ItemMapper.Result items = ItemMapper.map();
        RecipeAssembly.Prepared recipes = new RecipeAssembly(renderThread).read(recipeSource.candidates());
        Set<String> ownerNamespaces = new HashSet<>(items.namespacesWithItems());
        ownerNamespaces.addAll(recipes.namespaces());
        return new Snapshot.Contents(
                Platform.minecraftVersion(),
                minecraft.options.languageCode,
                InstanceName.of(minecraft.gameDirectory.toPath().toAbsolutePath().normalize(), Files::exists),
                ModInfos.assemble(Platform.listMods(), ownerNamespaces),
                items.wireItems(),
                recipes);
    }

    /** Queues the sheet on the render thread; {@code pending} is a copy the sender won't touch. */
    private CompletableFuture<IconSheet> renderIconSheet(List<String> pending) {
        return renderThread.submit(deadline -> {
            if (iconRenderer == null) iconRenderer = new IconRenderer(minecraft);
            List<ItemStack> stacks = new ArrayList<>(pending.size());
            for (String id : pending) {
                stacks.add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))));
            }
            return iconRenderer.render(stacks, deadline);
        });
    }

    /** Queues the sheet on the render thread; {@code pending} is a copy the sender won't touch. */
    private CompletableFuture<LayoutSheet> renderLayoutSheet(List<LayoutJob> pending) {
        return renderThread.submit(deadline -> {
            if (layoutRenderer == null) layoutRenderer = new LayoutRenderer(minecraft);
            return layoutRenderer.render(pending, deadline);
        });
    }

    /** Release GL resources on the render thread. Safe to call repeatedly. */
    @Override
    public void close() {
        IconRenderer icons = iconRenderer;
        iconRenderer = null;
        if (icons != null) renderThread.post(icons::close);
        LayoutRenderer layouts = layoutRenderer;
        layoutRenderer = null;
        if (layouts != null) renderThread.post(layouts::close);
    }
}
