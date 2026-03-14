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

import com.google.common.collect.Lists;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author damonkohler@google.com (Damon Kohler)
 * @author Spyros Koukas
 */
public final class TcpClientManager {

  private final ChannelGroup channelGroup;
  private final List<TcpClient> tcpClients;
  private final List<NamedChannelHandler> namedChannelHandlers;
  private final ChannelFactory channelFactory;

  public TcpClientManager(Executor executor) {
    this.channelGroup = new DefaultChannelGroup();
    this.tcpClients = Lists.newArrayList();
    this.namedChannelHandlers = Lists.newArrayList();
    this.channelFactory = new NioClientSocketChannelFactory(executor, executor);
  }

  public void addNamedChannelHandler(final NamedChannelHandler namedChannelHandler) {
    namedChannelHandlers.add(namedChannelHandler);
  }

  public final void addAllNamedChannelHandlers(final List<NamedChannelHandler> namedChannelHandlers) {
    this.namedChannelHandlers.addAll(namedChannelHandlers);
  }

  /**
   * Connects to a server.
   * <p>
   * This call blocks until the connection is established or fails.
   *
   * @param connectionName
   *          the name of the new connection
   * @param socketAddress
   *          the {@link SocketAddress} to connect to
   * @return a new {@link TcpClient}
   */
  public final TcpClient connect(final String connectionName,final SocketAddress socketAddress) {
    final TcpClient tcpClient = TcpClient.newSharedFactoryClient(this.channelGroup, this.channelFactory);
    tcpClient.addAllNamedChannelHandlers(this.namedChannelHandlers);
    tcpClient.connect(connectionName, socketAddress);
    this.tcpClients.add(tcpClient);
    return tcpClient;
  }

  /**
   * Sets all {@link TcpClient}s as non-persistent and closes all open
   * {@link Channel}s.
   */
  public final void shutdown() {
    this.channelGroup.close().awaitUninterruptibly();
    this.tcpClients.clear();
    this.channelFactory.shutdown();
  }
}
