/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongFunction;
import net.minecraft.client.Minecraft;

/**
 * Runs Snapshot work on Minecraft's render thread in slices that fit a per-frame budget,
 * so producing a Snapshot doesn't drop frames. The budget is shared by the slices that run in
 * one frame: Minecraft runs every queued task between two frames, so a slice queued while the
 * last one was still running would otherwise get a whole budget of its own.
 */
public final class RenderThreadScheduler implements RenderThread {

    /** Render-thread time the slices of one frame may take together. */
    private static final long BUDGET_NANOS = 3_000_000L;
    /** A slice that starts this soon after the last one ended runs in the same frame. */
    private static final long SAME_FRAME_NANOS = 2_000_000L;

    /** Touched only on the render thread. */
    private long lastSliceEnd = Long.MIN_VALUE / 2;

    private long spentThisFrame;

    @Override
    public <T> CompletableFuture<T> submit(LongFunction<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            long start = System.nanoTime();
            if (start - lastSliceEnd > SAME_FRAME_NANOS) spentThisFrame = 0;
            try {
                future.complete(work.apply(start + Math.max(0, BUDGET_NANOS - spentThisFrame)));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            } finally {
                lastSliceEnd = System.nanoTime();
                spentThisFrame += lastSliceEnd - start;
            }
        });
        return future;
    }

    @Override
    public void post(Runnable work) {
        Minecraft.getInstance().execute(work);
    }
}
