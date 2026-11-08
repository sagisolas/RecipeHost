package org.sagisolas.recipehost.mod.snapshot;

import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.LongFunction;

/**
 * Runs Snapshot work on Minecraft's render thread in slices that fit a per-frame budget,
 * so producing a Snapshot doesn't drop frames. The calling (sender) thread blocks for
 * each slice's result.
 */
public final class RenderThreadScheduler {

    /** Render-thread time one slice may take. */
    private static final long BUDGET_NANOS = 3_000_000L;

    /**
     * Runs {@code work} on the render thread and blocks for its result. {@code work}
     * gets the {@code System.nanoTime()} deadline it should stop by.
     */
    public <T> T call(LongFunction<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                future.complete(work.apply(System.nanoTime() + BUDGET_NANOS));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        }
    }

    /** Runs {@code work} on the render thread without waiting for it. */
    public void post(Runnable work) {
        Minecraft.getInstance().execute(work);
    }
}
