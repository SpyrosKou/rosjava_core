/*
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

package org.ros.internal.transport.tcp;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Spyros Koukas
 */
class NettyThreadLifecycleRegressionTest {

  private static final int CONNECTION_COUNT = 4;
  private static final long THREAD_WAIT_TIMEOUT_MS = 10000;
  private static final long THREAD_POLL_INTERVAL_MS = 100;

  @Test
  void comparesLegacyPerConnectionFactoriesAgainstSharedManager() throws Exception {
    final ExecutorService serverExecutor = Executors.newCachedThreadPool(
        namedThreadFactory("netty-thread-test-server-"));
    final NioServerSocketChannelFactory serverFactory =
        new NioServerSocketChannelFactory(serverExecutor, serverExecutor, 1);
    final ServerBootstrap serverBootstrap = new ServerBootstrap(serverFactory);
    serverBootstrap.setOption("child.bufferFactory",
        new HeapChannelBufferFactory(ByteOrder.LITTLE_ENDIAN));
    serverBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() {
        return Channels.pipeline(new SimpleChannelHandler());
      }
    });
    final Channel serverChannel = serverBootstrap.bind(new InetSocketAddress(0));

    final List<ChannelFactory> legacyFactories = new ArrayList<>();
    final List<Channel> legacyChannels = new ArrayList<>();
    final ExecutorService legacyExecutor = Executors.newCachedThreadPool(
        namedThreadFactory("netty-thread-test-legacy-"));
    final ExecutorService sharedExecutor = Executors.newCachedThreadPool(
        namedThreadFactory("netty-thread-test-shared-"));
    TcpClientManager tcpClientManager = null;

    try {
      final long baselineWheelThreads = countHashedWheelTimerThreads();

      for (int i = 0; i < CONNECTION_COUNT; i++) {
        final NioClientSocketChannelFactory legacyFactory =
            new NioClientSocketChannelFactory(legacyExecutor, legacyExecutor, 1, 1);
        final ClientBootstrap legacyBootstrap = new ClientBootstrap(legacyFactory);
        legacyBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
          @Override
          public ChannelPipeline getPipeline() {
            return Channels.pipeline(new SimpleChannelHandler());
          }
        });
        final ChannelFuture future =
            legacyBootstrap.connect(serverChannel.getLocalAddress()).awaitUninterruptibly();
        assertTrue(future.isSuccess(), "Legacy client failed to connect.");
        legacyFactories.add(legacyFactory);
        legacyChannels.add(future.getChannel());
      }

      waitFor(() -> countHashedWheelTimerThreads() - baselineWheelThreads >= CONNECTION_COUNT,
          "legacy hashed-wheel timers to start");
      closeChannels(legacyChannels);
      Thread.sleep(THREAD_POLL_INTERVAL_MS * 2);

      final long legacyWheelThreadsAfterClose =
          countHashedWheelTimerThreads() - baselineWheelThreads;
      System.out.printf("legacy close-only: wheel=%d%n", legacyWheelThreadsAfterClose);

      assertTrue(legacyWheelThreadsAfterClose >= CONNECTION_COUNT,
          "Expected one leaked hashed-wheel timer per legacy factory.");

      shutdownFactories(legacyFactories);
      waitFor(() -> countHashedWheelTimerThreads() == baselineWheelThreads,
          "legacy factory shutdown to release netty threads");

      final long sharedBaselineWheelThreads = countHashedWheelTimerThreads();
      tcpClientManager = new TcpClientManager(sharedExecutor);
      for (int i = 0; i < CONNECTION_COUNT; i++) {
        tcpClientManager.connect("shared-" + i, serverChannel.getLocalAddress());
      }

      waitFor(() -> countHashedWheelTimerThreads() - sharedBaselineWheelThreads >= 1,
          "shared manager hashed-wheel timer to start");
      final long sharedWheelThreadsDuringUse =
          countHashedWheelTimerThreads() - sharedBaselineWheelThreads;
      System.out.printf("shared active: wheel=%d%n", sharedWheelThreadsDuringUse);

      assertTrue(sharedWheelThreadsDuringUse <= 1,
          "Shared manager should create at most one extra hashed-wheel timer.");

      tcpClientManager.shutdown();
      tcpClientManager = null;
      waitFor(() -> countHashedWheelTimerThreads() == sharedBaselineWheelThreads,
          "shared manager shutdown to release netty threads");

      System.out.printf("shared after shutdown: wheel=%d%n",
          countHashedWheelTimerThreads() - sharedBaselineWheelThreads);
    } finally {
      if (tcpClientManager != null) {
        tcpClientManager.shutdown();
      }
      closeChannels(legacyChannels);
      shutdownFactories(legacyFactories);
      serverChannel.close().awaitUninterruptibly();
      serverFactory.shutdown();
      legacyExecutor.shutdownNow();
      sharedExecutor.shutdownNow();
      serverExecutor.shutdownNow();
    }
  }

  private static void closeChannels(List<Channel> channels) {
    for (Channel channel : channels) {
      channel.close().awaitUninterruptibly();
    }
  }

  private static void shutdownFactories(List<ChannelFactory> channelFactories) {
    for (ChannelFactory channelFactory : channelFactories) {
      channelFactory.shutdown();
    }
  }

  private static void waitFor(BooleanSupplier condition, String description)
      throws InterruptedException {
    final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(THREAD_WAIT_TIMEOUT_MS);
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(THREAD_POLL_INTERVAL_MS);
    }
    throw new AssertionError("Timed out waiting for " + description + ".");
  }

  private static long countHashedWheelTimerThreads() {
    return Thread.getAllStackTraces().entrySet().stream()
        .filter(entry -> entry.getKey().isAlive())
        .filter(entry -> entry.getKey().getName().toLowerCase().contains("hashed wheel timer")
            || stackContains(entry.getValue(), "org.jboss.netty.util.HashedWheelTimer"))
        .count();
  }

  private static boolean stackContains(StackTraceElement[] stackTrace, String classNamePrefix) {
    for (StackTraceElement stackTraceElement : stackTrace) {
      if (stackTraceElement.getClassName().startsWith(classNamePrefix)) {
        return true;
      }
    }
    return false;
  }

  private static ThreadFactory namedThreadFactory(String prefix) {
    final AtomicInteger counter = new AtomicInteger();
    return runnable -> new Thread(runnable, prefix + counter.incrementAndGet());
  }
}
