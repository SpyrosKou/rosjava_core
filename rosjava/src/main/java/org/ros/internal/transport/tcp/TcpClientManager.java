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

package org.ros.internal.transport.tcp;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class TcpClientManager {

    private final ChannelGroup channelGroup = new DefaultChannelGroup();
    private final List<TcpClient> tcpClients = new ArrayList<>();
    private final List<NamedChannelHandler> namedChannelHandlers = new ArrayList<>();
    private final Executor executor;

    public TcpClientManager(Executor executor) {
        this.executor = executor;
    }

    public void add(NamedChannelHandler namedChannelHandler) {
        namedChannelHandlers.add(namedChannelHandler);
    }

    public void addAll(final Collection<NamedChannelHandler> namedChannelHandlers) {
        this.namedChannelHandlers.addAll(namedChannelHandlers);
    }

    /**
     * Connects to a server.
     * <p>
     * This call blocks until the connection is established or fails.
     *
     * @param connectionName the name of the new connection
     * @param socketAddress  the {@link SocketAddress} to connect to
     *
     * @return a new {@link TcpClient}
     */
    public final TcpClient connect(final String connectionName,final SocketAddress socketAddress) {
        final TcpClient tcpClient = new TcpClient(channelGroup, executor);
        tcpClient.addAllNamedChannelHandlers(namedChannelHandlers);
        tcpClient.connect(connectionName, socketAddress);
        this.tcpClients.add(tcpClient);
        return tcpClient;
    }

    /**
     * Sets all {@link TcpClientConnection}s as non-persistent and closes all open
     * {@link Channel}s.
     */
    public void shutdown() {
        this.channelGroup.close().awaitUninterruptibly();
        this.tcpClients.clear();
        this.namedChannelHandlers.clear();
        // We don't call channelFactory.releaseExternalResources() or
        // bootstrap.releaseExternalResources() since the only external resource is
        // the ExecutorService which must remain in the control of the overall
        // application.
    }
}
