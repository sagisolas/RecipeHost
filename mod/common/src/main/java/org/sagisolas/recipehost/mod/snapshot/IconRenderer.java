package org.sagisolas.recipehost.mod.snapshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.io.IOException;

/**
 * Owns a single 16×16 offscreen {@link RenderTarget} and the GL state needed to
 * rasterize an {@link ItemStack} into PNG bytes. All methods must be called on
 * the render thread. GL resources are released by {@link #close()}, which also
 * must run on the render thread.
 *
 * <p>See ADR 0003: we go through {@code GuiGraphics.renderItem} rather than the
 * texture atlas so 3D block items, custom renderers, and model overrides come out
 * correctly.
 */
final class IconRenderer implements AutoCloseable {

    private static final int SIZE = 16;
    // Same depth range and view offset GameRenderer uses for the GUI pass, so
    // renderItem's z offsets (+150 and up) land inside the clip volume.
    private static final float NEAR = 1000f;
    private static final float FAR = 21000f;
    private static final float GUI_DEPTH_OFFSET = -11000f;

    private final Minecraft minecraft;
    private final RenderTarget target;

    IconRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        this.target.setClearColor(0f, 0f, 0f, 0f);
    }

    byte[] renderToPng(ItemStack stack) throws IOException {
        RenderTarget main = minecraft.getMainRenderTarget();
        // clear() ends with unbindWrite() (back to the window's framebuffer), so
        // bind after clearing or the item is drawn into the window, not the icon.
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        // We run from Minecraft#execute, outside the frame's GUI pass, so the
        // projection/model-view left over from the last frame are the screen's
        // (ortho over the scaled window, identity view). Under those the item
        // covers < 1 px and is depth-clipped, giving fully transparent icons.
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0f, SIZE, SIZE, 0f, NEAR, FAR), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0f, 0f, GUI_DEPTH_OFFSET);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        try {
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            GuiGraphics guiGraphics = new GuiGraphics(minecraft, buffers);
            guiGraphics.renderItem(stack, 0, 0);
            guiGraphics.flush();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }

        try (NativeImage image = new NativeImage(SIZE, SIZE, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            return image.asByteArray();
        } finally {
            target.unbindWrite();
            main.bindWrite(true);
        }
    }

    @Override
    public void close() {
        target.destroyBuffers();
    }
}
