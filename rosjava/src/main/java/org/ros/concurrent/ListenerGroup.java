/*
 * Copyright (C) 2012 Google Inc.
 * Copyright (C) 2026 Spyros Koukas
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A group of listeners.
 *
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 */
public final class ListenerGroup<T> {

    private final static int DEFAULT_QUEUE_CAPACITY = 128;

    private final ExecutorService executorService;
    private final List<EventDispatcher<T>> eventDispatchers = new CopyOnWriteArrayList();

    public ListenerGroup(ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Adds a listener to the {@link ListenerGroup}.
     *
     * @param listener      the listener to add
     * @param queueCapacity the maximum number of events to buffer
     * @return the {@link EventDispatcher} responsible for calling the specified
     * listener
     */
    public final EventDispatcher<T> add(final T listener, final int queueCapacity) {
        final EventDispatcher<T> eventDispatcher = new EventDispatcher<T>(listener, queueCapacity, this.executorService);
        this.eventDispatchers.add(eventDispatcher);
        return eventDispatcher;
    }

    /**
     * Adds the specified listener to the {@link ListenerGroup} with the queue
     * limit set to {@link #DEFAULT_QUEUE_CAPACITY}.
     *
     * @param listener the listener to add
     * @return the {@link EventDispatcher} responsible for calling the specified
     * listener
     */
    public final EventDispatcher<T> add(T listener) {
        return add(listener, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Adds all the specified listeners to the {@link ListenerGroup}.
     *
     * @param listeners the listeners to add
     * @param limit     the maximum number of events to buffer
     * @return a {@link Collection} of {@link EventDispatcher}s responsible for
     * calling the specified listeners
     */
    public final void addAll(Collection<T> listeners, int limit) {
        for (final T listener : listeners) {
            this.add(listener, limit);
        }

    }

    /**
     * Adds all the specified listeners to the {@link ListenerGroup} with the
     * queue capacity for each set to {@link Integer#MAX_VALUE}.
     *
     * @param listeners the listeners to add
     * @return a {@link Collection} of {@link EventDispatcher}s responsible for
     * calling the specified listeners
     */
    public final void addAll(Collection<T> listeners) {
        this.addAll(listeners, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Removes and cancels the {@EventDispatcher} specified by the listener
     * from the {@link ListenerGroup}.
     *
     * @param listener the listener to remove
     * @return flag indicating successful removal
     */
    public final boolean remove(final T listener) {
        Preconditions.checkNotNull(listener);
        boolean result = false;
        for (final EventDispatcher<T> eventDispatcher : this.eventDispatchers) {
            if (listener.equals(eventDispatcher.getListener())) {
                eventDispatcher.cancel();
                this.eventDispatchers.remove(eventDispatcher);
                result = true;
            }
        }
        return false;
    }

    /**
     * @return the number of listeners in the group
     */
    public final int size() {
        return eventDispatchers.size();
    }

    /**
     * Signals all listeners.
     * <p>
     * Each {@link Consumer} is executed in a separate thread.
     */
    public final int signal(final Consumer<T> signalConsumer) {
        int counter = 0;
        for (final EventDispatcher<T> eventDispatcher : this.eventDispatchers) {
            eventDispatcher.signal(signalConsumer);
            counter++;
        }
        return counter;
    }

    /**
     * Signals all listeners and waits for the result.
     * <p>
     * Each {@link Consumer} is executed in a separate thread. In the event
     * that the {@link Consumer} is dropped from the
     * {@link EventDispatcher}'s queue and thus not executed, this method will
     * block for the entire specified timeout.
     *
     * @return {@code true} if all listeners completed within the specified time
     * limit, {@code false} otherwise
     * @throws InterruptedException
     */
    public final boolean signal(final Consumer<T> signalConsumer, long timeout, TimeUnit unit)
            throws InterruptedException {
        final List<EventDispatcher<T>> copy = Lists.newArrayList(eventDispatchers);
        final CountDownLatch latch = new CountDownLatch(copy.size());
        for (final EventDispatcher<T> eventDispatcher : copy) {
            eventDispatcher.signal(listener -> {
                signalConsumer.accept(listener);
                latch.countDown();
            });
        }
        return latch.await(timeout, unit);
    }

    public final void shutdown() {
        for (final EventDispatcher<T> eventDispatcher : this.eventDispatchers) {
            eventDispatcher.cancel();
        }
        this.eventDispatchers.clear();
    }
}
