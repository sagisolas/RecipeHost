/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongFunction;

/** Runs render-thread work on the calling thread, with a deadline that never passes. */
public final class DirectRenderThread implements RenderThread {

    @Override
    public <T> CompletableFuture<T> submit(LongFunction<T> work) {
        try {
            return CompletableFuture.completedFuture(work.apply(Long.MAX_VALUE));
        } catch (RuntimeException | Error e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void post(Runnable work) {
        work.run();
    }
}
