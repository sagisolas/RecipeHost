package org.sagisolas.recipehost.mod.snapshot;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

    /** The future's result, rethrowing its failure as it was thrown. */
    static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            if (e.getCause() instanceof Error error) throw error;
            throw e;
        }
    }
}
