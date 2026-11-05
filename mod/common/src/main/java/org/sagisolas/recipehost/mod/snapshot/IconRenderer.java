package org.sagisolas.recipehost.mod.snapshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.List;

/**
 * Owns one offscreen {@link RenderTarget} laid out as a grid of 16×16 cells and
 * rasterizes {@link ItemStack}s into it, a sheet at a time. Each sheet costs one
 * clear, one GL state setup and one synchronous read-back, instead of one per
 * icon. All methods must be called on the render thread, including
 * {@link #close()}.
 *
 * <p>See ADR 0003: we go through {@code GuiGraphics.renderItem} rather than the
 * texture atlas so 3D block items, custom renderers, and model overrides come out
 * correctly.
 */
final class IconRenderer implements AutoCloseable {

    static final int CELL = 16;
    static final int COLUMNS = 8;
    static final int ROWS = 4;
    static final int CAPACITY = COLUMNS * ROWS;

    private static final int WIDTH = COLUMNS * CELL;
    private static final int HEIGHT = ROWS * CELL;
    // Same depth range and view offset GameRenderer uses for the GUI pass, so
    // renderItem's z offsets (+150 and up) land inside the clip volume.
    private static final float NEAR = 1000f;
    private static final float FAR = 21000f;
    private static final float GUI_DEPTH_OFFSET = -11000f;

    private final Minecraft minecraft;
    private final RenderTarget target;

    IconRenderer(Minecraft minecraft) {
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
    IconSheet render(List<ItemStack> stacks, long deadlineNanos) {
        RenderTarget main = minecraft.getMainRenderTarget();
        // clear() ends with unbindWrite() (back to the window's framebuffer), so
        // bind after clearing or the items are drawn into the window, not the sheet.
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        // We run from Minecraft#execute, outside the frame's GUI pass, so the
        // projection/model-view left over from the last frame are the screen's
        // (ortho over the scaled window, identity view). Under those the items
        // are scaled down and depth-clipped, giving fully transparent icons.
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0f, WIDTH, HEIGHT, 0f, NEAR, FAR), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0f, 0f, GUI_DEPTH_OFFSET);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();

        int limit = Math.min(CAPACITY, stacks.size());
        int drawn = 0;
        try {
            GuiGraphics guiGraphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            do {
                guiGraphics.renderItem(stacks.get(drawn), (drawn % COLUMNS) * CELL, (drawn / COLUMNS) * CELL);
                drawn++;
            } while (drawn < limit && System.nanoTime() < deadlineNanos);
            guiGraphics.flush();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }

        try (NativeImage image = new NativeImage(WIDTH, HEIGHT, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            return new IconSheet(COLUMNS, CELL, drawn, toArgb(image));
        } finally {
            target.unbindWrite();
            main.bindWrite(true);
        }
    }

    /** NativeImage pixels are ABGR; swap red and blue to get ARGB. */
    private static int[] toArgb(NativeImage image) {
        int[] argb = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int abgr = image.getPixelRGBA(x, y);
                argb[y * WIDTH + x] = (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >>> 16) & 0xFF);
            }
        }
        return argb;
    }

    @Override
    public void close() {
        target.destroyBuffers();
    }
}
