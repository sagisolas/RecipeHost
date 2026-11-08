package org.sagisolas.recipehost.mod.snapshot.recipe;

import org.sagisolas.recipehost.mod.snapshot.RenderThread;

import java.util.function.LongFunction;

/** Runs render-thread work on the calling thread, with a deadline that never passes. */
final class DirectRenderThread implements RenderThread {

    @Override
    public <T> T call(LongFunction<T> work) {
        return work.apply(Long.MAX_VALUE);
    }

    @Override
    public void post(Runnable work) {
        work.run();
    }
}
