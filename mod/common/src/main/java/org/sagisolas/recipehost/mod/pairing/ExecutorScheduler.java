/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.pairing;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** {@link PairingController.Scheduler} backed by a single daemon thread. */
public final class ExecutorScheduler implements PairingController.Scheduler {

    private final ScheduledExecutorService executor;

    public ExecutorScheduler(String threadName) {
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public PairingController.Cancellable schedule(Runnable task, Duration delay) {
        ScheduledFuture<?> future = executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }
}
