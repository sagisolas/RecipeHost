package org.sagisolas.recipehost.mod.snapshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

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

    private final Minecraft minecraft;
    private final RenderTarget target;

    IconRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        this.target.setClearColor(0f, 0f, 0f, 0f);
    }

    byte[] renderToPng(ItemStack stack) throws IOException {
        RenderTarget main = minecraft.getMainRenderTarget();
        target.bindWrite(true);
        target.clear(Minecraft.ON_OSX);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        GuiGraphics guiGraphics = new GuiGraphics(minecraft, buffers);
        guiGraphics.pose().pushPose();
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.pose().popPose();
        guiGraphics.flush();

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
