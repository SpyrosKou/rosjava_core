/*
 * Copyright (C) 2011 Google Inc.
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

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Spyros Koukas
 */
class TcpClientManagerTest {

  private ExecutorService executorService;
  private TcpClientManager tcpClientManager;
  private NioServerSocketChannelFactory serverChannelFactory;
  private ServerBootstrap serverBootstrap;
  private Channel serverChannel;

  @BeforeEach
  void setUp() {
    executorService = Executors.newCachedThreadPool();
    tcpClientManager = new TcpClientManager(executorService);
    serverChannelFactory = new NioServerSocketChannelFactory(executorService, executorService);
    serverBootstrap = new ServerBootstrap(serverChannelFactory);
    serverBootstrap.setOption("child.bufferFactory",
        new HeapChannelBufferFactory(ByteOrder.LITTLE_ENDIAN));
    serverBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() {
        return Channels.pipeline(new SimpleChannelHandler());
      }
    });
    serverChannel = serverBootstrap.bind(new InetSocketAddress(0));
  }

  @AfterEach
  void tearDown() {
    if (tcpClientManager != null) {
      tcpClientManager.shutdown();
    }
    if (serverChannel != null) {
      serverChannel.close().awaitUninterruptibly();
    }
    if (serverChannelFactory != null) {
      serverChannelFactory.shutdown();
    }
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }

  @Test
  void reusesChannelFactoryAcrossConnections() {
    TcpClient firstClient = tcpClientManager.connect("first", serverChannel.getLocalAddress());
    TcpClient secondClient = tcpClientManager.connect("second", serverChannel.getLocalAddress());

    assertSame(firstClient.getChannelFactory(), secondClient.getChannelFactory());
  }
}
