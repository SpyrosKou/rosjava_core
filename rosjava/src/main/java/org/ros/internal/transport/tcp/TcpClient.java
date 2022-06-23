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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.ros.exception.RosRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class TcpClient {

    private static final boolean DEBUG = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpClient.class);

    private static final int DEFAULT_CONNECTION_TIMEOUT_DURATION = 5;
    private static final TimeUnit DEFAULT_CONNECTION_TIMEOUT_UNIT = TimeUnit.SECONDS;
    private static final boolean DEFAULT_KEEP_ALIVE = true;
    public static final String KEEP_ALIVE = "keepAlive";

    private final ChannelGroup channelGroup;
    private final ChannelFactory channelFactory;
    private final ChannelBufferFactory channelBufferFactory;
    private final ClientBootstrap bootstrap;
    private final List<NamedChannelHandler> namedChannelHandlers;
    private Optional<String> currentConnectionName;
    private Channel channel;

    public TcpClient(final ChannelGroup channelGroup, final Executor executor) {
        this.channelGroup = channelGroup;
        channelFactory = new NioClientSocketChannelFactory(executor, executor);
        channelBufferFactory = new HeapChannelBufferFactory(ByteOrder.LITTLE_ENDIAN);
        bootstrap = new ClientBootstrap(channelFactory);
        bootstrap.setOption("bufferFactory", channelBufferFactory);
        setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT_DURATION, DEFAULT_CONNECTION_TIMEOUT_UNIT);
        setKeepAlive(DEFAULT_KEEP_ALIVE);
        namedChannelHandlers = Lists.newArrayList();
    }

    public void setConnectionTimeout(final long duration, final TimeUnit unit) {
        bootstrap.setOption("connectionTimeoutMillis", TimeUnit.MILLISECONDS.convert(duration, unit));
    }

    public void setKeepAlive(final boolean value) {
        bootstrap.setOption(KEEP_ALIVE, value);
    }

    public void addNamedChannelHandler(final NamedChannelHandler namedChannelHandler) {
        namedChannelHandlers.add(namedChannelHandler);
    }

    public void addAllNamedChannelHandlers(final List<NamedChannelHandler> namedChannelHandlers) {
        this.namedChannelHandlers.addAll(namedChannelHandlers);
    }

    /**
     * @param connectionName
     * @param socketAddress
     */
    public void connect(final String connectionName, final SocketAddress socketAddress) {
        this.currentConnectionName = Optional.of(connectionName);
        final TcpClientPipelineFactory tcpClientPipelineFactory = new TcpClientPipelineFactory(channelGroup) {
            @Override
            public ChannelPipeline getPipeline() {
                final ChannelPipeline pipeline = super.getPipeline();
                for (final NamedChannelHandler namedChannelHandler : namedChannelHandlers) {
                    pipeline.addLast(namedChannelHandler.getName(), namedChannelHandler);
                }
                return pipeline;
            }
        };
        bootstrap.setPipelineFactory(tcpClientPipelineFactory);
        final ChannelFuture future = bootstrap.connect(socketAddress).awaitUninterruptibly();
        if (future.isSuccess()) {
            channel = future.getChannel();
            if (DEBUG) {
                LOGGER.info("Connection: [" + connectionName + "] Connected to: " + socketAddress);
            }
        } else {
            // We expect the first connection to succeed. If not, fail fast.
            throw new RosRuntimeException("Connection exception: " + socketAddress, future.getCause());
        }
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelFuture write(final ChannelBuffer buffer) {
        Preconditions.checkNotNull(channel);
        Preconditions.checkNotNull(buffer);
        return channel.write(buffer);
    }


    @Override
    public String toString() {
        return new StringJoiner(", ", TcpClient.class.getSimpleName() + "[", "]")
                .add("channelGroup=" + channelGroup == null ? null : channelGroup.getName()+" "+channelGroup.toString())
                .add("currentConnectionName=" + currentConnectionName)
                .toString();
    }
}