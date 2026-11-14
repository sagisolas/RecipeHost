/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Snapshot work on Minecraft's render thread in slices that fit a per-tick budget, so
 * producing a Snapshot doesn't drop frames. Work waits in this scheduler's own queue until
 * {@link #runSlices()} runs it; the composition root calls that at the end of every client tick.
 *
 * <p>The work is not given to {@code Minecraft#execute}. Work run from Minecraft's task queue
 * can't wait on {@code Minecraft#submit}: from inside a task, submit queues behind it instead of
 * running inline, and the render thread would wait on itself. Item renderers do wait on it
 * (GeckoLib's glowing textures), so drawing their Icons from a task froze the game.
 */
public final class RenderThreadScheduler implements RenderThread {

    private static final Logger LOG = LoggerFactory.getLogger(RenderThreadScheduler.class);

    /** Render-thread time the slices of one tick may take together. */
    static final long BUDGET_NANOS = 8_000_000L;

    private final Queue<LongConsumer> slices = new ConcurrentLinkedQueue<>();
    private final LongSupplier clock;

    public RenderThreadScheduler() {
        this(System::nanoTime);
    }

    /** @param clock gives {@code System.nanoTime()}-style readings */
    RenderThreadScheduler(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public <T> CompletableFuture<T> submit(LongFunction<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        slices.add(deadline -> {
            try {
                future.complete(work.apply(deadline));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @Override
    public void post(Runnable work) {
        slices.add(deadline -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                LOG.error("[recipehost] Render-thread work failed", e);
            }
        });
    }

    /**
     * Runs queued slices on the calling (render) thread until the queue is empty or this tick's
     * budget is spent, giving each the deadline the budget ends at. At least one slice runs, so
     * the queue always makes progress; slices queued meanwhile run too while budget remains.
     */
    public void runSlices() {
        long deadline = clock.getAsLong() + BUDGET_NANOS;
        do {
            LongConsumer slice = slices.poll();
            if (slice == null) return;
            slice.accept(deadline);
        } while (clock.getAsLong() < deadline);
    }
}
