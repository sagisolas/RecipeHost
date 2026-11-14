/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.icon;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.sagisolas.recipehost.mod.snapshot.image.NativeImages;
import org.slf4j.Logger;

/**
 * Owns one offscreen {@link RenderTarget} laid out as a grid of cells and
 * rasterizes {@link ItemStack}s into it, a sheet at a time. Items are drawn in
 * GUI units (16 per item, as {@code renderItem} expects) and rasterized at
 * {@link #SCALE}× that, so each cell is {@link #CELL} px and 3D block items,
 * glint and custom renderers keep detail on high-density screens; a flat
 * item's texels simply become {@code SCALE}×{@code SCALE} blocks. Each sheet costs one
 * clear, one GL state setup and one synchronous read-back, instead of one per
 * icon. All methods must be called on the render thread, including
 * {@link #close()}.
 *
 * <p>See ADR 0003: we go through {@code GuiGraphics.renderItem} rather than the
 * texture atlas so 3D block items, custom renderers, and model overrides come out
 * correctly.
 */
public final class IconRenderer implements AutoCloseable {

    /** Size of one item in GUI units, the coordinates {@code renderItem} draws in. */
    private static final Logger LOG = LogUtils.getLogger();

    private static final int ITEM = 16;
    /** Framebuffer pixels per GUI unit. */
    static final int SCALE = 2;

    static final int CELL = ITEM * SCALE;
    static final int COLUMNS = 16;
    static final int ROWS = 8;
    public static final int CAPACITY = COLUMNS * ROWS;

    private static final int WIDTH = COLUMNS * CELL;
    private static final int HEIGHT = ROWS * CELL;
    // Same depth range and view offset GameRenderer uses for the GUI pass, so
    // renderItem's z offsets (+150 and up) land inside the clip volume.
    private static final float NEAR = 1000f;
    private static final float FAR = 21000f;
    private static final float GUI_DEPTH_OFFSET = -11000f;

    private final Minecraft minecraft;
    private final RenderTarget target;

    /** Namespaces whose Icons failed, so each is logged once. Touched only on the render thread. */
    private final Set<String> failedNamespaces = new HashSet<>();

    public IconRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        this.target.setClearColor(0f, 0f, 0f, 0f);
    }

    /**
     * Draws stacks from the front of {@code stacks} into the grid until it is full
     * or {@code System.nanoTime()} passes {@code deadlineNanos}, then reads the
     * sheet back. At least one stack is drawn, so callers always make progress.
     * The sheet's {@link IconSheet#count()} says how many were taken.
     */
    public IconSheet render(List<ItemStack> stacks, long deadlineNanos) {
        RenderTarget main = minecraft.getMainRenderTarget();
        // clear() ends with unbindWrite() (back to the window's framebuffer), so
        // bind after clearing or the items are drawn into the window, not the sheet.
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        // We run during or after a frame's GUI pass, never inside the world's, so the
        // projection/model-view in place are the screen's
        // (ortho over the scaled window, identity view). Under those the items
        // are scaled down and depth-clipped, giving fully transparent icons.
        // The ortho spans the sheet in GUI units while the viewport (set by
        // bindWrite) spans it in pixels, which is what scales items by SCALE.
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0f, COLUMNS * ITEM, ROWS * ITEM, 0f, NEAR, FAR), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0f, 0f, GUI_DEPTH_OFFSET);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();

        int limit = Math.min(CAPACITY, stacks.size());
        int drawn = 0;
        try {
            do {
                draw(stacks.get(drawn), (drawn % COLUMNS) * ITEM, (drawn / COLUMNS) * ITEM);
                drawn++;
            } while (drawn < limit && System.nanoTime() < deadlineNanos);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }

        try (NativeImage image = new NativeImage(WIDTH, HEIGHT, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            // Flipped and swizzled on the sender thread, outside the frame's budget.
            return IconSheet.fromReadback(COLUMNS, CELL, ROWS, drawn, NativeImages.readback(image));
        } finally {
            target.unbindWrite();
            main.bindWrite(true);
        }
    }

    /**
     * Draws one stack with its own {@link GuiGraphics}, so a renderer that throws midway
     * can't leave a pushed pose behind for the next. A stack whose renderer throws (an
     * exception, or a linkage error from a mod built against another API) keeps a blank
     * cell, and the Snapshot goes on; it is logged once per namespace.
     */
    private void draw(ItemStack stack, int x, int y) {
        GuiGraphics guiGraphics =
                new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        try {
            guiGraphics.renderItem(stack, x, y);
        } catch (RuntimeException | LinkageError e) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (failedNamespaces.add(id.getNamespace())) {
                LOG.warn(
                        "[recipehost] Couldn't draw the Icon of {}; it ships blank (logged once per namespace)", id, e);
            }
        } finally {
            guiGraphics.flush();
        }
    }

    @Override
    public void close() {
        target.destroyBuffers();
    }
}
