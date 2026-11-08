package org.sagisolas.recipehost.mod.snapshot;

import java.util.function.LongFunction;

/**
 * Where Snapshot work that must touch the game's GUI or GL state runs: Minecraft's render
 * thread in the game ({@link RenderThreadScheduler}), the calling thread in tests.
 */
public interface RenderThread {

    /**
     * Runs {@code work} there and blocks for its result. {@code work} gets the
     * {@code System.nanoTime()} deadline its slice should stop by.
     */
    <T> T call(LongFunction<T> work);

    /** Runs {@code work} there without waiting for it. */
    void post(Runnable work);
}
