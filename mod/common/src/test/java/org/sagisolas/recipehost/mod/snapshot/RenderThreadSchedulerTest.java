/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class RenderThreadSchedulerTest {

    /** A clock that only moves when a slice says so. */
    private long now = 1_000L;

    private final RenderThreadScheduler scheduler = new RenderThreadScheduler(() -> now);

    @Test
    public void workWaitsForTheFrame() throws Exception {
        CompletableFuture<String> future = scheduler.submit(deadline -> "done");
        assertFalse("nothing runs before the frame", future.isDone());

        scheduler.runSlices();

        assertEquals("done", future.get());
    }

    @Test
    public void slicesShareOneBudgetPerFrame() {
        List<Long> deadlines = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            scheduler.submit(deadline -> {
                deadlines.add(deadline);
                now += RenderThreadScheduler.BUDGET_NANOS / 2;
                return null;
            });
        }

        scheduler.runSlices();
        assertEquals("the budget is spent after two half-budget slices", 2, deadlines.size());
        assertEquals("both get the same deadline", deadlines.get(0), deadlines.get(1));

        scheduler.runSlices();
        assertEquals("the next frame runs the rest", 3, deadlines.size());
    }

    @Test
    public void aSliceRunsEvenIfItOverrunsTheBudget() {
        List<String> ran = new ArrayList<>();
        scheduler.submit(deadline -> {
            now += 10 * RenderThreadScheduler.BUDGET_NANOS;
            return ran.add("slow");
        });
        scheduler.submit(deadline -> ran.add("next"));

        scheduler.runSlices();
        assertEquals(List.of("slow"), ran);
        scheduler.runSlices();
        assertEquals(List.of("slow", "next"), ran);
    }

    @Test
    public void workQueuedDuringTheFrameRunsWhileBudgetRemains() throws Exception {
        CompletableFuture<CompletableFuture<String>> follower = new CompletableFuture<>();
        scheduler.submit(deadline -> follower.complete(scheduler.submit(d -> "follow-up")));

        scheduler.runSlices();

        assertEquals("follow-up", follower.get().get());
    }

    @Test
    public void aFailingSliceFailsItsFutureAndTheFrameGoesOn() {
        IllegalStateException boom = new IllegalStateException("boom");
        CompletableFuture<Object> failing = scheduler.submit(deadline -> {
            throw boom;
        });
        List<String> ran = new ArrayList<>();
        scheduler.post(() -> {
            throw new IllegalStateException("posted");
        });
        scheduler.post(() -> ran.add("after"));

        scheduler.runSlices();

        ExecutionException e = assertThrows(ExecutionException.class, failing::get);
        assertSame(boom, e.getCause());
        assertEquals(List.of("after"), ran);
    }
}
