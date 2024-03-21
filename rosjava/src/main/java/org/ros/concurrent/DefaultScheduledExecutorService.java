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

import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This wraps a {@link Executors#newCachedThreadPool()} and a
 * {@link Executors#newScheduledThreadPool(int)} to provide the functionality of
 * both in a single {@link ScheduledExecutorService}. This is necessary since
 * the {@link ScheduledExecutorService} uses an unbounded queue which makes it
 * impossible to create an unlimited number of threads on demand (as explained
 * in the {@link ThreadPoolExecutor} class javadoc.
 *
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class DefaultScheduledExecutorService implements ScheduledExecutorService {

    private static final int CORE_POOL_SIZE = 11;
    private static final String DEFAULT_EXECUTOR_SERVICE_NAME = "ROS1";
    private static final String DEFAULT_SCHEDULED_EXECUTOR_SERVICE_NAME = "ROS1-Scheduled";

    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final String executorServiceName;
    private final String scheduledExecutorServiceName;

    public DefaultScheduledExecutorService(final String executorServiceName, final String scheduledExecutorServiceName) {

        this.executorServiceName = executorServiceName == null ? DEFAULT_EXECUTOR_SERVICE_NAME : executorServiceName;
        this.scheduledExecutorServiceName = scheduledExecutorServiceName == null ? DEFAULT_SCHEDULED_EXECUTOR_SERVICE_NAME : scheduledExecutorServiceName;
        this.executorService = Executors.newCachedThreadPool(new NamedThreadFactory(this.executorServiceName));
        this.scheduledExecutorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE, new NamedThreadFactory(this.scheduledExecutorServiceName));
    }

    public DefaultScheduledExecutorService() {
        this(DEFAULT_EXECUTOR_SERVICE_NAME, DEFAULT_SCHEDULED_EXECUTOR_SERVICE_NAME);
    }




    @Override
    public void shutdown() {
        this.executorService.shutdown();
        this.scheduledExecutorService.shutdown();
    }

    @Override
    @Nonnull
    public List<Runnable> shutdownNow() {
        final List<Runnable> combined = Lists.newArrayList();
        combined.addAll(executorService.shutdownNow());
        combined.addAll(scheduledExecutorService.shutdownNow());
        return combined;
    }

    @Override
    public boolean isShutdown() {
        return executorService.isShutdown() && scheduledExecutorService.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executorService.isTerminated() && scheduledExecutorService.isTerminated();
    }

    /**
     * First calls {@link #awaitTermination(long, TimeUnit)} on the wrapped
     * {@link ExecutorService} and then {@link #awaitTermination(long, TimeUnit)}
     * on the wrapped {@link ScheduledExecutorService}.
     *
     * @return {@code true} if both {@link Executor}s terminated, {@code false}
     * otherwise
     */
    @Override
    public boolean awaitTermination(long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
        final boolean executorServiceResult = executorService.awaitTermination(timeout, unit);
        final boolean scheduledExecutorServiceResult =
                scheduledExecutorService.awaitTermination(timeout, unit);
        return executorServiceResult && scheduledExecutorServiceResult;
    }

    @Override
    @Nonnull
    public <T> Future<T> submit(@Nonnull final Callable<T> task) {
        return executorService.submit(task);
    }

    @Override
    @Nonnull
    public <T> Future<T> submit(final @Nonnull Runnable task, final @Nonnull T result) {
        return executorService.submit(task, result);
    }

    @Override
    @Nonnull
    public Future<?> submit(final @Nonnull Runnable task) {
        return executorService.submit(task);
    }

    @Override
    @Nonnull
    public <T> List<Future<T>> invokeAll(@Nonnull final Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return executorService.invokeAll(tasks);
    }

    @Override
    @Nonnull
    public <T> List<Future<T>> invokeAll(@Nonnull final Collection<? extends Callable<T>> tasks
            , @Nonnull final long timeout
            , @Nonnull final TimeUnit unit) throws InterruptedException {
        return executorService.invokeAll(tasks, timeout, unit);
    }

    @Override
    @Nonnull
    public <T> T invokeAny(@Nonnull final Collection<? extends Callable<T>> tasks) throws InterruptedException,
            ExecutionException {
        return executorService.invokeAny(tasks);
    }

    @Override
    @Nonnull
    public <T> T invokeAny(@Nonnull final Collection<? extends Callable<T>> tasks
            , long timeout, @Nonnull final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return executorService.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(@Nonnull final Runnable command) {
        executorService.execute(command);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> schedule(@Nonnull final Runnable command, long delay, @Nonnull final TimeUnit unit) {
        return scheduledExecutorService.schedule(command, delay, unit);
    }

    @Override
    @Nonnull
    public <V> ScheduledFuture<V> schedule(@Nonnull final Callable<V> callable, long delay, @Nonnull final TimeUnit unit) {
        return scheduledExecutorService.schedule(callable, delay, unit);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> scheduleAtFixedRate(@Nonnull final Runnable command, long initialDelay, long period,
                                                  @Nonnull final TimeUnit unit) {
        return scheduledExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> scheduleWithFixedDelay(@Nonnull final Runnable command, long initialDelay, long delay,
                                                     @Nonnull final TimeUnit unit) {
        return scheduledExecutorService.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
}
