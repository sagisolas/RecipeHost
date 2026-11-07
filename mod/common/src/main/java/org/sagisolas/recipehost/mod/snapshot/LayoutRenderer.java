package org.sagisolas.recipehost.mod.snapshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Owns one offscreen {@link RenderTarget} and draws {@link LayoutJob}s onto it at
 * one pixel per GUI pixel, shelf-packed, a sheet at a time: one clear, one GL state
 * setup and one synchronous read-back per sheet. All methods must be called on the
 * render thread, including {@link #close()}.
 */
final class LayoutRenderer implements AutoCloseable {

    private static final int SIZE = 512;

    // Same depth range and view offset as IconRenderer (and GameRenderer's GUI pass).
    private static final float NEAR = 1000f;
    private static final float FAR = 21000f;
    private static final float GUI_DEPTH_OFFSET = -11000f;
    /** No hover highlight: the pointer is nowhere on the layout. */
    private static final int NO_MOUSE = -1;

    private final Minecraft minecraft;
    private final RenderTarget target;

    LayoutRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        this.target.setClearColor(0f, 0f, 0f, 0f);
    }

    /**
     * Draws jobs from the front of {@code jobs} until the sheet is full or
     * {@code System.nanoTime()} passes {@code deadlineNanos}, then reads the sheet
     * back. At least one job is taken, so callers always make progress. Each job is
     * clipped to its own rectangle. A job whose draw throws (an exception or a
     * linkage error), or that is larger than an empty sheet, is reported as failed.
     */
    LayoutSheet render(List<LayoutJob> jobs, long deadlineNanos) {
        RenderTarget main = minecraft.getMainRenderTarget();
        // clear() ends with unbindWrite(), so bind after clearing (see IconRenderer).
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0f, SIZE, SIZE, 0f, NEAR, FAR), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0f, 0f, GUI_DEPTH_OFFSET);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();

        ShelfPacker packer = new ShelfPacker(SIZE, SIZE);
        List<LayoutSheet.Placed> placed = new ArrayList<>();
        List<LayoutSheet.Failed> failed = new ArrayList<>();
        int taken = 0;
        try {
            do {
                LayoutJob job = jobs.get(taken);
                Optional<ShelfPacker.Position> position = packer.place(job.width(), job.height());
                if (position.isEmpty()) {
                    // Only a job that can't fit even an empty sheet is refused first.
                    if (taken == 0) {
                        failed.add(new LayoutSheet.Failed(job, new IllegalArgumentException(
                                "layout " + job.width() + "x" + job.height() + " is larger than the " + SIZE + " px sheet")));
                        taken++;
                    }
                    break;
                }
                int x = position.get().x();
                int y = position.get().y();
                try {
                    draw(job, x, y);
                    placed.add(new LayoutSheet.Placed(job, x, y));
                } catch (RuntimeException | LinkageError e) {
                    // LinkageError: a mod's draw compiled against an API that isn't there.
                    failed.add(new LayoutSheet.Failed(job, e));
                }
                taken++;
            } while (taken < jobs.size() && System.nanoTime() < deadlineNanos);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }

        try (NativeImage image = new NativeImage(SIZE, SIZE, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            return new LayoutSheet(SIZE, NativeImages.toArgb(image), taken, placed, failed);
        } finally {
            target.unbindWrite();
            main.bindWrite(true);
        }
    }

    /**
     * Draws one job with its own {@link GuiGraphics}, so a draw that throws midway
     * can't leave pushed poses behind for the next. Whatever it buffered is flushed
     * inside its scissor, so it stays inside its own rectangle.
     */
    private void draw(LayoutJob job, int x, int y) {
        GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        // GL scissor boxes start at the bottom-left; the sheet's GUI y runs down from the top.
        RenderSystem.enableScissor(x, SIZE - y - job.height(), job.width(), job.height());
        try {
            graphics.pose().translate(x, y, 0f);
            job.drawer().draw(graphics, NO_MOUSE, NO_MOUSE);
        } finally {
            graphics.flush();
            RenderSystem.disableScissor();
        }
    }

    @Override
    public void close() {
        target.destroyBuffers();
    }
}
