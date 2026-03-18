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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A deque that removes head or tail elements when the number of elements
 * exceeds the limit and blocks on {@link #takeFirst()} and {@link #takeLast()} when
 * there are no elements available.
 *
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 */
public final class CircularBlockingDeque<T> implements Iterable<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final T[] queue;
    private final Object mutex;

    /**
     * The maximum number of entries in the queue.
     */
    private final int limit;

    /**
     * Points to the next entry that will be returned by {@link #takeFirst()} unless
     * {@link #isEmpty()}.
     */
    private int start;

    /**
     * The number of entries in the queue.
     */
    private int length;

    /**
     * @param capacity the maximum number of elements allowed in the queue
     */
    @SuppressWarnings("unchecked")
    public CircularBlockingDeque(int capacity) {
        this.queue = (T[]) new Object[capacity];
        this.mutex = new Object();
        this.limit = capacity;
        this.start = 0;
        this.length = 0;
    }

    /**
     * Adds the specified entry to the tail of the queue, overwriting older
     * entries if necessary.
     *
     * @param entry the entry to add
     * @return {@code true}
     */
    public final boolean addLast(T entry) {
        synchronized (this.mutex) {
            final int entryIndex = (this.start + this.length) % this.limit;
            final T deletedEntry = this.length == this.limit ? this.queue[entryIndex] : null;
            this.queue[entryIndex] = entry;
            if (this.length == this.limit) {
                this.start = (this.start + 1) % this.limit;

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(CircularBlockingDeque.class.getSimpleName() + " was full, an entry was overridden: {" + deletedEntry + "}");
                }
            } else {
                this.length++;
            }
            this.mutex.notify();
        }
        return true;
    }

    /**
     * Adds the specified entry to the tail of the queue, overwriting older
     * entries if necessary.
     *
     * @param entry the entry to add
     * @return {@code true}
     */
    public final boolean addFirst(T entry) {
        synchronized (this.mutex) {
            final int previousStart = this.start;
            if (this.start - 1 < 0) {
                this.start = this.limit - 1;
            } else {
                this.start--;
            }
            this.queue[this.start] = entry;
            if (this.length < this.limit) {
                this.length++;
            } else {
                if (LOGGER.isDebugEnabled()) {
                    final T deletedEntry = this.queue[previousStart];
                    LOGGER.debug(CircularBlockingDeque.class.getSimpleName() + " was full, an entry was overridden: {" + deletedEntry + "}");
                }
            }
            this.mutex.notify();
        }
        return true;
    }

    /**
     * Retrieves the head of the queue, blocking if necessary until an entry is
     * available.
     *
     * @return the head of the queue
     * @throws InterruptedException
     */
    public final T takeFirst() throws InterruptedException {

        synchronized (this.mutex) {
            while (true) {
                if (this.length > 0) {
                    final T entry = this.queue[this.start];
                    this.queue[this.start] = null;
                    this.start = (this.start + 1) % this.limit;
                    this.length--;
                    return entry;
                } else {
                    this.mutex.wait();
                }
            }
        }

    }

    /**
     * Retrieves, but does not remove, the head of this queue, returning
     * {@code null} if this queue is empty.
     *
     * @return the head of this queue, or {@code null} if this queue is empty
     */
    public final T peekFirst() {
        synchronized (this.mutex) {
            if (this.length > 0) {
                return this.queue[this.start];
            } else {
                return null;
            }
        }
    }

    /**
     * Retrieves and removes the head of this queue, returning {@code null} if this
     * queue is empty.
     *
     * @return the head of this queue, or {@code null} if this queue is empty
     */
    public final T pollFirst() {
        synchronized (this.mutex) {
            if (this.length > 0) {
                final T entry = this.queue[this.start];
                this.queue[this.start] = null;
                this.start = (this.start + 1) % this.limit;
                this.length--;
                return entry;
            } else {
                return null;
            }
        }
    }

    /**
     * Retrieves the tail of the queue, blocking if necessary until an entry is
     * available.
     *
     * @return the tail of the queue
     * @throws InterruptedException
     */
    public final T takeLast() throws InterruptedException {

        synchronized (this.mutex) {
            while (true) {
                if (this.length > 0) {
                    final int entryIndex = (this.start + this.length - 1) % this.limit;
                    final T entry = this.queue[entryIndex];
                    this.queue[entryIndex] = null;
                    this.length--;
                    return entry;
                } else {
                    this.mutex.wait();
                }
            }
        }

    }

    /**
     * Retrieves, but does not remove, the tail of this queue, returning
     * {@code null} if this queue is empty.
     *
     * @return the tail of this queue, or {@code null} if this queue is empty
     */
    public final T peekLast() {
        synchronized (mutex) {
            if (length > 0) {
                return queue[(start + length - 1) % limit];
            } else {
                return null;
            }
        }
    }

    public final boolean isEmpty() {
        return this.length == 0;
    }

    public final void clear() {
        synchronized (this.mutex) {
            for (int i = 0; i < this.limit; i++) {
                this.queue[i] = null;
            }
            this.start = 0;
            this.length = 0;
        }
    }

    /**
     * Returns an iterator over the queue.
     * <p>
     * Note that this is not thread-safe and that {@link Iterator#remove()} is
     * unsupported.
     *
     * @see java.lang.Iterable#iterator()
     */
    @Override
    public final Iterator<T> iterator() {
        return new Iterator<T>() {
            int offset = 0;

            @Override
            public final boolean hasNext() {
                return offset < length;
            }

            @Override
            public final T next() {
                if (offset == length) {
                    throw new NoSuchElementException();
                } else {
                    final T entry = queue[(start + offset) % limit];
                    offset++;
                    return entry;
                }
            }

            @Override
            public final void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
