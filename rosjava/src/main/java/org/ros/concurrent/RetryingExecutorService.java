/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.concurrent;

import com.google.common.collect.Maps;
import org.ros.exception.RosRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps an {@link ScheduledExecutorService} to execute {@link Callable}s with
 * retries.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public class RetryingExecutorService {


    private static final Logger LOGGER = LoggerFactory.getLogger(RetryingExecutorService.class);
    private static final boolean DEBUG = LOGGER.isDebugEnabled();
    private static final long DEFAULT_RETRY_DELAY = 5;
    private static final TimeUnit DEFAULT_RETRY_TIME_UNIT = TimeUnit.SECONDS;

    private final ScheduledExecutorService scheduledExecutorService;
    private final RetryLoop retryLoop = new RetryLoop();
    private final Map<Callable<Boolean>, CountDownLatch> latches = Maps.newConcurrentMap();
    ;
    private final Map<Future<Boolean>, Callable<Boolean>> callables = Maps.newConcurrentMap();
    ;
    private final CompletionService<Boolean> completionService;
    private final Object mutex = new Object();

    private long retryDelay=DEFAULT_RETRY_DELAY;
    private TimeUnit retryTimeUnit=DEFAULT_RETRY_TIME_UNIT;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private class RetryLoop extends CancellableLoop {
        @Override
        public void loop() throws InterruptedException {
            final Future<Boolean> future = RetryingExecutorService.this.completionService.take();
            final Callable<Boolean> callable;
            final CountDownLatch latch;
            // Grab the mutex to make sure submit() of the future that we took is finished.
            synchronized (mutex) {
                callable = RetryingExecutorService.this.callables.remove(future);
                latch = RetryingExecutorService.this.latches.get(callable);
            }
            final boolean retry;
            try {
                retry = future.get();
            } catch (ExecutionException exception) {
                throw new RosRuntimeException(exception.getCause());
            }
            if (retry) {
                if (DEBUG) {
                    LOGGER.info("Retry requested.");
                }

                RetryingExecutorService.this.scheduledExecutorService.schedule(() -> submit(callable), retryDelay, retryTimeUnit);
            } else {
                latch.countDown();
            }
        }
    }

    /**
     * @param scheduledExecutorService the {@link ExecutorService} to wrap
     */
    public RetryingExecutorService(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.completionService = new ExecutorCompletionService<>(scheduledExecutorService);

        // TODO(damonkohler): Unify this with the passed in ExecutorService.
        scheduledExecutorService.execute(retryLoop);
    }

    /**
     * Submit a new {@link Callable} to be executed. The submitted
     * {@link Callable} should return {@code true} to be retried, {@code false}
     * otherwise.
     *
     * @param callable the {@link Callable} to execute
     *
     * @throws RejectedExecutionException if the {@link RetryingExecutorService} is shutting down
     */
    public final void submit(final Callable<Boolean> callable) {

        if (this.running.get()) {
            synchronized (this.mutex) {
                final Future<Boolean> future = this.completionService.submit(callable);
                this.latches.put(callable, new CountDownLatch(1));
                this.callables.put(future, callable);
            }
        } else {
            throw new RejectedExecutionException();
        }

    }

    /**
     * @param delay the delay in units of {@code unit}
     * @param unit  the {@link TimeUnit} of the delay
     */
    public final void setRetryDelay(long delay, TimeUnit unit) {
        this.retryDelay = delay;
        this.retryTimeUnit = unit;
    }

    /**
     * Stops accepting new {@link Callable}s and waits for all submitted
     * {@link Callable}s to finish within the specified timeout.
     *
     * @param timeout the timeout in units of {@code unit}
     * @param unit    the {@link TimeUnit} of {@code timeout}
     *
     * @throws InterruptedException
     */
    public final void shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        this.running.set(false);
        for (final CountDownLatch latch : latches.values()) {
            latch.await(timeout, unit);
        }
        this.retryLoop.cancel();
    }
}