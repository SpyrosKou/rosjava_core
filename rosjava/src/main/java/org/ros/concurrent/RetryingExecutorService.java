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


import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.exception.RosRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Wraps an {@link ScheduledExecutorService} to execute {@link Callable}s with
 * retries.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class RetryingExecutorService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final long DEFAULT_RETRY_DELAY = 5;
  private static final TimeUnit DEFAULT_RETRY_TIME_UNIT = TimeUnit.SECONDS;

  private final ScheduledExecutorService scheduledExecutorService;
  private final RetryLoop retryLoop=new RetryLoop();
  private final Map<Callable<Boolean>, CountDownLatch> latches=new ConcurrentHashMap<>();
  private final Map<Future<Boolean>, Callable<Boolean>> callables=new ConcurrentHashMap<>();
  private final CompletionService<Boolean> completionService;
  private final Object mutex= new Object();




  private long retryDelay= DEFAULT_RETRY_DELAY;
  private TimeUnit retryTimeUnit= DEFAULT_RETRY_TIME_UNIT;
  private boolean running;

  private final class RetryLoop extends CancellableLoop {
    @Override
    public final void loop() throws InterruptedException {
      final Future<Boolean> future = RetryingExecutorService.this.completionService.take();
      final Callable<Boolean> callable;
      final CountDownLatch latch;
      // Grab the mutex to make sure submit() of the future that we took is finished.
      synchronized (RetryingExecutorService.this.mutex) {
        callable = RetryingExecutorService.this.callables.remove(future);
        latch = RetryingExecutorService.this.latches.get(callable);
      }
      final boolean retry;
      try {
        retry = future.get();
      } catch (final ExecutionException executionException) {
        if(LOGGER.isErrorEnabled()){
          LOGGER.error("Error while retrying: "+ExceptionUtils.getStackTrace(executionException));
        }
        throw new RosRuntimeException(executionException.getCause());
      }
      if (retry) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("Retry requested.");
        }
        final Callable<Boolean> finalCallable = callable;
        scheduledExecutorService.schedule(() -> submit(finalCallable), retryDelay, retryTimeUnit);
      } else {
        latch.countDown();
      }
    }
  }

  /**
   * @param scheduledExecutorService
   *          the {@link ExecutorService} to wrap
   */
  public RetryingExecutorService(final ScheduledExecutorService scheduledExecutorService) {
    this.scheduledExecutorService = scheduledExecutorService;
    this.completionService = new ExecutorCompletionService<>(scheduledExecutorService);
    this.running = true;
    // TODO(damonkohler): Unify this with the passed in ExecutorService.
    scheduledExecutorService.execute(retryLoop);
  }

  /**
   * Submit a new {@link Callable} to be executed. The submitted
   * {@link Callable} should return {@code true} to be retried, {@code false}
   * otherwise.
   *
   * @param callable
   *          the {@link Callable} to execute
   * @throws RejectedExecutionException
   *           if the {@link RetryingExecutorService} is shutting down
   */
  public final void submit(final Callable<Boolean> callable) {
    synchronized (mutex) {
      if (this.running) {
        final Future<Boolean> future = completionService.submit(callable);
        this.latches.put(callable, new CountDownLatch(1));
        this.callables.put(future, callable);
      } else {
        if(LOGGER.isDebugEnabled()){
          LOGGER.debug("Submission rejected as service is not running");
        }
        throw new RejectedExecutionException("Submission rejected as service is not running");
      }
    }
  }

  /**
   * @param delay
   *          the delay in units of {@code unit}
   * @param unit
   *          the {@link TimeUnit} of the delay
   */
  public final void setRetryDelay(final long delay,final TimeUnit unit) {
    this.retryDelay = delay;
    this.retryTimeUnit = unit;
  }

  /**
   * Stops accepting new {@link Callable}s and waits for all submitted
   * {@link Callable}s to finish within the specified timeout.
   *
   * @param timeout
   *          the timeout in units of {@code unit}
   * @param unit
   *          the {@link TimeUnit} of {@code timeout}
   * @throws InterruptedException
   */
  public final void shutdown(long timeout, TimeUnit unit) throws InterruptedException {
    this.running = false;
    for (final CountDownLatch latch : this.latches.values()) {
      latch.await(timeout, unit);
    }
    this.retryLoop.cancel();
  }
}