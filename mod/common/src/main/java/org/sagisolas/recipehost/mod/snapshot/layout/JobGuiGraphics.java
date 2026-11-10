package org.sagisolas.recipehost.mod.snapshot.layout;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * The {@link GuiGraphics} one {@link LayoutJob} draws with: clipped to the job's rectangle
 * on {@link LayoutRenderer}'s sheet. Vanilla's own scissor works in window pixels at the GUI
 * scale and turns clipping off when its stack empties, so a widget's scissor would land in
 * the wrong place and then let the job draw over its neighbours. Here scissors are kept in
 * sheet pixels, inside the job's rectangle, and the job's clip comes back when they're done.
 */
final class JobGuiGraphics extends GuiGraphics {

    private final int sheetHeight;
    private final ScreenRectangle job;
    private final Deque<ScreenRectangle> scissors = new ArrayDeque<>();

    /** @param job the job's rectangle on the sheet, in sheet (= GUI) pixels */
    JobGuiGraphics(Minecraft minecraft, int sheetHeight, ScreenRectangle job) {
        super(minecraft, minecraft.renderBuffers().bufferSource());
        this.sheetHeight = sheetHeight;
        this.job = job;
    }

    /** Clips to the job's rectangle; called once before the job draws. */
    void clipToJob() {
        apply(job);
    }

    @Override
    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        // Corners as vanilla 1.21.1 takes them: in screen (here: sheet) pixels, not through the pose.
        ScreenRectangle requested = new ScreenRectangle(minX, minY, Math.max(0, maxX - minX), Math.max(0, maxY - minY));
        ScreenRectangle clip = requested.intersection(current());
        ScreenRectangle box = clip != null ? clip : new ScreenRectangle(job.left(), job.top(), 0, 0);
        scissors.push(box);
        apply(box);
    }

    @Override
    public void disableScissor() {
        if (!scissors.isEmpty()) scissors.pop();
        apply(current());
    }

    private ScreenRectangle current() {
        return scissors.isEmpty() ? job : scissors.peek();
    }

    /** What's drawn so far belongs to the previous clip, so it's flushed first. */
    private void apply(ScreenRectangle box) {
        flush();
        // GL scissor boxes start at the bottom-left; the sheet's GUI y runs down from the top.
        RenderSystem.enableScissor(box.left(), sheetHeight - box.bottom(), box.width(), box.height());
    }
}
