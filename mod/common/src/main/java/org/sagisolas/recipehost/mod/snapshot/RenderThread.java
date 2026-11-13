/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.LongFunction;

/**
 * Where Snapshot work that must touch the game's GUI or GL state runs: Minecraft's render
 * thread in the game ({@link RenderThreadScheduler}), the calling thread in tests.
 */
public interface RenderThread {

    /**
     * Queues {@code work} there. {@code work} gets the {@code System.nanoTime()} deadline its slice
     * should stop by; the future completes with its result or its failure.
     */
    <T> CompletableFuture<T> submit(LongFunction<T> work);

    /** Runs {@code work} there without waiting for it. */
    void post(Runnable work);

    /** Runs {@code work} there and blocks for its result, rethrowing its failure. */
    default <T> T call(LongFunction<T> work) {
        return join(submit(work));
    }

    /**
     * Waits for the future's result, rethrowing its failure as it was thrown. An interrupted wait
     * (the Session is being torn down) gives up with the thread's interrupt flag set.
     */
    static <T> T join(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the render thread", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            if (e.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(e.getCause());
        }
    }
}
