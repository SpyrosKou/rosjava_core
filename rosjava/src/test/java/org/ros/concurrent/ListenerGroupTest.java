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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 */
public class ListenerGroupTest {
  private static final long TEST_TIMEOUT_MILLIS = 2_000L;

  private ExecutorService executorService;
  private ListenerGroup<Runnable> listenerGroup;

  @BeforeEach
  public void before() {
    executorService = Executors.newCachedThreadPool();
    listenerGroup = new ListenerGroup<Runnable>(executorService);
  }

  @AfterEach
  public void after() throws InterruptedException {
    if (listenerGroup != null) {
      listenerGroup.shutdown();
    }
    if (executorService != null) {
      executorService.shutdownNow();
      executorService.awaitTermination(1, TimeUnit.SECONDS);
    }
  }

  @Test
  public void testOneListenerMultipleSignals() throws InterruptedException {
    int numberOfSignals = 10;
    final CountDownLatch latch = new CountDownLatch(numberOfSignals);
    listenerGroup.add(new Runnable() {
      @Override
      public void run() {
        latch.countDown();
      }
    });
    for (int i = 0; i < numberOfSignals; i++) {
      listenerGroup.signal(listener -> listener.run());
    }
    assertTrue(latch.await(1, TimeUnit.SECONDS));
  }

  @Test
  public void testMultipleListenersMultipleSignals() throws InterruptedException {
    int numberOfSignals = 10;
    final CountDownLatch latch1 = new CountDownLatch(numberOfSignals);
    final CountDownLatch latch2 = new CountDownLatch(numberOfSignals);
    listenerGroup.add(new Runnable() {
      @Override
      public void run() {
        latch1.countDown();
      }
    });
    listenerGroup.add(new Runnable() {
      @Override
      public void run() {
        latch2.countDown();
      }
    });
    for (int i = 0; i < numberOfSignals; i++) {
      listenerGroup.signal(listener -> listener.run());
    }
    assertTrue(latch1.await(1, TimeUnit.SECONDS));
    assertTrue(latch2.await(1, TimeUnit.SECONDS));
  }

  private interface CountingListener {
    void run(int count);
  }

  @Test
  public void testSignalOrder() throws InterruptedException {
    int numberOfSignals = 100;
    final CountDownLatch latch = new CountDownLatch(numberOfSignals);

    ListenerGroup<CountingListener> listenerGroup =
        new ListenerGroup<CountingListener>(executorService);
    listenerGroup.add(new CountingListener() {
      private AtomicInteger count = new AtomicInteger();

      @Override
      public void run(int count) {
        if (this.count.compareAndSet(count, count + 1)) {
          latch.countDown();
        }
        try {
          // Sleeping allows the queue to fill up a bit by slowing down the
          // consumer.
          Thread.sleep(5);
        } catch (InterruptedException e) {
        }
      }
    });

    for (int i = 0; i < numberOfSignals; i++) {
      final int count = i;
      listenerGroup.signal(listener -> listener.run(count));
    }

    assertTrue(latch.await(1, TimeUnit.SECONDS));
  }

  @Test
  public void testPerListenerOrderingIsPreservedExactly() throws InterruptedException {
    final int numberOfSignals = 32;
    final CountDownLatch latch = new CountDownLatch(numberOfSignals);
    final List<Integer> actual = new ArrayList<Integer>();

    ListenerGroup<CountingListener> orderedListenerGroup =
        new ListenerGroup<CountingListener>(executorService);
    orderedListenerGroup.add(new CountingListener() {
      @Override
      public void run(int count) {
        synchronized (actual) {
          actual.add(count);
        }
        latch.countDown();
      }
    });

    for (int i = 0; i < numberOfSignals; i++) {
      final int count = i;
      orderedListenerGroup.signal(listener -> listener.run(count));
    }

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertEquals(numberOfSignals, actual.size());
    for (int i = 0; i < numberOfSignals; i++) {
      assertEquals(i, actual.get(i).intValue());
    }
  }

  @Test
  // Characterizes the required safety property for redesign: one listener must
  // never execute two callbacks at the same time.
  public void testSameListenerCallbacksNeverRunConcurrently() throws InterruptedException {
    final int numberOfSignals = 24;
    final CountDownLatch latch = new CountDownLatch(numberOfSignals);
    final AtomicInteger inFlightCallbacks = new AtomicInteger();
    final AtomicInteger maxConcurrentCallbacks = new AtomicInteger();
    final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

    ListenerGroup<Runnable> runnableListenerGroup = new ListenerGroup<Runnable>(executorService);
    runnableListenerGroup.add(new Runnable() {
      @Override
      public void run() {
        final int concurrentCallbacks = inFlightCallbacks.incrementAndGet();
        maxConcurrentCallbacks.accumulateAndGet(concurrentCallbacks, Math::max);
        try {
          if (concurrentCallbacks > 1) {
            failure.compareAndSet(null, new AssertionError("Listener callback overlapped."));
          }
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          failure.compareAndSet(null, e);
        } finally {
          inFlightCallbacks.decrementAndGet();
          latch.countDown();
        }
      }
    });

    for (int i = 0; i < numberOfSignals; i++) {
      runnableListenerGroup.signal(listener -> listener.run());
    }

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertTrue(failure.get() == null);
    assertEquals(1, maxConcurrentCallbacks.get());
  }

  @Test
  // Characterizes the current blocking contract of signal(timeout).
  public void testSignalWithTimeoutWaitsForAllListeners() throws InterruptedException {
    final CountDownLatch entered = new CountDownLatch(2);
    final CountDownLatch release = new CountDownLatch(1);
    final ListenerGroup<Runnable> runnableListenerGroup = new ListenerGroup<Runnable>(executorService);

    runnableListenerGroup.add(new Runnable() {
      @Override
      public void run() {
        entered.countDown();
        try {
          release.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });
    runnableListenerGroup.add(new Runnable() {
      @Override
      public void run() {
        entered.countDown();
        try {
          release.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });

    final AtomicReference<Boolean> completed = new AtomicReference<Boolean>(null);
    final Thread signalThread = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          completed.set(runnableListenerGroup.signal(listener -> listener.run(), 1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          completed.set(Boolean.FALSE);
        }
      }
    });
    signalThread.start();

    assertTrue(entered.await(1, TimeUnit.SECONDS));
    assertTrue(signalThread.isAlive());

    release.countDown();
    signalThread.join(1_000L);
    assertFalse(signalThread.isAlive());
    assertEquals(Boolean.TRUE, completed.get());
  }

  @Test
  public void testAddingListenersDoesNotStartIdleDispatcherThreads() throws InterruptedException {
    final int listenerCount = 6;
    final String threadNamePrefix = "ListenerGroupTest-" + System.nanoTime();
    listenerGroup.shutdown();
    executorService.shutdownNow();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
    executorService = newTransientExecutor(threadNamePrefix);
    listenerGroup = new ListenerGroup<Runnable>(executorService);

    for (int i = 0; i < listenerCount; i++) {
      listenerGroup.add(new Runnable() {
        @Override
        public void run() {
        }
      });
    }

    assertTrue(awaitThreadCount(threadNamePrefix, 0, TEST_TIMEOUT_MILLIS));
  }

  @Test
  public void testDispatcherThreadsAreTransientWhenWorkCompletes() throws InterruptedException {
    final int listenerCount = 4;
    final String threadNamePrefix = "ListenerGroupTransientTest-" + System.nanoTime();
    listenerGroup.shutdown();
    executorService.shutdownNow();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
    executorService = newTransientExecutor(threadNamePrefix);
    listenerGroup = new ListenerGroup<Runnable>(executorService);
    final CountDownLatch latch = new CountDownLatch(listenerCount);

    for (int i = 0; i < listenerCount; i++) {
      listenerGroup.add(new Runnable() {
        @Override
        public void run() {
          latch.countDown();
        }
      });
    }

    listenerGroup.signal(listener -> listener.run());
    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertTrue(awaitThreadCount(threadNamePrefix, 0, TEST_TIMEOUT_MILLIS));
  }

  @Test
  public void testReentrantSignalOnSameListenerIsDeliveredInOrder() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(2);
    final List<Integer> actual = new ArrayList<Integer>();
    final AtomicReference<ListenerGroup<CountingListener>> listenerGroupRef =
        new AtomicReference<ListenerGroup<CountingListener>>();

    final ListenerGroup<CountingListener> countingListenerGroup =
        new ListenerGroup<CountingListener>(executorService);
    listenerGroupRef.set(countingListenerGroup);
    countingListenerGroup.add(new CountingListener() {
      @Override
      public void run(int count) {
        synchronized (actual) {
          actual.add(count);
        }
        if (count == 0) {
          listenerGroupRef.get().signal(listener -> listener.run(1));
        }
        latch.countDown();
      }
    });

    countingListenerGroup.signal(listener -> listener.run(0));

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertEquals(2, actual.size());
    assertEquals(0, actual.get(0).intValue());
    assertEquals(1, actual.get(1).intValue());
  }

  @Test
  public void testCancelWhileExecutingDropsQueuedCallbacks() throws InterruptedException {
    final CountDownLatch firstCallbackEntered = new CountDownLatch(1);
    final CountDownLatch firstCallbackFinished = new CountDownLatch(1);
    final CountDownLatch releaseFirstCallback = new CountDownLatch(1);
    final AtomicInteger laterCallbacks = new AtomicInteger();

    final ListenerGroup<CountingListener> countingListenerGroup =
        new ListenerGroup<CountingListener>(executorService);
    final EventDispatcher<CountingListener> eventDispatcher =
        countingListenerGroup.add(new CountingListener() {
          @Override
          public void run(int count) {
            if (count == 0) {
              firstCallbackEntered.countDown();
              try {
                releaseFirstCallback.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                firstCallbackFinished.countDown();
              }
              return;
            }
            laterCallbacks.incrementAndGet();
          }
        });

    countingListenerGroup.signal(listener -> listener.run(0));
    assertTrue(firstCallbackEntered.await(1, TimeUnit.SECONDS));

    countingListenerGroup.signal(listener -> listener.run(1));
    countingListenerGroup.signal(listener -> listener.run(2));
    eventDispatcher.cancel();
    countingListenerGroup.signal(listener -> listener.run(3));

    releaseFirstCallback.countDown();
    assertTrue(firstCallbackFinished.await(1, TimeUnit.SECONDS));
    Thread.sleep(100);
    assertEquals(0, laterCallbacks.get());
  }

  @Test
  public void testSlowListenerDoesNotBlockFastListener() throws InterruptedException {
    final CountDownLatch slowEntered = new CountDownLatch(1);
    final CountDownLatch fastCompleted = new CountDownLatch(1);
    final CountDownLatch releaseSlow = new CountDownLatch(1);
    final ListenerGroup<Runnable> runnableListenerGroup = new ListenerGroup<Runnable>(executorService);

    runnableListenerGroup.add(new Runnable() {
      @Override
      public void run() {
        slowEntered.countDown();
        try {
          releaseSlow.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });
    runnableListenerGroup.add(new Runnable() {
      @Override
      public void run() {
        fastCompleted.countDown();
      }
    });

    runnableListenerGroup.signal(listener -> listener.run());

    assertTrue(slowEntered.await(1, TimeUnit.SECONDS));
    assertTrue(fastCompleted.await(1, TimeUnit.SECONDS));
    releaseSlow.countDown();
  }

  @Test
  public void testDispatcherThreadsReturnToZeroAfterRepeatedBursts() throws InterruptedException {
    final int listenerCount = 3;
    final int burstCount = 5;
    final String threadNamePrefix = "ListenerGroupBurstTest-" + System.nanoTime();
    listenerGroup.shutdown();
    executorService.shutdownNow();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
    executorService = newTransientExecutor(threadNamePrefix);
    listenerGroup = new ListenerGroup<Runnable>(executorService);

    for (int i = 0; i < listenerCount; i++) {
      listenerGroup.add(new Runnable() {
        @Override
        public void run() {
        }
      });
    }

    for (int burst = 0; burst < burstCount; burst++) {
      final CountDownLatch latch = new CountDownLatch(listenerCount);
      listenerGroup.signal(listener -> latch.countDown());
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(awaitThreadCount(threadNamePrefix, 0, TEST_TIMEOUT_MILLIS));
    }
  }

  private static boolean awaitThreadCount(String threadNamePrefix, int expectedCount, long timeoutMillis)
      throws InterruptedException {
    final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    while (System.nanoTime() < deadline) {
      if (countAliveThreads(threadNamePrefix) == expectedCount) {
        return true;
      }
      Thread.sleep(10);
    }
    return countAliveThreads(threadNamePrefix) == expectedCount;
  }

  private static int countAliveThreads(String threadNamePrefix) {
    int count = 0;
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      if (thread.isAlive() && thread.getName().startsWith(threadNamePrefix)) {
        count++;
      }
    }
    return count;
  }

  private static final class PrefixThreadFactory implements ThreadFactory {
    private final String threadNamePrefix;
    private final AtomicInteger threadCounter = new AtomicInteger();

    private PrefixThreadFactory(String threadNamePrefix) {
      this.threadNamePrefix = threadNamePrefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
      return new Thread(runnable, threadNamePrefix + "-" + threadCounter.incrementAndGet());
    }
  }

  private static ExecutorService newTransientExecutor(String threadNamePrefix) {
    final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(0, 16,
        100, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>(),
        new PrefixThreadFactory(threadNamePrefix));
    threadPoolExecutor.allowCoreThreadTimeOut(true);
    return threadPoolExecutor;
  }
}
