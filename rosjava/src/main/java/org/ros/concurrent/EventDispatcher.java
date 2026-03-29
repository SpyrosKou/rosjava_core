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


import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 *
 * @param <T>
 *          the listener type
 */
public final class EventDispatcher<T> {

  private final T listener;
  private final CircularBlockingDeque<Consumer<T>> events;
  private final ExecutorService executorService;
  private final Object mutex = new Object();

  private boolean cancelled;
  private boolean dispatching;
  private Thread dispatchThread;

  public EventDispatcher(final T listener, final int queueCapacity,
      final ExecutorService executorService) {
    this.listener = listener;
    this.events = new CircularBlockingDeque<>(queueCapacity);
    this.executorService = executorService;
  }

  private boolean startDispatchIfNeeded() {
    if (this.cancelled || this.dispatching || this.events.peekFirst() == null) {
      return false;
    }
    this.dispatching = true;
    return true;
  }

  private void executeDispatch() {
    try {
      this.executorService.execute(this::dispatch);
    } catch (final RejectedExecutionException e) {
      synchronized (mutex) {
        this.dispatching = false;
      }
      throw e;
    }
  }

  /**
   * Submits a task to be processed by the dispatcher if the dispatcher is not cancelled.
   * Tasks are executed asynchronously in the order they are submitted.
   *
   * @param signalConsumer a consumer that will process the event, which operates on the listener of type {@code T}
   */
  public final void signal(final Consumer<T> signalConsumer) {
    final boolean shouldDispatch;
    synchronized (mutex) {
      if (this.cancelled) {
        return;
      }
      this.events.addLast(signalConsumer);
      shouldDispatch = this.startDispatchIfNeeded();
    }
    if (shouldDispatch) {
      this.executeDispatch();
    }
  }

  /**
   * Executes and processes tasks submitted to the dispatcher in a sequential and thread-safe manner.
   *
   * This method is internally invoked to process tasks that were submitted via the signal method.
   * It runs in a loop, retrieving and executing each task in the order they were submitted.
   * If the dispatcher is marked as cancelled, the method terminates early, ensuring no additional tasks are executed.
   *
   * The method employs synchronization to manage access to shared resources such as the task queue,
   * the dispatching state, and the current dispatch thread reference. For every iteration of the loop,
   * it retrieves the next task from the queue (if available) and invokes it using the listener as the argument.
   *
   * Upon completion or cancellation, this method ensures proper cleanup by resetting
   * the dispatch thread reference, allowing subsequent operations to proceed safely.
   *
   * Thread interruptions or unexpected errors will not prevent the final cleanup steps from executing.
   */
  private final void dispatch() {
    final Thread currentThread = Thread.currentThread();
    Throwable failure = null;
    boolean shouldDispatch = false;
    synchronized (this.mutex) {
      this.dispatchThread = currentThread;
    }
    try {
      while (true) {
        final Consumer<T> consumer;
        synchronized (this.mutex) {
          if (this.cancelled) {
            this.dispatching = false;
            return;
          }
          consumer = this.events.pollFirst();
          if (consumer == null) {
            this.dispatching = false;
            return;
          }
        }
        try {
          consumer.accept(this.listener);
        } catch (final Throwable throwable) {
          failure = throwable;
          synchronized (this.mutex) {
            this.dispatching = false;
            shouldDispatch = this.startDispatchIfNeeded();
          }
          return;
        }
      }
    } finally {
      synchronized (this.mutex) {
        if (this.dispatchThread == currentThread) {
          this.dispatchThread = null;
        }
      }
      if (shouldDispatch) {
        try {
          this.executeDispatch();
        } catch (final Throwable scheduleFailure) {
          if (failure != null) {
            failure.addSuppressed(scheduleFailure);
          } else {
            throw scheduleFailure;
          }
        }
      }
      if (failure != null) {
        EventDispatcher.<RuntimeException>throwUnchecked(failure);
      }
    }
  }

  /**
   * Cancels the event dispatching process and ensures proper cleanup of resources.
   *
   * This method marks the dispatcher as cancelled, clears queued work, and
   * interrupts the active dispatch thread, if one is running.
   */
  public final void cancel() {
    final Thread activeDispatchThread;
    synchronized (mutex) {
      this.cancelled = true;
      this.dispatching = false;
      this.events.clear();
      activeDispatchThread = this.dispatchThread;
    }
    if (activeDispatchThread != null) {
      activeDispatchThread.interrupt();
    }
  }

  public final T getListener() {
    return this.listener;
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwUnchecked(final Throwable throwable) throws E {
    throw (E) throwable;
  }
}
