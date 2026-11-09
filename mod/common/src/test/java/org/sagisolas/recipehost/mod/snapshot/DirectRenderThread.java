package org.sagisolas.recipehost.mod.snapshot;

import java.util.function.LongFunction;

/** Runs render-thread work on the calling thread, with a deadline that never passes. */
public final class DirectRenderThread implements RenderThread {

    @Override
    public <T> T call(LongFunction<T> work) {
        return work.apply(Long.MAX_VALUE);
    }

    @Override
    public void post(Runnable work) {
        work.run();
    }
}
